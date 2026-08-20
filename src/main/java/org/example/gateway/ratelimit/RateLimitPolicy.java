package org.example.gateway.ratelimit;

/**
 * The limit to apply to one key, derived from the caller's tier at request time.
 *
 * @param permitsPerSecond sustained refill rate
 * @param burstCapacity    maximum number of permits that can accumulate; equal to
 *                         {@code permitsPerSecond} gives strict per-second behaviour, higher values
 *                         tolerate bursty clients
 */
public record RateLimitPolicy(int permitsPerSecond, int burstCapacity) {

    public RateLimitPolicy {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive, was " + permitsPerSecond);
        }
        if (burstCapacity <= 0) {
            throw new IllegalArgumentException("burstCapacity must be positive, was " + burstCapacity);
        }
    }

    public static RateLimitPolicy perSecond(int permitsPerSecond) {
        return new RateLimitPolicy(permitsPerSecond, permitsPerSecond);
    }
}
