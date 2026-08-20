package org.example.gateway.auth;

/**
 * Everything the gateway learned about a caller from its API key: who they are, who pays, and what
 * they are entitled to.
 */
public record ResolvedCredential(Long apiKeyId,
                                 String keyPrefix,
                                 Long customerId,
                                 String customerName,
                                 String userId,
                                 TierSnapshot tier) {
}
