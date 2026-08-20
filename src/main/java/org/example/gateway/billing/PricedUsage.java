package org.example.gateway.billing;

import java.math.BigDecimal;

/**
 * Result of pricing one customer's month.
 *
 * @param includedRequests requests covered by the subscription fee
 * @param overageRequests  requests beyond the quota, charged per call
 */
public record PricedUsage(long totalRequests,
                          long includedRequests,
                          long overageRequests,
                          BigDecimal baseCharge,
                          BigDecimal overageCharge,
                          BigDecimal totalCharge) {
}
