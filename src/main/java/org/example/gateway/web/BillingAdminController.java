package org.example.gateway.web;

import java.util.List;
import org.example.gateway.billing.AggregationReport;
import org.example.gateway.billing.MonthlyUsageAggregationJob;
import org.example.gateway.error.ErrorCode;
import org.example.gateway.error.GatewayException;
import org.example.gateway.repository.MonthlyUsageSummaryRepository;
import org.example.gateway.web.dto.MonthlySummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control plane for the billing rollup.
 *
 * <p>The aggregation job runs on a schedule, but exposing a manual trigger matters in practice: it
 * is how you re-run a month after fixing a pricing mistake, and how the integration tests drive the
 * job deterministically instead of waiting for the first of the month.
 */
@RestController
@RequestMapping("/admin/billing")
public class BillingAdminController {

    private final MonthlyUsageAggregationJob aggregationJob;
    private final MonthlyUsageSummaryRepository summaryRepository;

    public BillingAdminController(MonthlyUsageAggregationJob aggregationJob,
                                  MonthlyUsageSummaryRepository summaryRepository) {
        this.aggregationJob = aggregationJob;
        this.summaryRepository = summaryRepository;
    }

    /** Idempotent: re-running a period replaces that period's summaries. */
    @PostMapping("/aggregate")
    public AggregationReport aggregate(@RequestParam String period) {
        validatePeriod(period);
        return aggregationJob.aggregate(period);
    }

    @GetMapping("/summaries")
    public List<MonthlySummaryResponse> summaries(@RequestParam String period) {
        validatePeriod(period);
        return summaryRepository.findByBillingPeriodOrderByTotalChargeDesc(period).stream()
                .map(MonthlySummaryResponse::from)
                .toList();
    }

    @GetMapping("/summaries/{customerId}")
    public MonthlySummaryResponse summary(@PathVariable Long customerId, @RequestParam String period) {
        validatePeriod(period);
        return summaryRepository.findByCustomerIdAndBillingPeriod(customerId, period)
                .map(MonthlySummaryResponse::from)
                .orElseThrow(() -> new GatewayException(ErrorCode.NOT_FOUND,
                        "No summary for customer %d in %s".formatted(customerId, period)));
    }

    private void validatePeriod(String period) {
        try {
            org.example.gateway.domain.BillingPeriod.parse(period);
        } catch (RuntimeException e) {
            throw new GatewayException(ErrorCode.VALIDATION_FAILED,
                    "period must be formatted as yyyy-MM, got '" + period + "'");
        }
    }
}
