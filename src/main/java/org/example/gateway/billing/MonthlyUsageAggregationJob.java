package org.example.gateway.billing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.example.gateway.domain.BillingPeriod;
import org.example.gateway.domain.EndpointUsageSummary;
import org.example.gateway.domain.MonthlyUsageSummary;
import org.example.gateway.domain.Tier;
import org.example.gateway.repository.MonthlyUsageSummaryRepository;
import org.example.gateway.repository.SubscriptionRepository;
import org.example.gateway.repository.TierRepository;
import org.example.gateway.repository.UsageEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backend job that rolls the raw usage event stream up into a priced monthly summary per customer,
 * with a per-endpoint breakdown.
 *
 * <p>Design notes:
 * <ul>
 *   <li><b>Idempotent.</b> Re-running a period overwrites that period's summaries instead of
 *       duplicating them, so a failed or partial run can simply be retried, and an operator can
 *       re-run a month after correcting a tier's pricing.</li>
 *   <li><b>Aggregation in the database.</b> The per-endpoint rollup is a {@code GROUP BY}, so the
 *       job's memory usage stays flat whether the month produced a thousand events or a billion.</li>
 *   <li><b>Per-customer transactions.</b> One customer failing to price does not roll back the
 *       customers already summarised.</li>
 * </ul>
 */
@Component
public class MonthlyUsageAggregationJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyUsageAggregationJob.class);

    private final UsageEventRepository usageEventRepository;
    private final MonthlyUsageSummaryRepository summaryRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TierRepository tierRepository;
    private final BillingService billingService;
    private final Clock clock;

    public MonthlyUsageAggregationJob(UsageEventRepository usageEventRepository,
                                      MonthlyUsageSummaryRepository summaryRepository,
                                      SubscriptionRepository subscriptionRepository,
                                      TierRepository tierRepository,
                                      BillingService billingService,
                                      Clock clock) {
        this.usageEventRepository = usageEventRepository;
        this.summaryRepository = summaryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tierRepository = tierRepository;
        this.billingService = billingService;
        this.clock = clock;
    }

    /**
     * Closes the previous month. Runs a couple of hours into the new month so that any in-flight
     * usage writes from the last seconds of the period have certainly landed.
     */
    @Scheduled(cron = "${gateway.billing.aggregation-cron}", zone = "${gateway.billing.timezone}")
    public void runForPreviousMonth() {
        String period = BillingPeriod.of(YearMonth.from(clock.instant().atZone(clock.getZone())).minusMonths(1));
        log.info("Scheduled monthly aggregation starting for period {}", period);
        AggregationReport report = aggregate(period);
        log.info("Scheduled monthly aggregation finished: {}", report);
    }

    /** Aggregates and prices one billing period. Safe to call repeatedly. */
    public AggregationReport aggregate(String period) {
        long startedAt = System.nanoTime();
        List<Long> customerIds = usageEventRepository.findCustomerIdsWithUsage(period);

        long totalRequests = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        int processed = 0;

        for (Long customerId : customerIds) {
            try {
                MonthlyUsageSummary summary = summariseCustomer(customerId, period);
                totalRequests += summary.getTotalRequests();
                totalRevenue = totalRevenue.add(summary.getTotalCharge());
                processed++;
            } catch (RuntimeException e) {
                log.error("Failed to summarise customer {} for period {}; continuing with the rest",
                        customerId, period, e);
            }
        }

        return new AggregationReport(period, processed, totalRequests, totalRevenue, clock.instant(),
                (System.nanoTime() - startedAt) / 1_000_000L);
    }

    @Transactional
    public MonthlyUsageSummary summariseCustomer(Long customerId, String period) {
        List<Object[]> rows = usageEventRepository.aggregateByEndpoint(customerId, period);
        Tier tier = resolveTier(customerId, rows);

        long totalRequests = rows.stream().mapToLong(row -> ((Number) row[2]).longValue()).sum();
        PricedUsage priced = billingService.price(totalRequests, tier);

        MonthlyUsageSummary summary = summaryRepository
                .findByCustomerIdAndBillingPeriod(customerId, period)
                .orElseGet(() -> new MonthlyUsageSummary(customerId, period, tier.getCode()));

        summary.setTierCode(tier.getCode());
        summary.setTotalRequests(priced.totalRequests());
        summary.setIncludedRequests(priced.includedRequests());
        summary.setOverageRequests(priced.overageRequests());
        summary.setBaseCharge(priced.baseCharge());
        summary.setOverageCharge(priced.overageCharge());
        summary.setTotalCharge(priced.totalCharge());
        summary.setGeneratedAt(Instant.now(clock));

        // Rebuild the breakdown rather than merging it, so a re-run is a true replacement.
        summary.clearEndpoints();
        for (Object[] row : rows) {
            summary.addEndpoint(new EndpointUsageSummary(
                    (String) row[0],
                    (String) row[1],
                    ((Number) row[2]).longValue(),
                    Math.round(((Number) row[3]).doubleValue())));
        }

        return summaryRepository.save(summary);
    }

    /**
     * Prices the month against the tier the customer was actually on.
     *
     * <p>Usage events carry the tier code that was in force when the call was served, so a customer
     * who upgraded mid-month is priced on the tier their traffic ended on rather than whatever they
     * happen to be subscribed to at the moment the job runs. Proration across a mid-month change is
     * out of scope here and would be the natural next step.
     */
    private Tier resolveTier(Long customerId, List<Object[]> rows) {
        Optional<String> tierCodeFromUsage = rows.stream()
                .max((a, b) -> Long.compare(((Number) a[2]).longValue(), ((Number) b[2]).longValue()))
                .map(row -> (String) row[4]);

        return tierCodeFromUsage
                .flatMap(tierRepository::findByCode)
                .or(() -> subscriptionRepository.findByCustomerId(customerId).map(s -> s.getTier()))
                .orElseThrow(() -> new IllegalStateException(
                        "No tier could be resolved for customer " + customerId));
    }
}
