package org.example.gateway.ratelimit;

/**
 * Strategy for short-window rate limiting.
 *
 * <p>The gateway only ever talks to this interface. The shipped implementation keeps buckets in
 * process memory, which is correct for a single node; swapping in a Redis-backed implementation for
 * a multi-node deployment is a matter of providing another bean, with no change to the filter.
 */
public interface RateLimiter {

    /**
     * Attempts to take one permit for {@code key} under {@code policy}.
     *
     * <p>The policy is supplied per call rather than registered up front, because tier configuration
     * is editable at runtime and every request must be judged against the current values.
     */
    RateLimitDecision tryAcquire(String key, RateLimitPolicy policy);

    /** Drops any state held for the key. Used by tests and by admin key-revocation. */
    void reset(String key);

    /** Number of keys currently holding state; exposed for monitoring and eviction tests. */
    int trackedKeys();
}
