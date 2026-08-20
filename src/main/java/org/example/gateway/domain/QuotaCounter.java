package org.example.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Durable per-customer, per-month request counter backing the monthly quota check.
 *
 * <p>Unlike the rate limiter this cannot live in memory: a monthly allowance has to survive
 * restarts and be shared by every gateway instance. The counter is advanced with a single
 * conditional {@code UPDATE ... WHERE used < :limit}, which makes the check-and-increment atomic in
 * the database rather than racy in application code.
 */
@Entity
@Table(name = "quota_counter",
        uniqueConstraints = @UniqueConstraint(name = "uk_quota_customer_period",
                columnNames = {"customer_id", "billing_period"}))
public class QuotaCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "billing_period", nullable = false, length = 7)
    private String billingPeriod;

    /** Snapshot of the tier quota, kept for reporting; enforcement always uses the live tier value. */
    @Column(name = "quota_limit", nullable = false)
    private long quotaLimit;

    @Column(name = "used", nullable = false)
    private long used;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected QuotaCounter() {
        // for JPA
    }

    public QuotaCounter(Long customerId, String billingPeriod, long quotaLimit) {
        this.customerId = customerId;
        this.billingPeriod = billingPeriod;
        this.quotaLimit = quotaLimit;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public long getQuotaLimit() {
        return quotaLimit;
    }

    public long getUsed() {
        return used;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
