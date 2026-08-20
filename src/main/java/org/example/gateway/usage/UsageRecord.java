package org.example.gateway.usage;

import java.time.Instant;

/**
 * Metadata captured for one metered call, handed from the filter to the usage tracker.
 *
 * @param customerId  the billed account
 * @param userId      the individual key holder inside that account
 * @param endpoint    route template, e.g. {@code /api/v1/products/{id}}
 * @param occurredAt  when the call was served
 */
public record UsageRecord(Long customerId,
                          String userId,
                          Long apiKeyId,
                          String endpoint,
                          String httpMethod,
                          int statusCode,
                          Instant occurredAt,
                          long latencyMs,
                          String billingPeriod,
                          String tierCode,
                          boolean billable) {
}
