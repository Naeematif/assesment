package org.example.gateway.auth;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.example.gateway.config.GatewayProperties;
import org.example.gateway.domain.ApiKey;
import org.example.gateway.domain.ApiKeyStatus;
import org.example.gateway.domain.CustomerStatus;
import org.example.gateway.domain.Subscription;
import org.example.gateway.domain.SubscriptionStatus;
import org.example.gateway.error.ErrorCode;
import org.example.gateway.error.GatewayException;
import org.example.gateway.ratelimit.Ticker;
import org.example.gateway.repository.ApiKeyRepository;
import org.example.gateway.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

/**
 * Turns a raw API key into a {@link ResolvedCredential}, or explains why it cannot.
 *
 * <p>Authentication runs on every single request, so the resolved binding is cached in memory for a
 * short TTL. Two properties matter: the TTL is short enough that a revoked key or a tier change goes
 * live quickly on its own, and every admin write calls {@link #invalidateAll()} so in practice
 * changes are visible immediately. Failures are cached too, otherwise a misconfigured client
 * hammering with a bad key would turn into a database load test.
 */
@Service
public class CredentialResolver {

    private final ApiKeyRepository apiKeyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final Ticker ticker;
    private final long ttlNanos;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CredentialResolver(ApiKeyRepository apiKeyRepository,
                              SubscriptionRepository subscriptionRepository,
                              ApiKeyGenerator apiKeyGenerator,
                              GatewayProperties properties,
                              Ticker ticker) {
        this.apiKeyRepository = apiKeyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.apiKeyGenerator = apiKeyGenerator;
        this.ticker = ticker;
        Duration ttl = properties.getCache().getCredentialTtl();
        this.ttlNanos = ttl == null ? 0 : ttl.toNanos();
    }

    /**
     * @throws GatewayException when the key is unknown, revoked, or its account cannot be served
     */
    public ResolvedCredential resolve(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new GatewayException(ErrorCode.MISSING_API_KEY);
        }
        String hash = apiKeyGenerator.hash(rawKey.trim());
        long now = ticker.nanos();

        CacheEntry cached = cache.get(hash);
        if (cached != null && cached.expiresAtNanos() - now > 0) {
            return cached.unwrap();
        }

        CacheEntry entry = load(hash);
        cache.put(hash, entry.withExpiry(now + ttlNanos));
        return entry.unwrap();
    }

    /** Called by every admin mutation so configuration changes are picked up on the next request. */
    public void invalidateAll() {
        cache.clear();
    }

    public int cachedEntries() {
        return cache.size();
    }

    private CacheEntry load(String hash) {
        Optional<ApiKey> maybeKey = apiKeyRepository.findByKeyHash(hash);
        if (maybeKey.isEmpty()) {
            return CacheEntry.failure(ErrorCode.INVALID_API_KEY, ErrorCode.INVALID_API_KEY.defaultMessage());
        }
        ApiKey apiKey = maybeKey.get();
        if (apiKey.getStatus() != ApiKeyStatus.ACTIVE) {
            return CacheEntry.failure(ErrorCode.API_KEY_REVOKED, ErrorCode.API_KEY_REVOKED.defaultMessage());
        }
        if (apiKey.getCustomer().getStatus() != CustomerStatus.ACTIVE) {
            return CacheEntry.failure(ErrorCode.CUSTOMER_SUSPENDED, ErrorCode.CUSTOMER_SUSPENDED.defaultMessage());
        }

        Optional<Subscription> maybeSubscription =
                subscriptionRepository.findByCustomerId(apiKey.getCustomer().getId());
        if (maybeSubscription.isEmpty() || maybeSubscription.get().getStatus() != SubscriptionStatus.ACTIVE) {
            return CacheEntry.failure(ErrorCode.NO_ACTIVE_SUBSCRIPTION,
                    ErrorCode.NO_ACTIVE_SUBSCRIPTION.defaultMessage());
        }

        TierSnapshot tier = TierSnapshot.from(maybeSubscription.get().getTier());
        if (!tier.active()) {
            return CacheEntry.failure(ErrorCode.TIER_INACTIVE, ErrorCode.TIER_INACTIVE.defaultMessage());
        }

        ResolvedCredential credential = new ResolvedCredential(apiKey.getId(), apiKey.getKeyPrefix(),
                apiKey.getCustomer().getId(), apiKey.getCustomer().getName(), apiKey.getUserId(), tier);
        return CacheEntry.success(credential);
    }

    /** Cached authentication outcome - either a credential or the reason it was rejected. */
    protected record CacheEntry(ResolvedCredential credential, ErrorCode failure, String message,
                                long expiresAtNanos) {

        static CacheEntry success(ResolvedCredential credential) {
            return new CacheEntry(credential, null, null, 0);
        }

        static CacheEntry failure(ErrorCode code, String message) {
            return new CacheEntry(null, code, message, 0);
        }

        CacheEntry withExpiry(long expiresAtNanos) {
            return new CacheEntry(credential, failure, message, expiresAtNanos);
        }

        ResolvedCredential unwrap() {
            if (failure != null) {
                throw new GatewayException(failure, message, Map.of());
            }
            return credential;
        }
    }
}
