package org.example.gateway.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.example.gateway.domain.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Pricing rules, tested as a pure function because this is the code that decides what people pay. */
class BillingServiceTest {

    private final BillingService billingService = new BillingService();

    private static Tier freeTier() {
        return new Tier("FREE", "Free", 100, 2, 2, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    private static Tier proTier() {
        return new Tier("PRO", "Pro", 100_000, 10, 20,
                new BigDecimal("50.00"), new BigDecimal("0.001000"), true);
    }

    @Nested
    @DisplayName("Free tier - $0, hard capped at 100 requests")
    class Free {

        @Test
        void chargesNothingForUsageInsideTheQuota() {
            PricedUsage priced = billingService.price(87, freeTier());

            assertThat(priced.includedRequests()).isEqualTo(87);
            assertThat(priced.overageRequests()).isZero();
            assertThat(priced.totalCharge()).isEqualByComparingTo("0.00");
        }

        @Test
        void chargesNothingEvenAtExactlyTheQuota() {
            assertThat(billingService.price(100, freeTier()).totalCharge()).isEqualByComparingTo("0.00");
        }

        @Test
        void neverBillsOverageBecauseTheTierDoesNotSellIt() {
            // The gateway rejects these calls, so if any leaked through they are not chargeable.
            PricedUsage priced = billingService.price(150, freeTier());

            assertThat(priced.includedRequests()).isEqualTo(100);
            assertThat(priced.overageRequests()).isZero();
            assertThat(priced.totalCharge()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Pro tier - $50/month, 100k included, $0.001 per extra request")
    class Pro {

        @Test
        void chargesTheFlatFeeWhenUsageIsInsideTheQuota() {
            PricedUsage priced = billingService.price(42_000, proTier());

            assertThat(priced.includedRequests()).isEqualTo(42_000);
            assertThat(priced.overageRequests()).isZero();
            assertThat(priced.baseCharge()).isEqualByComparingTo("50.00");
            assertThat(priced.totalCharge()).isEqualByComparingTo("50.00");
        }

        @Test
        void chargesTheFlatFeeEvenWithNoUsageAtAll() {
            // A subscription is a subscription: an idle month still owes the fee.
            assertThat(billingService.price(0, proTier()).totalCharge()).isEqualByComparingTo("50.00");
        }

        @Test
        void addsOverageForRequestsBeyondTheQuota() {
            PricedUsage priced = billingService.price(150_000, proTier());

            assertThat(priced.includedRequests()).isEqualTo(100_000);
            assertThat(priced.overageRequests()).isEqualTo(50_000);
            assertThat(priced.overageCharge()).isEqualByComparingTo("50.00"); // 50k x $0.001
            assertThat(priced.totalCharge()).isEqualByComparingTo("100.00");
        }

        @Test
        void doesNotRoundSmallPerRequestPricesAwayToZero() {
            // Rounding the unit price instead of the line total would bill $0.00 here.
            PricedUsage priced = billingService.price(100_001, proTier());

            assertThat(priced.overageRequests()).isEqualTo(1);
            assertThat(priced.overageCharge()).isEqualByComparingTo("0.00");
            assertThat(priced.totalCharge()).isEqualByComparingTo("50.00");

            PricedUsage larger = billingService.price(101_000, proTier());
            assertThat(larger.overageCharge()).isEqualByComparingTo("1.00");
        }

        @Test
        void roundsHalfUpToCents() {
            Tier tier = new Tier("CUSTOM", "Custom", 0, 10, 10,
                    BigDecimal.ZERO, new BigDecimal("0.005"), true);

            assertThat(billingService.price(1, tier).totalCharge()).isEqualByComparingTo("0.01");
            assertThat(billingService.price(3, tier).totalCharge()).isEqualByComparingTo("0.02");
        }
    }

    @Test
    void alwaysReportsMoneyWithTwoDecimals() {
        assertThat(billingService.price(10, proTier()).totalCharge().scale()).isEqualTo(2);
        assertThat(billingService.price(10, freeTier()).totalCharge().scale()).isEqualTo(2);
    }
}
