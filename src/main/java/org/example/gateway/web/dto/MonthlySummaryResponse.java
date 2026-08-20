package org.example.gateway.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.example.gateway.domain.MonthlyUsageSummary;

/** The priced monthly statement produced by the aggregation job. */
public record MonthlySummaryResponse(Long customerId,
                                     String billingPeriod,
                                     String tierCode,
                                     long totalRequests,
                                     long includedRequests,
                                     long overageRequests,
                                     BigDecimal baseCharge,
                                     BigDecimal overageCharge,
                                     BigDecimal totalCharge,
                                     String currency,
                                     Instant generatedAt,
                                     List<EndpointUsageResponse> endpoints) {

    public static MonthlySummaryResponse from(MonthlyUsageSummary summary) {
        return new MonthlySummaryResponse(summary.getCustomerId(), summary.getBillingPeriod(),
                summary.getTierCode(), summary.getTotalRequests(), summary.getIncludedRequests(),
                summary.getOverageRequests(), summary.getBaseCharge(), summary.getOverageCharge(),
                summary.getTotalCharge(), summary.getCurrency(), summary.getGeneratedAt(),
                summary.getEndpoints().stream().map(EndpointUsageResponse::from).toList());
    }
}
