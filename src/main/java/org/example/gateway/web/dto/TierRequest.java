package org.example.gateway.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Payload for creating or updating a tier.
 *
 * @param burstCapacity optional; defaults to {@code rateLimitPerSecond}, i.e. no burst allowance
 */
public record TierRequest(@NotBlank String code,
                          @NotBlank String displayName,
                          @PositiveOrZero long monthlyQuota,
                          @Positive int rateLimitPerSecond,
                          @PositiveOrZero Integer burstCapacity,
                          @NotNull @DecimalMin("0.0") BigDecimal monthlyPrice,
                          @DecimalMin("0.0") BigDecimal overagePricePerRequest,
                          boolean overageAllowed,
                          Boolean active) {

    public int effectiveBurstCapacity() {
        return burstCapacity == null || burstCapacity <= 0 ? rateLimitPerSecond : burstCapacity;
    }

    public BigDecimal effectiveOveragePrice() {
        return overagePricePerRequest == null ? BigDecimal.ZERO : overagePricePerRequest;
    }
}
