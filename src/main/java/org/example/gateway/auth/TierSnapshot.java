package org.example.gateway.auth;

import java.math.BigDecimal;
import org.example.gateway.domain.Tier;
import org.example.gateway.ratelimit.RateLimitPolicy;

/**
 * Detached, immutable copy of the tier settings needed on the request path.
 *
 * <p>Snapshotting keeps JPA entities (and their lazy proxies and open-session requirements) out of
 * the filter, and makes the cached credential safe to share across threads.
 */
public record TierSnapshot(Long id,
                           String code,
                           long monthlyQuota,
                           int rateLimitPerSecond,
                           int burstCapacity,
                           BigDecimal monthlyPrice,
                           BigDecimal overagePricePerRequest,
                           boolean overageAllowed,
                           boolean active) {

    public static TierSnapshot from(Tier tier) {
        return new TierSnapshot(tier.getId(), tier.getCode(), tier.getMonthlyQuota(),
                tier.getRateLimitPerSecond(),
                tier.getBurstCapacity() > 0 ? tier.getBurstCapacity() : tier.getRateLimitPerSecond(),
                tier.getMonthlyPrice(), tier.getOveragePricePerRequest(), tier.isOverageAllowed(),
                tier.isActive());
    }

    public RateLimitPolicy toRateLimitPolicy() {
        return new RateLimitPolicy(rateLimitPerSecond, burstCapacity);
    }
}
