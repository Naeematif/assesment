package org.example.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the rate limiting algorithm.
 *
 * <p>These are the tests that matter most for the tier enforcement requirement: they pin down the
 * exact number of requests admitted, how fast permits come back, and that the limiter stays correct
 * when a tier's configuration changes underneath it.
 */
class TokenBucketRateLimiterTest {

    private static final String KEY = "cust:1";

    private FakeTicker ticker;
    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        ticker = new FakeTicker();
        limiter = new TokenBucketRateLimiter(ticker);
    }

    @Nested
    @DisplayName("Free tier: 2 requests per second")
    class FreeTier {

        private final RateLimitPolicy policy = RateLimitPolicy.perSecond(2);

        @Test
        void admitsExactlyTheConfiguredNumberOfRequestsInstantly() {
            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isTrue();
            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isTrue();

            RateLimitDecision third = limiter.tryAcquire(KEY, policy);
            assertThat(third.allowed()).isFalse();
            assertThat(third.limit()).isEqualTo(2);
            assertThat(third.remaining()).isZero();
        }

        @Test
        void reportsRemainingPermitsAsTheyAreConsumed() {
            assertThat(limiter.tryAcquire(KEY, policy).remaining()).isEqualTo(1);
            assertThat(limiter.tryAcquire(KEY, policy).remaining()).isZero();
        }

        @Test
        void refillsGraduallyRatherThanAllAtOnce() {
            limiter.tryAcquire(KEY, policy);
            limiter.tryAcquire(KEY, policy);
            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isFalse();

            // At 2 permits/second one token is worth 500ms.
            ticker.advanceMillis(499);
            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isFalse();

            ticker.advanceMillis(1);
            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isTrue();
        }

        @Test
        void tellsTheCallerHowLongToWait() {
            limiter.tryAcquire(KEY, policy);
            limiter.tryAcquire(KEY, policy);

            RateLimitDecision denied = limiter.tryAcquire(KEY, policy);
            assertThat(denied.retryAfterMillis()).isEqualTo(500);
            // Retry-After is whole seconds and must never point at a moment that is still too early.
            assertThat(denied.retryAfterSeconds()).isEqualTo(1);
        }

        @Test
        void neverAccumulatesMorePermitsThanTheBurstCapacity() {
            // A client idle for an hour must not be able to fire 7200 requests at once.
            ticker.advance(Duration.ofHours(1));

            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isTrue();
            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isTrue();
            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Pro tier: 10 requests per second")
    class ProTier {

        private final RateLimitPolicy policy = RateLimitPolicy.perSecond(10);

        @Test
        void admitsTenRequestsPerSecondSustained() {
            for (int second = 0; second < 5; second++) {
                for (int i = 0; i < 10; i++) {
                    assertThat(limiter.tryAcquire(KEY, policy).allowed())
                            .as("request %d of second %d", i, second)
                            .isTrue();
                }
                assertThat(limiter.tryAcquire(KEY, policy).allowed())
                        .as("11th request of second %d", second)
                        .isFalse();
                ticker.advance(Duration.ofSeconds(1));
            }
        }
    }

    @Nested
    @DisplayName("Burst capacity above the sustained rate")
    class Burst {

        @Test
        void allowsAShortSpikeThenSettlesToTheSustainedRate() {
            RateLimitPolicy policy = new RateLimitPolicy(10, 20);

            for (int i = 0; i < 20; i++) {
                assertThat(limiter.tryAcquire(KEY, policy).allowed()).as("burst request %d", i).isTrue();
            }
            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isFalse();

            // After the spike the client is back to the sustained 10 per second.
            ticker.advance(Duration.ofSeconds(1));
            for (int i = 0; i < 10; i++) {
                assertThat(limiter.tryAcquire(KEY, policy).allowed()).isTrue();
            }
            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Dynamic tier reconfiguration")
    class DynamicPolicy {

        @Test
        void anUpgradeAppliesToTheVeryNextRequest() {
            RateLimitPolicy free = RateLimitPolicy.perSecond(2);
            limiter.tryAcquire(KEY, free);
            limiter.tryAcquire(KEY, free);
            assertThat(limiter.tryAcquire(KEY, free).allowed()).isFalse();

            // Customer upgrades to Pro; the same bucket is now judged against the new policy.
            RateLimitPolicy pro = RateLimitPolicy.perSecond(10);
            ticker.advance(Duration.ofSeconds(1));
            for (int i = 0; i < 10; i++) {
                assertThat(limiter.tryAcquire(KEY, pro).allowed()).as("request %d after upgrade", i).isTrue();
            }
        }

        @Test
        void aDowngradeClampsSurplusPermitsImmediately() {
            RateLimitPolicy pro = RateLimitPolicy.perSecond(10);
            limiter.tryAcquire(KEY, pro);
            ticker.advance(Duration.ofSeconds(1)); // bucket refills to 10

            // Operator drops the tier to 2/s. The 10 accumulated permits must not survive.
            RateLimitPolicy free = RateLimitPolicy.perSecond(2);
            assertThat(limiter.tryAcquire(KEY, free).allowed()).isTrue();
            assertThat(limiter.tryAcquire(KEY, free).allowed()).isTrue();
            assertThat(limiter.tryAcquire(KEY, free).allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Isolation and bookkeeping")
    class Isolation {

        @Test
        void keysDoNotShareBudgets() {
            RateLimitPolicy policy = RateLimitPolicy.perSecond(1);
            assertThat(limiter.tryAcquire("cust:1", policy).allowed()).isTrue();
            assertThat(limiter.tryAcquire("cust:1", policy).allowed()).isFalse();

            assertThat(limiter.tryAcquire("cust:2", policy).allowed()).isTrue();
        }

        @Test
        void evictsBucketsThatHaveBeenIdle() {
            RateLimitPolicy policy = RateLimitPolicy.perSecond(5);
            limiter.tryAcquire("cust:1", policy);
            limiter.tryAcquire("cust:2", policy);
            assertThat(limiter.trackedKeys()).isEqualTo(2);

            ticker.advance(Duration.ofMinutes(20));
            limiter.tryAcquire("cust:2", policy); // keeps this one warm

            assertThat(limiter.evictIdle(Duration.ofMinutes(15).toNanos())).isEqualTo(1);
            assertThat(limiter.trackedKeys()).isEqualTo(1);
        }

        @Test
        void resetDropsAKeysState() {
            RateLimitPolicy policy = RateLimitPolicy.perSecond(1);
            limiter.tryAcquire(KEY, policy);
            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isFalse();

            limiter.reset(KEY);
            assertThat(limiter.tryAcquire(KEY, policy).allowed()).isTrue();
        }
    }

    @Test
    @DisplayName("Concurrent callers never over-consume the budget")
    void isThreadSafe() throws Exception {
        int capacity = 100;
        int threads = 32;
        int attemptsPerThread = 10;
        RateLimitPolicy policy = new RateLimitPolicy(capacity, capacity);

        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < attemptsPerThread; i++) {
                            if (limiter.tryAcquire(KEY, policy).allowed()) {
                                allowed.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }

        // The ticker never moves, so no permits are refilled: exactly the capacity may pass.
        assertThat(allowed.get()).isEqualTo(capacity);
    }

    @Test
    void rejectsNonsensicalPolicies() {
        assertThatThrownBy(() -> new RateLimitPolicy(0, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permitsPerSecond");
        assertThatThrownBy(() -> new RateLimitPolicy(5, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("burstCapacity");
    }
}
