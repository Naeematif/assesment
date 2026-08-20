package org.example.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

/**
 * Shared setup for the end-to-end tests.
 *
 * <p>These run against a real embedded servlet container over real HTTP, so the servlet filter, the
 * header plumbing, the status codes and the database all participate. Anything that passes here is
 * something a consumer would actually experience.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        // Seed data would collide with the fixtures each test provisions for itself.
        "gateway.seed.enabled=false",
        // Persist usage on the request thread so assertions need no sleeps.
        "gateway.usage.async=false",
        // Never let a stale credential snapshot mask a configuration change under test.
        "gateway.cache.credential-ttl=0s"
})
abstract class AbstractGatewayIntegrationTest {

    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Autowired
    protected TestRestTemplate rest;

    /** A provisioned consumer: its account id, its plan, and a working API key. */
    protected record Tenant(Long customerId, String tierCode, String apiKey) {
    }

    protected static String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + UNIQUE.incrementAndGet();
    }

    /** Creates a dedicated tier and a customer on it, so tests never interfere with each other. */
    protected Tenant provision(long monthlyQuota, int rateLimitPerSecond, String monthlyPrice,
                               String overagePrice, boolean overageAllowed) {
        String tierCode = unique("TIER").toUpperCase();

        ResponseEntity<Map<String, Object>> tier = postJson("/admin/tiers", Map.of(
                "code", tierCode,
                "displayName", tierCode,
                "monthlyQuota", monthlyQuota,
                "rateLimitPerSecond", rateLimitPerSecond,
                "burstCapacity", rateLimitPerSecond,
                "monthlyPrice", new BigDecimal(monthlyPrice),
                "overagePricePerRequest", new BigDecimal(overagePrice),
                "overageAllowed", overageAllowed));
        assertThat(tier.getStatusCode().value()).isEqualTo(201);

        ResponseEntity<Map<String, Object>> customer = postJson("/admin/customers", Map.of(
                "name", "Test tenant",
                "email", unique("tenant") + "@example.com",
                "tierCode", tierCode,
                "userId", "user-1"));
        assertThat(customer.getStatusCode().value()).isEqualTo(201);

        Map<String, Object> body = customer.getBody();
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> apiKey = (Map<String, Object>) body.get("apiKey");
        Long customerId = ((Number) body.get("id")).longValue();

        return new Tenant(customerId, tierCode, (String) apiKey.get("apiKey"));
    }

    protected ResponseEntity<Map<String, Object>> callApi(String path, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null) {
            headers.set("X-API-Key", apiKey);
        }
        return exchange(path, HttpMethod.GET, new HttpEntity<>(headers), mapType());
    }

    protected ResponseEntity<Map<String, Object>> postJson(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), mapType());
    }

    protected ResponseEntity<Map<String, Object>> putJson(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), mapType());
    }

    protected List<Map<String, Object>> getList(String path) {
        return exchange(path, HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    protected Map<String, Object> getMap(String path) {
        return exchange(path, HttpMethod.GET, HttpEntity.EMPTY, mapType()).getBody();
    }

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, HttpEntity<?> entity,
                                           ParameterizedTypeReference<T> type) {
        return rest.exchange(path, method, entity, type);
    }

    private ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {
        };
    }

    /** The error {@code code} field, which is what clients are expected to branch on. */
    protected static String errorCode(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getBody()).isNotNull();
        return (String) response.getBody().get("code");
    }

    /**
     * The default error handler throws on 4xx/5xx; {@link TestRestTemplate} already suppresses that,
     * but make it explicit for readers used to plain {@link RestTemplate}.
     */
    protected static void assertStatus(ResponseEntity<?> response, int expected) {
        assertThat(response.getStatusCode().value()).isEqualTo(expected);
    }
}
