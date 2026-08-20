package org.example.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A credential issued to one user inside a customer account.
 *
 * <p>Only the SHA-256 hash of the key is stored; the plaintext is shown to the operator exactly
 * once at creation time, so a database leak does not hand out working credentials.
 */
@Entity
@Table(name = "api_key", indexes = @Index(name = "idx_api_key_hash", columnList = "key_hash", unique = true))
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    /** Non-secret leading segment, used for display and support ("which key is this?"). */
    @Column(name = "key_prefix", nullable = false, length = 20)
    private String keyPrefix;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Identifier of the individual user/service the key was issued to. */
    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Column(name = "label", length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ApiKey() {
        // for JPA
    }

    public ApiKey(String keyHash, String keyPrefix, Customer customer, String userId, String label) {
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.customer = customer;
        this.userId = userId;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public void setStatus(ApiKeyStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
