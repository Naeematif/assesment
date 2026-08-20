package org.example.gateway.config;

import java.time.Clock;
import org.example.gateway.auth.CredentialResolver;
import org.example.gateway.error.GatewayErrorWriter;
import org.example.gateway.gateway.MonetizationGatewayFilter;
import org.example.gateway.quota.QuotaService;
import org.example.gateway.ratelimit.RateLimiter;
import org.example.gateway.usage.UsageTrackingService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class GatewayFilterConfig {

    /**
     * Registers the gateway filter ahead of everything else in the chain, and scopes it to the
     * metered path prefix so admin and actuator endpoints are never charged to a customer.
     */
    @Bean
    public FilterRegistrationBean<MonetizationGatewayFilter> monetizationGatewayFilter(
            CredentialResolver credentialResolver,
            RateLimiter rateLimiter,
            QuotaService quotaService,
            UsageTrackingService usageTrackingService,
            GatewayErrorWriter errorWriter,
            GatewayProperties properties,
            Clock clock) {

        MonetizationGatewayFilter filter = new MonetizationGatewayFilter(credentialResolver, rateLimiter,
                quotaService, usageTrackingService, errorWriter, properties, clock);

        FilterRegistrationBean<MonetizationGatewayFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(properties.getMeteredPathPrefix() + "*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("monetizationGatewayFilter");
        return registration;
    }
}
