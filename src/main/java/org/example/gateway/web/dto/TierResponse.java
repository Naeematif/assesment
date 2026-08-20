package org.example.gateway.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import org.example.gateway.domain.Tier;

public record TierResponse(Long id,
                           String code,
                           String displayName,
                           long monthlyQuota,
                           int rateLimitPerSecond,
                           int burstCapacity,
                           BigDecimal monthlyPrice,
                           BigDecimal overagePricePerRequest,
                           boolean overageAllowed,
                           boolean active,
                           Instant updatedAt) {

    public static TierResponse from(Tier tier) {
        return new TierResponse(tier.getId(), tier.getCode(), tier.getDisplayName(), tier.getMonthlyQuota(),
                tier.getRateLimitPerSecond(), tier.getBurstCapacity(), tier.getMonthlyPrice(),
                tier.getOveragePricePerRequest(), tier.isOverageAllowed(), tier.isActive(),
                tier.getUpdatedAt());
    }
}
