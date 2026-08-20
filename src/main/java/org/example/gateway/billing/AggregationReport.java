package org.example.gateway.billing;

import java.math.BigDecimal;
import java.time.Instant;

/** Summary of one aggregation run, returned by the admin trigger and written to the logs. */
public record AggregationReport(String billingPeriod,
                                int customersProcessed,
                                long totalRequests,
                                BigDecimal totalRevenue,
                                Instant completedAt,
                                long durationMs) {
}
