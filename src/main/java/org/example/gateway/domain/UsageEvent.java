package org.example.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Immutable record of one metered call through the gateway.
 *
 * <p>This is the raw event stream the monthly aggregation job reads. It is intentionally
 * denormalised (customer id, user id, tier code and billing period are copied in) so that a
 * historical event still prices correctly after the customer changes tier, and so the aggregation
 * query needs no joins.
 */
@Entity
@Table(name = "usage_event", indexes = {
        @Index(name = "idx_usage_period_customer", columnList = "billing_period,customer_id"),
        @Index(name = "idx_usage_occurred_at", columnList = "occurred_at")
})
public class UsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /** Route template rather than the raw URI, so {@code /products/1} and {@code /products/2} group. */
    @Column(name = "endpoint", nullable = false, length = 300)
    private String endpoint;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "billing_period", nullable = false, length = 7)
    private String billingPeriod;

    @Column(name = "tier_code", nullable = false, length = 40)
    private String tierCode;

    /** False for calls that were served but must not be charged (e.g. a 5xx from our own backend). */
    @Column(name = "billable", nullable = false)
    private boolean billable;

    protected UsageEvent() {
        // for JPA
    }

    public UsageEvent(Long customerId, String userId, Long apiKeyId, String endpoint, String httpMethod,
                      int statusCode, Instant occurredAt, long latencyMs, String billingPeriod,
                      String tierCode, boolean billable) {
        this.customerId = customerId;
        this.userId = userId;
        this.apiKeyId = apiKeyId;
        this.endpoint = endpoint;
        this.httpMethod = httpMethod;
        this.statusCode = statusCode;
        this.occurredAt = occurredAt;
        this.latencyMs = latencyMs;
        this.billingPeriod = billingPeriod;
        this.tierCode = tierCode;
        this.billable = billable;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getUserId() {
        return userId;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public String getTierCode() {
        return tierCode;
    }

    public boolean isBillable() {
        return billable;
    }
}
