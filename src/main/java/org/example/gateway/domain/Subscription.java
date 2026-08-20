package org.example.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Binds a customer to the tier it is currently paying for. */
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tier_id", nullable = false)
    private Tier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Subscription() {
        // for JPA
    }

    public Subscription(Customer customer, Tier tier) {
        this.customer = customer;
        this.tier = tier;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Tier getTier() {
        return tier;
    }

    /** Upgrades or downgrades the customer. Takes effect on the next request. */
    public void changeTier(Tier newTier) {
        this.tier = newTier;
        this.updatedAt = Instant.now();
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
