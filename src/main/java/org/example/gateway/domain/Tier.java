package org.example.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A commercial tier (Free, Pro, ...).
 *
 * <p>Tier configuration is data, not code: quotas, rate limits and pricing are stored here and can
 * be changed at runtime through the admin API without a redeploy. Adding a new tier is an INSERT.
 */
@Entity
@Table(name = "tier")
public class Tier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable machine-readable identifier, e.g. {@code FREE}, {@code PRO}. */
    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /** Requests allowed per calendar month. */
    @Column(name = "monthly_quota", nullable = false)
    private long monthlyQuota;

    /** Sustained requests per second. */
    @Column(name = "rate_limit_per_second", nullable = false)
    private int rateLimitPerSecond;

    /**
     * Maximum burst size of the token bucket. Defaults to {@code rateLimitPerSecond} when not set,
     * which yields a strict "N per second" behaviour.
     */
    @Column(name = "burst_capacity", nullable = false)
    private int burstCapacity;

    /** Recurring subscription fee charged for the month. */
    @Column(name = "monthly_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal monthlyPrice = BigDecimal.ZERO;

    /** Price of each request beyond {@link #monthlyQuota}; only used when overage is allowed. */
    @Column(name = "overage_price_per_request", nullable = false, precision = 12, scale = 6)
    private BigDecimal overagePricePerRequest = BigDecimal.ZERO;

    /**
     * When false the quota is a hard cap and requests beyond it are rejected with HTTP 429. When
     * true the gateway keeps serving and bills the excess at {@link #overagePricePerRequest}.
     */
    @Column(name = "overage_allowed", nullable = false)
    private boolean overageAllowed;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Tier() {
        // for JPA
    }

    public Tier(String code, String displayName, long monthlyQuota, int rateLimitPerSecond,
                int burstCapacity, BigDecimal monthlyPrice, BigDecimal overagePricePerRequest,
                boolean overageAllowed) {
        this.code = code;
        this.displayName = displayName;
        this.monthlyQuota = monthlyQuota;
        this.rateLimitPerSecond = rateLimitPerSecond;
        this.burstCapacity = burstCapacity;
        this.monthlyPrice = monthlyPrice;
        this.overagePricePerRequest = overagePricePerRequest;
        this.overageAllowed = overageAllowed;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public long getMonthlyQuota() {
        return monthlyQuota;
    }

    public void setMonthlyQuota(long monthlyQuota) {
        this.monthlyQuota = monthlyQuota;
    }

    public int getRateLimitPerSecond() {
        return rateLimitPerSecond;
    }

    public void setRateLimitPerSecond(int rateLimitPerSecond) {
        this.rateLimitPerSecond = rateLimitPerSecond;
    }

    public int getBurstCapacity() {
        return burstCapacity;
    }

    public void setBurstCapacity(int burstCapacity) {
        this.burstCapacity = burstCapacity;
    }

    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    public void setMonthlyPrice(BigDecimal monthlyPrice) {
        this.monthlyPrice = monthlyPrice;
    }

    public BigDecimal getOveragePricePerRequest() {
        return overagePricePerRequest;
    }

    public void setOveragePricePerRequest(BigDecimal overagePricePerRequest) {
        this.overagePricePerRequest = overagePricePerRequest;
    }

    public boolean isOverageAllowed() {
        return overageAllowed;
    }

    public void setOverageAllowed(boolean overageAllowed) {
        this.overageAllowed = overageAllowed;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
