package org.example.gateway.ratelimit;

/** What the per-second limit is applied to. */
public enum RateLimitScope {

    /**
     * One shared bucket per paying customer (default). The tier sells the account "N requests per
     * second", so minting extra API keys must not multiply the entitlement.
     */
    CUSTOMER,

    /** One bucket per API key, giving each user or service its own independent allowance. */
    API_KEY
}
