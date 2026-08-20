package org.example.gateway.config;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import org.example.gateway.ratelimit.RateLimiter;
import org.example.gateway.ratelimit.Ticker;
import org.example.gateway.ratelimit.TokenBucketRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Configuration
public class RateLimiterConfig {

    /**
     * In-memory limiter, correct for a single gateway node.
     *
     * <p>For a horizontally scaled deployment this bean is the single seam to replace: a
     * Redis-backed {@link RateLimiter} using an atomic Lua token bucket would give one shared budget
     * across nodes, and nothing else in the codebase would change.
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public TokenBucketRateLimiter rateLimiter(Ticker ticker) {
        return new TokenBucketRateLimiter(ticker);
    }

    /**
     * Buckets are created on demand and would otherwise accumulate one entry per key seen since
     * startup. Evicting idle buckets bounds memory; it cannot grant extra permits because an idle
     * bucket has already refilled to capacity.
     */
    @Component
    static class IdleBucketEvictor {

        private static final Logger log = LoggerFactory.getLogger(IdleBucketEvictor.class);

        private final TokenBucketRateLimiter rateLimiter;
        private final TaskScheduler taskScheduler;
        private final GatewayProperties properties;

        IdleBucketEvictor(TokenBucketRateLimiter rateLimiter, TaskScheduler taskScheduler,
                          GatewayProperties properties) {
            this.rateLimiter = rateLimiter;
            this.taskScheduler = taskScheduler;
            this.properties = properties;
        }

        @PostConstruct
        void schedule() {
            Duration interval = properties.getRateLimit().getEvictionInterval();
            Duration idleTimeout = properties.getRateLimit().getBucketIdleTimeout();
            taskScheduler.scheduleWithFixedDelay(() -> {
                int evicted = rateLimiter.evictIdle(idleTimeout.toNanos());
                if (evicted > 0) {
                    log.debug("Evicted {} idle rate limit buckets, {} remain", evicted,
                            rateLimiter.trackedKeys());
                }
            }, Instant.now().plus(interval), interval);
        }
    }
}
