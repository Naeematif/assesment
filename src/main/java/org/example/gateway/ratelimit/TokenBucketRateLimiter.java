package org.example.gateway.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free token bucket rate limiter.
 *
 * <p>Each key owns a bucket that refills continuously at {@code permitsPerSecond} up to
 * {@code burstCapacity}. A token bucket is preferred over a fixed window counter because a fixed
 * window lets a client fire {@code 2 * limit} requests across a window boundary; the bucket smooths
 * that out. It is preferred over a sliding log because it needs O(1) memory per key instead of one
 * timestamp per request.
 *
 * <p>Bucket state is a single immutable record swapped with compare-and-set, so concurrent requests
 * for the same key contend on a CAS rather than a lock, and a losing thread simply recomputes
 * against the winner's state.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Ticker ticker;

    public TokenBucketRateLimiter() {
        this(Ticker.SYSTEM);
    }

    public TokenBucketRateLimiter(Ticker ticker) {
        this.ticker = ticker;
    }

    @Override
    public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(policy.burstCapacity(), ticker.nanos()));
        return bucket.tryAcquire(policy, ticker.nanos());
    }

    @Override
    public void reset(String key) {
        buckets.remove(key);
    }

    @Override
    public int trackedKeys() {
        return buckets.size();
    }

    /**
     * Drops buckets that have not been touched for {@code idleNanos}.
     *
     * <p>Safe to do at any time: an idle bucket has necessarily refilled to full capacity, so
     * recreating it grants exactly the same permits it would have held.
     */
    public int evictIdle(long idleNanos) {
        long cutoff = ticker.nanos() - idleNanos;
        int before = buckets.size();
        buckets.entrySet().removeIf(entry -> entry.getValue().lastUpdatedNanos() - cutoff < 0);
        return before - buckets.size();
    }

    /** Immutable bucket state; replaced atomically on every decision. */
    private record State(double tokens, long lastRefillNanos) {
    }

    private static final class Bucket {

        private final AtomicReference<State> state;

        Bucket(double initialTokens, long nowNanos) {
            this.state = new AtomicReference<>(new State(initialTokens, nowNanos));
        }

        long lastUpdatedNanos() {
            return state.get().lastRefillNanos();
        }

        RateLimitDecision tryAcquire(RateLimitPolicy policy, long nowNanos) {
            while (true) {
                State current = state.get();
                double tokens = refill(current, policy, nowNanos);

                if (tokens >= 1.0d) {
                    State next = new State(tokens - 1.0d, nowNanos);
                    if (state.compareAndSet(current, next)) {
                        return RateLimitDecision.allowed(policy.permitsPerSecond(), (long) Math.floor(next.tokens()));
                    }
                    continue; // lost the race, recompute against the winner's state
                }

                // Not enough tokens. Persist the refill so the deficit is not recomputed from a stale
                // timestamp, then tell the caller exactly how long until one token is available.
                State next = new State(tokens, nowNanos);
                if (state.compareAndSet(current, next)) {
                    double missing = 1.0d - tokens;
                    long waitMillis = (long) Math.ceil(missing / policy.permitsPerSecond() * 1000.0d);
                    return RateLimitDecision.denied(policy.permitsPerSecond(), Math.max(1, waitMillis));
                }
            }
        }

        /**
         * Adds the tokens earned since the last update, capped at the burst capacity.
         *
         * <p>Reading the capacity from the policy on every call is deliberate: when an operator
         * lowers a tier's rate limit the surplus is clamped away immediately rather than lingering
         * until the bucket happens to drain.
         */
        private double refill(State current, RateLimitPolicy policy, long nowNanos) {
            long elapsed = nowNanos - current.lastRefillNanos();
            double tokens = current.tokens();
            if (elapsed > 0) {
                tokens += (double) elapsed / NANOS_PER_SECOND * policy.permitsPerSecond();
            }
            return Math.min(tokens, policy.burstCapacity());
        }
    }
}
