package org.example.gateway.config;

import java.time.Clock;
import java.time.ZoneId;
import org.example.gateway.ratelimit.Ticker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Time is injected, never called statically.
 *
 * <p>Billing depends on which calendar month a request falls in, so the zone is an explicit
 * configuration value rather than whatever the host happens to be set to. Both beans are
 * {@code @ConditionalOnMissingBean} so tests can substitute a fixed clock or a fake ticker.
 */
@Configuration
public class ClockConfig {

    /** Wall-clock time, used for billing periods and timestamps. */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock(GatewayProperties properties) {
        return Clock.system(ZoneId.of(properties.getBilling().getTimezone()));
    }

    /** Monotonic time, used for rate limiting where wall-clock jumps would be a correctness bug. */
    @Bean
    @ConditionalOnMissingBean
    public Ticker ticker() {
        return Ticker.SYSTEM;
    }
}
