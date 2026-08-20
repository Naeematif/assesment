package org.example.gateway.web.dto;

/** Live view of a customer's position against its monthly allowance. */
public record QuotaStatusResponse(Long customerId,
                                  String billingPeriod,
                                  String tierCode,
                                  long monthlyQuota,
                                  long used,
                                  long remaining,
                                  int rateLimitPerSecond) {
}
