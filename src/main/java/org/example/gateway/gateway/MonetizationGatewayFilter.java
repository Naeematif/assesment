package org.example.gateway.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.example.gateway.auth.CredentialResolver;
import org.example.gateway.auth.ResolvedCredential;
import org.example.gateway.config.GatewayProperties;
import org.example.gateway.error.ErrorCode;
import org.example.gateway.error.GatewayException;
import org.example.gateway.error.GatewayErrorWriter;
import org.example.gateway.quota.QuotaDecision;
import org.example.gateway.quota.QuotaService;
import org.example.gateway.ratelimit.RateLimitDecision;
import org.example.gateway.ratelimit.RateLimitScope;
import org.example.gateway.ratelimit.RateLimiter;
import org.example.gateway.usage.UsageRecord;
import org.example.gateway.usage.UsageTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * The monetization gateway itself.
 *
 * <p>Every metered request runs the same pipeline:
 * <ol>
 *   <li><b>Authenticate</b> - resolve the API key to a customer, user and tier.</li>
 *   <li><b>Rate limit</b> - per-second token bucket; rejected with 429 and a {@code Retry-After}.</li>
 *   <li><b>Quota</b> - monthly allowance; rejected with 429 and a distinct error code, or admitted
 *       as billable overage when the tier allows it.</li>
 *   <li><b>Proxy</b> - hand off to the internal service.</li>
 *   <li><b>Meter</b> - record the call for billing.</li>
 * </ol>
 *
 * <p>Order matters. Rate limiting is checked before quota because it is the cheaper, in-memory
 * check, and because a client in a hot retry loop should be told to slow down rather than have its
 * monthly allowance drained by traffic it is not even receiving.
 */
public class MonetizationGatewayFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MonetizationGatewayFilter.class);

    private final CredentialResolver credentialResolver;
    private final RateLimiter rateLimiter;
    private final QuotaService quotaService;
    private final UsageTrackingService usageTrackingService;
    private final GatewayErrorWriter errorWriter;
    private final GatewayProperties properties;
    private final Clock clock;

    public MonetizationGatewayFilter(CredentialResolver credentialResolver,
                                     RateLimiter rateLimiter,
                                     QuotaService quotaService,
                                     UsageTrackingService usageTrackingService,
                                     GatewayErrorWriter errorWriter,
                                     GatewayProperties properties,
                                     Clock clock) {
        this.credentialResolver = credentialResolver;
        this.rateLimiter = rateLimiter;
        this.quotaService = quotaService;
        this.usageTrackingService = usageTrackingService;
        this.errorWriter = errorWriter;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(properties.getMeteredPathPrefix());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        ResolvedCredential credential;
        String period;
        try {
            credential = credentialResolver.resolve(request.getHeader(properties.getApiKeyHeader()));
            response.setHeader(GatewayHeaders.TIER, credential.tier().code());

            enforceRateLimit(credential, response);

            period = quotaService.currentPeriod();
            enforceQuota(credential, response, period);
        } catch (GatewayException e) {
            log.debug("Rejected {} {}: {}", request.getMethod(), request.getRequestURI(), e.getCode());
            errorWriter.write(request, response, e);
            return;
        }

        publishRequestAttributes(request, credential);

        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
            meter(request, response, credential, period, latencyMs);
        }
    }

    private void enforceRateLimit(ResolvedCredential credential, HttpServletResponse response) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }
        RateLimitDecision decision =
                rateLimiter.tryAcquire(rateLimitKey(credential), credential.tier().toRateLimitPolicy());

        response.setHeader(GatewayHeaders.RATE_LIMIT_LIMIT, String.valueOf(decision.limit()));
        response.setHeader(GatewayHeaders.RATE_LIMIT_REMAINING, String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            response.setHeader(GatewayHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("limitPerSecond", decision.limit());
            details.put("retryAfterMillis", decision.retryAfterMillis());
            details.put("tier", credential.tier().code());
            throw new GatewayException(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Rate limit of %d requests per second exceeded for tier %s"
                            .formatted(decision.limit(), credential.tier().code()),
                    details);
        }
    }

    private void enforceQuota(ResolvedCredential credential, HttpServletResponse response, String period) {
        QuotaDecision decision = quotaService.tryConsume(credential.customerId(), credential.tier(), period);

        response.setHeader(GatewayHeaders.QUOTA_LIMIT, String.valueOf(decision.limit()));
        response.setHeader(GatewayHeaders.QUOTA_REMAINING, String.valueOf(decision.remaining()));
        response.setHeader(GatewayHeaders.QUOTA_PERIOD, period);

        if (!decision.allowed()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("monthlyQuota", decision.limit());
            details.put("billingPeriod", period);
            details.put("tier", credential.tier().code());
            details.put("resolution", "Upgrade the plan or wait for the quota to reset next month");
            throw new GatewayException(ErrorCode.QUOTA_EXCEEDED,
                    "Monthly quota of %d requests exhausted for tier %s in %s"
                            .formatted(decision.limit(), credential.tier().code(), period),
                    details);
        }
    }

    /**
     * Rate limiting is applied per account by default so that issuing more API keys does not
     * multiply the entitlement the customer paid for.
     */
    private String rateLimitKey(ResolvedCredential credential) {
        return properties.getRateLimit().getScope() == RateLimitScope.API_KEY
                ? "key:" + credential.apiKeyId()
                : "cust:" + credential.customerId();
    }

    private void publishRequestAttributes(HttpServletRequest request, ResolvedCredential credential) {
        request.setAttribute(GatewayHeaders.ATTR_CUSTOMER_ID, credential.customerId());
        request.setAttribute(GatewayHeaders.ATTR_USER_ID, credential.userId());
        request.setAttribute(GatewayHeaders.ATTR_TIER, credential.tier().code());
    }

    private void meter(HttpServletRequest request, HttpServletResponse response,
                       ResolvedCredential credential, String period, long latencyMs) {
        int status = response.getStatus();

        // A 5xx is our failure, so it is neither charged nor counted against the allowance.
        boolean billable = status < 500;
        if (!billable) {
            quotaService.refund(credential.customerId(), period);
        }

        usageTrackingService.record(new UsageRecord(
                credential.customerId(),
                credential.userId(),
                credential.apiKeyId(),
                resolveEndpoint(request),
                request.getMethod(),
                status,
                clock.instant(),
                latencyMs,
                period,
                credential.tier().code(),
                billable));
    }

    /**
     * Prefers the matched route template over the raw URI, so {@code /products/1} and
     * {@code /products/2} roll up into one line on the invoice instead of two million.
     */
    private String resolveEndpoint(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern instanceof String s && !s.isBlank() ? s : request.getRequestURI();
    }
}
