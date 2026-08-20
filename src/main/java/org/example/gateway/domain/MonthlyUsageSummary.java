package org.example.gateway.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Output of the monthly aggregation job: one priced row per customer per billing period, with a
 * per-endpoint breakdown attached.
 */
@Entity
@Table(name = "monthly_usage_summary",
        uniqueConstraints = @UniqueConstraint(name = "uk_summary_customer_period",
                columnNames = {"customer_id", "billing_period"}))
public class MonthlyUsageSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "billing_period", nullable = false, length = 7)
    private String billingPeriod;

    @Column(name = "tier_code", nullable = false, length = 40)
    private String tierCode;

    @Column(name = "total_requests", nullable = false)
    private long totalRequests;

    /** Requests covered by the subscription fee. */
    @Column(name = "included_requests", nullable = false)
    private long includedRequests;

    /** Requests beyond the quota, charged per call. */
    @Column(name = "overage_requests", nullable = false)
    private long overageRequests;

    @Column(name = "base_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseCharge = BigDecimal.ZERO;

    @Column(name = "overage_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal overageCharge = BigDecimal.ZERO;

    @Column(name = "total_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCharge = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    @OneToMany(mappedBy = "summary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("requestCount DESC")
    private List<EndpointUsageSummary> endpoints = new ArrayList<>();

    protected MonthlyUsageSummary() {
        // for JPA
    }

    public MonthlyUsageSummary(Long customerId, String billingPeriod, String tierCode) {
        this.customerId = customerId;
        this.billingPeriod = billingPeriod;
        this.tierCode = tierCode;
    }

    public void addEndpoint(EndpointUsageSummary endpoint) {
        endpoint.setSummary(this);
        this.endpoints.add(endpoint);
    }

    public void clearEndpoints() {
        this.endpoints.clear();
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

    public String getTierCode() {
        return tierCode;
    }

    public void setTierCode(String tierCode) {
        this.tierCode = tierCode;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getIncludedRequests() {
        return includedRequests;
    }

    public void setIncludedRequests(long includedRequests) {
        this.includedRequests = includedRequests;
    }

    public long getOverageRequests() {
        return overageRequests;
    }

    public void setOverageRequests(long overageRequests) {
        this.overageRequests = overageRequests;
    }

    public BigDecimal getBaseCharge() {
        return baseCharge;
    }

    public void setBaseCharge(BigDecimal baseCharge) {
        this.baseCharge = baseCharge;
    }

    public BigDecimal getOverageCharge() {
        return overageCharge;
    }

    public void setOverageCharge(BigDecimal overageCharge) {
        this.overageCharge = overageCharge;
    }

    public BigDecimal getTotalCharge() {
        return totalCharge;
    }

    public void setTotalCharge(BigDecimal totalCharge) {
        this.totalCharge = totalCharge;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<EndpointUsageSummary> getEndpoints() {
        return endpoints;
    }
}
