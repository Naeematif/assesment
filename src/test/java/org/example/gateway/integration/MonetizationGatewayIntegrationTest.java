package org.example.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * End-to-end behaviour of the gateway: who gets in, who gets refused, and what the caller is told.
 */
class MonetizationGatewayIntegrationTest extends AbstractGatewayIntegrationTest {

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        void refusesRequestsWithNoApiKey() {
            var response = callApi("/api/v1/weather", null);

            assertStatus(response, 401);
            assertThat(errorCode(response)).isEqualTo("MISSING_API_KEY");
        }

        @Test
        void refusesUnknownApiKeys() {
            var response = callApi("/api/v1/weather", "amk_live_not_a_real_key");

            assertStatus(response, 401);
            assertThat(errorCode(response)).isEqualTo("INVALID_API_KEY");
        }

        @Test
        void refusesRevokedApiKeys() {
            Tenant tenant = provision(1000, 100, "0", "0", false);
            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);

            List<Map<String, Object>> keys = getList("/admin/customers/" + tenant.customerId() + "/api-keys");
            Long keyId = ((Number) keys.get(0).get("id")).longValue();
            rest.delete("/admin/customers/" + tenant.customerId() + "/api-keys/" + keyId);

            var response = callApi("/api/v1/weather", tenant.apiKey());
            assertStatus(response, 401);
            assertThat(errorCode(response)).isEqualTo("API_KEY_REVOKED");
        }

        @Test
        void refusesSuspendedCustomers() {
            Tenant tenant = provision(1000, 100, "0", "0", false);
            putJson("/admin/customers/" + tenant.customerId() + "/status/SUSPENDED", Map.of());

            var response = callApi("/api/v1/weather", tenant.apiKey());
            assertStatus(response, 403);
            assertThat(errorCode(response)).isEqualTo("CUSTOMER_SUSPENDED");
        }

        @Test
        void doesNotMeterTrafficOutsideTheApiPrefix() {
            Tenant tenant = provision(1, 100, "0", "0", false);

            // Admin and actuator calls must not consume the customer's single request.
            getMap("/admin/customers/" + tenant.customerId());
            getMap("/actuator/health");

            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);
        }
    }

    @Nested
    @DisplayName("Rate limiting")
    class RateLimiting {

        @Test
        @DisplayName("Free tier's 2 requests per second is enforced with a 429 and a Retry-After")
        void enforcesThePerSecondLimit() {
            Tenant tenant = provision(1_000, 2, "0", "0", false);

            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);
            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);

            var throttled = callApi("/api/v1/weather", tenant.apiKey());
            assertStatus(throttled, 429);
            assertThat(errorCode(throttled)).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(throttled.getHeaders().getFirst("Retry-After")).isEqualTo("1");
            assertThat(throttled.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("2");
            assertThat(throttled.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        }

        @Test
        void advertisesTheRemainingAllowanceOnSuccessfulCalls() {
            Tenant tenant = provision(1_000, 10, "0", "0", false);

            var response = callApi("/api/v1/weather", tenant.apiKey());

            assertStatus(response, 200);
            assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("10");
            assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("9");
            assertThat(response.getHeaders().getFirst("X-Tier")).isEqualTo(tenant.tierCode());
        }

        @Test
        void permitsComeBackAfterWaiting() throws Exception {
            Tenant tenant = provision(1_000, 2, "0", "0", false);

            callApi("/api/v1/weather", tenant.apiKey());
            callApi("/api/v1/weather", tenant.apiKey());
            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 429);

            Thread.sleep(1_100); // one second's worth of refill at 2 permits/second

            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);
        }

        @Test
        void oneCustomerCannotThrottleAnother() {
            Tenant noisy = provision(1_000, 1, "0", "0", false);
            Tenant quiet = provision(1_000, 1, "0", "0", false);

            assertStatus(callApi("/api/v1/weather", noisy.apiKey()), 200);
            assertStatus(callApi("/api/v1/weather", noisy.apiKey()), 429);

            assertStatus(callApi("/api/v1/weather", quiet.apiKey()), 200);
        }
    }

    @Nested
    @DisplayName("Monthly quota")
    class Quota {

        @Test
        @DisplayName("A hard-capped tier is refused once the monthly allowance is gone")
        void enforcesTheMonthlyQuota() {
            Tenant tenant = provision(3, 100, "0", "0", false);

            for (int i = 1; i <= 3; i++) {
                var ok = callApi("/api/v1/weather", tenant.apiKey());
                assertStatus(ok, 200);
                assertThat(ok.getHeaders().getFirst("X-Quota-Remaining")).isEqualTo(String.valueOf(3 - i));
            }

            var exhausted = callApi("/api/v1/weather", tenant.apiKey());
            assertStatus(exhausted, 429);
            // Same status as a rate limit, but a distinct code: this one does not clear in a second.
            assertThat(errorCode(exhausted)).isEqualTo("QUOTA_EXCEEDED");
            assertThat(exhausted.getHeaders().getFirst("X-Quota-Remaining")).isEqualTo("0");
        }

        @Test
        void reportsTheLiveQuotaPosition() {
            Tenant tenant = provision(10, 100, "0", "0", false);
            callApi("/api/v1/weather", tenant.apiKey());
            callApi("/api/v1/weather", tenant.apiKey());

            Map<String, Object> quota = getMap("/admin/customers/" + tenant.customerId() + "/quota");

            assertThat(quota).containsEntry("monthlyQuota", 10)
                    .containsEntry("used", 2)
                    .containsEntry("remaining", 8)
                    .containsEntry("tierCode", tenant.tierCode());
        }

        @Test
        @DisplayName("A tier that sells overage keeps serving past the quota")
        void allowsOverageWhenTheTierPermitsIt() {
            Tenant tenant = provision(2, 100, "50.00", "0.01", true);

            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);
            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);

            var overage = callApi("/api/v1/weather", tenant.apiKey());
            assertStatus(overage, 200);
            assertThat(overage.getHeaders().getFirst("X-Quota-Remaining")).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("Dynamic tier configuration")
    class DynamicConfiguration {

        @Test
        @DisplayName("Raising a tier's quota unblocks its customers without a restart")
        void quotaChangesTakeEffectImmediately() {
            Tenant tenant = provision(1, 100, "0", "0", false);

            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);
            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 429);

            putJson("/admin/tiers/" + tenant.tierCode(), Map.of(
                    "code", tenant.tierCode(),
                    "displayName", tenant.tierCode(),
                    "monthlyQuota", 100,
                    "rateLimitPerSecond", 100,
                    "burstCapacity", 100,
                    "monthlyPrice", BigDecimal.ZERO,
                    "overagePricePerRequest", BigDecimal.ZERO,
                    "overageAllowed", false));

            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);
        }

        @Test
        @DisplayName("Lowering a tier's rate limit applies to traffic already in flight")
        void rateLimitChangesTakeEffectImmediately() {
            Tenant tenant = provision(1_000, 50, "0", "0", false);
            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);

            putJson("/admin/tiers/" + tenant.tierCode(), Map.of(
                    "code", tenant.tierCode(),
                    "displayName", tenant.tierCode(),
                    "monthlyQuota", 1_000,
                    "rateLimitPerSecond", 1,
                    "burstCapacity", 1,
                    "monthlyPrice", BigDecimal.ZERO,
                    "overagePricePerRequest", BigDecimal.ZERO,
                    "overageAllowed", false));

            // The permits accumulated under the old, higher limit must not survive the downgrade.
            var first = callApi("/api/v1/weather", tenant.apiKey());
            assertThat(first.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("1");
            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 429);
        }

        @Test
        @DisplayName("Upgrading a customer to a bigger plan lifts their limits at once")
        void upgradingACustomerAppliesTheNewTier() {
            Tenant small = provision(1, 100, "0", "0", false);
            Tenant big = provision(10_000, 100, "50.00", "0.001", true);

            assertStatus(callApi("/api/v1/weather", small.apiKey()), 200);
            assertStatus(callApi("/api/v1/weather", small.apiKey()), 429);

            putJson("/admin/customers/" + small.customerId() + "/subscription",
                    Map.of("tierCode", big.tierCode()));

            var afterUpgrade = callApi("/api/v1/weather", small.apiKey());
            assertStatus(afterUpgrade, 200);
            assertThat(afterUpgrade.getHeaders().getFirst("X-Tier")).isEqualTo(big.tierCode());
            assertThat(afterUpgrade.getHeaders().getFirst("X-Quota-Limit")).isEqualTo("10000");
        }

        @Test
        void rejectsTierConfigurationThatMakesNoSense() {
            var response = postJson("/admin/tiers", Map.of(
                    "code", unique("BAD").toUpperCase(),
                    "displayName", "Bad",
                    "monthlyQuota", 10,
                    "rateLimitPerSecond", 0,
                    "monthlyPrice", BigDecimal.ZERO,
                    "overageAllowed", false));

            assertStatus(response, 400);
            assertThat(errorCode(response)).isEqualTo("VALIDATION_FAILED");
        }
    }

    @Nested
    @DisplayName("Usage tracking")
    class UsageTracking {

        @Test
        @DisplayName("Every served call is logged with customer, user, endpoint and timestamp")
        void recordsUsageMetadata() {
            Tenant tenant = provision(100, 100, "0", "0", false);
            callApi("/api/v1/weather", tenant.apiKey());
            callApi("/api/v1/products/7", tenant.apiKey());

            List<Map<String, Object>> events = getList("/admin/usage/" + tenant.customerId());

            assertThat(events).hasSize(2);
            assertThat(events).allSatisfy(event -> {
                assertThat(event).containsEntry("customerId", tenant.customerId().intValue());
                assertThat(event).containsEntry("userId", "user-1");
                assertThat(event).containsEntry("tier", tenant.tierCode());
                assertThat(event).containsEntry("billable", true);
                assertThat((String) event.get("timestamp")).isNotBlank();
            });
            assertThat(events).extracting(event -> event.get("endpoint"))
                    // Path variables collapse into the route template, so usage rolls up per route
                    // rather than producing one line per product id.
                    .containsExactlyInAnyOrder("/api/v1/weather", "/api/v1/products/{id}");
        }

        @Test
        void doesNotLogRejectedCalls() {
            Tenant tenant = provision(1, 100, "0", "0", false);
            callApi("/api/v1/weather", tenant.apiKey());
            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 429);

            Map<String, Object> count = getMap("/admin/usage/" + tenant.customerId() + "/count");

            // A request that never reached the service is not usage, and must not be billed.
            assertThat(count).containsEntry("events", 1);
        }
    }
}
