package org.example.gateway.ratelimit;

/**
 * Outcome of a rate limit check, carrying everything needed to populate the response headers.
 *
 * @param allowed         whether the permit was granted
 * @param limit           the configured permits per second
 * @param remaining       permits still available after this decision
 * @param retryAfterMillis how long the caller should wait before retrying; 0 when allowed
 */
public record RateLimitDecision(boolean allowed, int limit, long remaining, long retryAfterMillis) {

    public static RateLimitDecision allowed(int limit, long remaining) {
        return new RateLimitDecision(true, limit, remaining, 0);
    }

    public static RateLimitDecision denied(int limit, long retryAfterMillis) {
        return new RateLimitDecision(false, limit, 0, retryAfterMillis);
    }

    /** Retry-After is expressed in whole seconds by RFC 9110, rounded up so it is never premature. */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfterMillis + 999) / 1000);
    }
}
