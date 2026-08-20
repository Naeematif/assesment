package org.example.gateway.config;

import java.time.Duration;
import org.example.gateway.ratelimit.RateLimitScope;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised gateway configuration.
 *
 * <p>Note that <em>tier</em> configuration deliberately does not live here: tiers are stored in the
 * database and are editable at runtime through the admin API. This class only holds infrastructure
 * level settings.
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /** Header carrying the consumer's API key. */
    private String apiKeyHeader = "X-API-Key";

    /** Only requests below this prefix are metered and billed. */
    private String meteredPathPrefix = "/api/";

    private final RateLimit rateLimit = new RateLimit();
    private final Usage usage = new Usage();
    private final Billing billing = new Billing();
    private final Cache cache = new Cache();
    private final Seed seed = new Seed();

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    public void setApiKeyHeader(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader;
    }

    public String getMeteredPathPrefix() {
        return meteredPathPrefix;
    }

    public void setMeteredPathPrefix(String meteredPathPrefix) {
        this.meteredPathPrefix = meteredPathPrefix;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Usage getUsage() {
        return usage;
    }

    public Billing getBilling() {
        return billing;
    }

    public Cache getCache() {
        return cache;
    }

    public Seed getSeed() {
        return seed;
    }

    public static class RateLimit {
        /** Master switch, mostly useful for load tests and local debugging. */
        private boolean enabled = true;

        /** Whether the per-second limit is shared by the account or held per API key. */
        private RateLimitScope scope = RateLimitScope.CUSTOMER;

        /** Buckets untouched for longer than this are evicted to bound memory. */
        private Duration bucketIdleTimeout = Duration.ofMinutes(15);

        private Duration evictionInterval = Duration.ofMinutes(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RateLimitScope getScope() {
            return scope;
        }

        public void setScope(RateLimitScope scope) {
            this.scope = scope;
        }

        public Duration getBucketIdleTimeout() {
            return bucketIdleTimeout;
        }

        public void setBucketIdleTimeout(Duration bucketIdleTimeout) {
            this.bucketIdleTimeout = bucketIdleTimeout;
        }

        public Duration getEvictionInterval() {
            return evictionInterval;
        }

        public void setEvictionInterval(Duration evictionInterval) {
            this.evictionInterval = evictionInterval;
        }
    }

    public static class Usage {
        /**
         * When true, usage events are persisted on a background executor so that logging never adds
         * latency to the proxied call. Tests flip this off to get deterministic assertions.
         */
        private boolean async = true;

        private int queueCapacity = 10_000;
        private int workerThreads = 2;

        public boolean isAsync() {
            return async;
        }

        public void setAsync(boolean async) {
            this.async = async;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
        }
    }

    public static class Billing {
        /** Defaults to 02:15 on the first day of every month. */
        private String aggregationCron = "0 15 2 1 * *";

        private String timezone = "UTC";

        public String getAggregationCron() {
            return aggregationCron;
        }

        public void setAggregationCron(String aggregationCron) {
            this.aggregationCron = aggregationCron;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }
    }

    /** Demo data created at startup so the service is explorable straight after {@code mvn spring-boot:run}. */
    public static class Seed {
        private boolean enabled = true;
        private String freeApiKey = "amk_live_demo_free_key";
        private String proApiKey = "amk_live_demo_pro_key";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFreeApiKey() {
            return freeApiKey;
        }

        public void setFreeApiKey(String freeApiKey) {
            this.freeApiKey = freeApiKey;
        }

        public String getProApiKey() {
            return proApiKey;
        }

        public void setProApiKey(String proApiKey) {
            this.proApiKey = proApiKey;
        }
    }

    public static class Cache {
        /**
         * How long a resolved API key -> customer/tier binding is cached. Keeps the hot path off the
         * database while still picking up tier changes quickly; writes invalidate eagerly.
         */
        private Duration credentialTtl = Duration.ofSeconds(30);

        public Duration getCredentialTtl() {
            return credentialTtl;
        }

        public void setCredentialTtl(Duration credentialTtl) {
            this.credentialTtl = credentialTtl;
        }
    }
}
