package org.example.gateway.gateway;

/** Response headers the gateway adds so consumers can self-regulate instead of guessing. */
public final class GatewayHeaders {

    public static final String RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    public static final String RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    public static final String QUOTA_LIMIT = "X-Quota-Limit";
    public static final String QUOTA_REMAINING = "X-Quota-Remaining";
    public static final String QUOTA_PERIOD = "X-Quota-Period";
    public static final String TIER = "X-Tier";
    public static final String RETRY_AFTER = "Retry-After";

    /** Request attributes published for downstream handlers. */
    public static final String ATTR_CUSTOMER_ID = "gateway.customerId";
    public static final String ATTR_USER_ID = "gateway.userId";
    public static final String ATTR_TIER = "gateway.tier";

    private GatewayHeaders() {
    }
}
