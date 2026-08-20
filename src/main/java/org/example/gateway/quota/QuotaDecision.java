package org.example.gateway.quota;

/**
 * Outcome of the monthly quota check.
 *
 * @param allowed   whether the call may proceed
 * @param limit     the tier's monthly allowance
 * @param used      units consumed in the period including this call when allowed
 * @param remaining units left; zero once the allowance is gone
 * @param overage   true when the call was served beyond the quota and will be billed per request
 */
public record QuotaDecision(boolean allowed, long limit, long used, long remaining, boolean overage) {

    public static QuotaDecision allowed(long limit, long used, boolean overage) {
        return new QuotaDecision(true, limit, used, Math.max(0, limit - used), overage);
    }

    public static QuotaDecision exhausted(long limit) {
        return new QuotaDecision(false, limit, limit, 0, false);
    }
}
