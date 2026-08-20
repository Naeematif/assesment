package org.example.gateway.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.example.gateway.domain.Tier;
import org.springframework.stereotype.Service;

/**
 * Turns a request count into money.
 *
 * <p>Kept as a pure function of (count, tier) with no I/O so the pricing rules can be unit tested
 * exhaustively, which matters more here than anywhere else in the system: this is the code that
 * decides what a customer is charged.
 *
 * <p>Rounding is HALF_UP to two decimals, applied once to each charge component rather than to the
 * running total, so a million requests at $0.0005 costs exactly $500.00 and not $0.00.
 */
@Service
public class BillingService {

    private static final int MONEY_SCALE = 2;

    public PricedUsage price(long totalRequests, Tier tier) {
        long quota = Math.max(0, tier.getMonthlyQuota());
        long included = Math.min(totalRequests, quota);

        // A tier without overage is a hard cap; the gateway already rejected anything above the
        // quota, so treating any excess as unbilled is the conservative reading.
        long overage = tier.isOverageAllowed() ? Math.max(0, totalRequests - quota) : 0;

        BigDecimal baseCharge = scale(tier.getMonthlyPrice());
        BigDecimal overageCharge = scale(tier.getOveragePricePerRequest()
                .multiply(BigDecimal.valueOf(overage)));

        return new PricedUsage(totalRequests, included, overage, baseCharge, overageCharge,
                baseCharge.add(overageCharge));
    }

    private BigDecimal scale(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
