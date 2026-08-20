package org.example.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The full monetization loop: traffic goes through the gateway, the backend job rolls it up, and the
 * customer ends the month with a priced statement broken down by endpoint.
 */
class BillingAggregationIntegrationTest extends AbstractGatewayIntegrationTest {

    /** Billing periods are defined in the configured billing zone, which is UTC by default. */
    private static String currentPeriod() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    private Map<String, Object> summaryFor(Long customerId) {
        return getMap("/admin/billing/summaries/" + customerId + "?period=" + currentPeriod());
    }

    @Test
    @DisplayName("Free tier: usage is summarised and costs nothing")
    void summarisesFreeTierUsageAtZeroCost() {
        Tenant tenant = provision(100, 100, "0", "0", false);
        for (int i = 0; i < 4; i++) {
            callApi("/api/v1/weather", tenant.apiKey());
        }

        postJson("/admin/billing/aggregate?period=" + currentPeriod(), Map.of());
        Map<String, Object> summary = summaryFor(tenant.customerId());

        assertThat(summary).containsEntry("totalRequests", 4)
                .containsEntry("includedRequests", 4)
                .containsEntry("overageRequests", 0)
                .containsEntry("tierCode", tenant.tierCode());
        assertThat(new java.math.BigDecimal(summary.get("totalCharge").toString()))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Paid tier: the subscription fee plus per-request overage")
    void pricesSubscriptionAndOverage() {
        // 5 requests included at $50/month, then $0.01 per extra request.
        Tenant tenant = provision(5, 100, "50.00", "0.01", true);
        for (int i = 0; i < 8; i++) {
            assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);
        }

        postJson("/admin/billing/aggregate?period=" + currentPeriod(), Map.of());
        Map<String, Object> summary = summaryFor(tenant.customerId());

        assertThat(summary).containsEntry("totalRequests", 8)
                .containsEntry("includedRequests", 5)
                .containsEntry("overageRequests", 3);
        assertThat(new java.math.BigDecimal(summary.get("baseCharge").toString()))
                .isEqualByComparingTo("50.00");
        assertThat(new java.math.BigDecimal(summary.get("overageCharge").toString()))
                .isEqualByComparingTo("0.03");
        assertThat(new java.math.BigDecimal(summary.get("totalCharge").toString()))
                .isEqualByComparingTo("50.03");
    }

    @Test
    @DisplayName("The statement breaks usage down per endpoint")
    void breaksUsageDownByEndpoint() {
        Tenant tenant = provision(100, 100, "0", "0", false);
        callApi("/api/v1/weather", tenant.apiKey());
        callApi("/api/v1/weather", tenant.apiKey());
        callApi("/api/v1/weather", tenant.apiKey());
        callApi("/api/v1/products/1", tenant.apiKey());
        callApi("/api/v1/products/2", tenant.apiKey());

        postJson("/admin/billing/aggregate?period=" + currentPeriod(), Map.of());
        Map<String, Object> summary = summaryFor(tenant.customerId());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) summary.get("endpoints");

        // Two distinct product ids roll up into a single route line.
        assertThat(endpoints).hasSize(2);
        assertThat(endpoints).anySatisfy(row -> {
            assertThat(row).containsEntry("endpoint", "/api/v1/weather");
            assertThat(row).containsEntry("requestCount", 3);
            assertThat(row).containsEntry("httpMethod", "GET");
        });
        assertThat(endpoints).anySatisfy(row -> {
            assertThat(row).containsEntry("endpoint", "/api/v1/products/{id}");
            assertThat(row).containsEntry("requestCount", 2);
        });
    }

    @Test
    @DisplayName("Re-running a period replaces its summaries instead of double-charging")
    void aggregationIsIdempotent() {
        Tenant tenant = provision(100, 100, "10.00", "0", false);
        callApi("/api/v1/weather", tenant.apiKey());
        callApi("/api/v1/weather", tenant.apiKey());

        postJson("/admin/billing/aggregate?period=" + currentPeriod(), Map.of());
        Map<String, Object> first = summaryFor(tenant.customerId());

        postJson("/admin/billing/aggregate?period=" + currentPeriod(), Map.of());
        Map<String, Object> second = summaryFor(tenant.customerId());

        assertThat(second).containsEntry("totalRequests", 2);
        assertThat(second.get("totalCharge")).isEqualTo(first.get("totalCharge"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) second.get("endpoints");
        assertThat(endpoints).hasSize(1);
        assertThat(endpoints.get(0)).containsEntry("requestCount", 2);
    }

    @Test
    @DisplayName("Rejected calls never reach the invoice")
    void doesNotBillThrottledOrOverQuotaRequests() {
        Tenant tenant = provision(2, 100, "0", "0", false);
        assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);
        assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 200);
        assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 429);
        assertStatus(callApi("/api/v1/weather", tenant.apiKey()), 429);

        postJson("/admin/billing/aggregate?period=" + currentPeriod(), Map.of());

        assertThat(summaryFor(tenant.customerId())).containsEntry("totalRequests", 2);
    }

    @Test
    void reportsWhatTheRunProcessed() {
        Tenant tenant = provision(100, 100, "25.00", "0", false);
        callApi("/api/v1/weather", tenant.apiKey());

        Map<String, Object> report =
                postJson("/admin/billing/aggregate?period=" + currentPeriod(), Map.of()).getBody();

        assertThat(report).isNotNull();
        assertThat(report).containsEntry("billingPeriod", currentPeriod());
        assertThat(((Number) report.get("customersProcessed")).intValue()).isPositive();
        assertThat(((Number) report.get("totalRequests")).longValue()).isPositive();
        assertThat(report.get("completedAt")).isNotNull();
    }

    @Test
    void rejectsAMalformedBillingPeriod() {
        var response = postJson("/admin/billing/aggregate?period=March", Map.of());

        assertStatus(response, 400);
        assertThat(errorCode(response)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void returnsNotFoundWhenNoSummaryExistsYet() {
        Tenant tenant = provision(100, 100, "0", "0", false);

        var response = rest.getForEntity(
                "/admin/billing/summaries/" + tenant.customerId() + "?period=2001-01", Map.class);

        assertStatus(response, 404);
    }
}
