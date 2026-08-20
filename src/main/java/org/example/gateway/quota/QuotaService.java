package org.example.gateway.quota;

import java.time.Clock;
import java.time.Instant;
import org.example.gateway.auth.TierSnapshot;
import org.example.gateway.domain.BillingPeriod;
import org.example.gateway.domain.QuotaCounter;
import org.example.gateway.repository.QuotaCounterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the tier's monthly request allowance.
 *
 * <p>The counter lives in the database rather than in memory for two reasons: a monthly allowance
 * has to survive a restart, and every gateway instance has to see the same number. Correctness under
 * concurrency comes from a conditional UPDATE - {@code set used = used + 1 where used < :limit} -
 * which the database evaluates atomically. A read-then-write in application code would let two
 * simultaneous requests both observe {@code used == limit - 1} and both be admitted.
 *
 * <p>Each consume runs in its own transaction ({@code REQUIRES_NEW}) so the increment commits
 * independently of whatever the proxied request goes on to do.
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final QuotaCounterRepository repository;
    private final QuotaCounterInitializer counterInitializer;
    private final Clock clock;

    public QuotaService(QuotaCounterRepository repository, QuotaCounterInitializer counterInitializer,
                        Clock clock) {
        this.repository = repository;
        this.counterInitializer = counterInitializer;
        this.clock = clock;
    }

    /** Current billing period in the gateway's billing timezone. */
    public String currentPeriod() {
        return BillingPeriod.of(clock.instant(), clock.getZone());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QuotaDecision tryConsume(Long customerId, TierSnapshot tier) {
        return tryConsume(customerId, tier, currentPeriod());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QuotaDecision tryConsume(Long customerId, TierSnapshot tier, String period) {
        long limit = tier.monthlyQuota();
        Instant now = clock.instant();

        // Optimistic path: one statement, no preceding SELECT. The row is missing only on a
        // customer's first call of the month, so paying for an existence check on every request
        // would be paying for the rare case.
        int consumed = repository.tryConsume(customerId, period, limit, now);
        if (consumed == 0 && !repository.existsByCustomerIdAndBillingPeriod(customerId, period)) {
            createCounter(customerId, period, limit);
            consumed = repository.tryConsume(customerId, period, limit, now);
        }

        if (consumed == 1) {
            return QuotaDecision.allowed(limit, usedIn(customerId, period), false);
        }

        if (!tier.overageAllowed()) {
            return QuotaDecision.exhausted(limit);
        }

        // The tier sells beyond its included allowance: keep serving and let the billing job charge
        // the excess at the overage rate.
        repository.consumeUnbounded(customerId, period, limit, now);
        return QuotaDecision.allowed(limit, usedIn(customerId, period), true);
    }

    /**
     * Returns a consumed unit. Used when the downstream service failed with a 5xx: the customer
     * should not burn monthly allowance on our outage.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refund(Long customerId, String period) {
        repository.refund(customerId, period, clock.instant());
    }

    @Transactional(readOnly = true)
    public QuotaCounter snapshot(Long customerId, String period) {
        return repository.findByCustomerIdAndBillingPeriod(customerId, period).orElse(null);
    }

    private long usedIn(Long customerId, String period) {
        return repository.findByCustomerIdAndBillingPeriod(customerId, period)
                .map(QuotaCounter::getUsed)
                .orElse(0L);
    }

    /**
     * Two concurrent first-requests can both try to insert. The unique constraint makes one of them
     * fail, and losing that race is harmless: the row we wanted now exists either way.
     */
    private void createCounter(Long customerId, String period, long limit) {
        try {
            counterInitializer.create(customerId, period, limit);
        } catch (DataIntegrityViolationException e) {
            log.debug("Quota counter for customer {} period {} was created concurrently", customerId, period);
        }
    }
}
