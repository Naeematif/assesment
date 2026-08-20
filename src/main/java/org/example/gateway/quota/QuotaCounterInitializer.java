package org.example.gateway.quota;

import org.example.gateway.domain.QuotaCounter;
import org.example.gateway.repository.QuotaCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the per-customer, per-month counter row on first use.
 *
 * <p>This is a separate bean purely so the insert gets its own transaction. When several first
 * requests race, all but one hit the unique constraint; letting that exception escape a dedicated
 * {@code REQUIRES_NEW} boundary means the doomed transaction and its Hibernate session are discarded
 * cleanly, and the caller's transaction is untouched. Swallowing the exception inside the same
 * transaction instead would leave a poisoned session that fails on the next flush.
 */
@Service
public class QuotaCounterInitializer {

    private final QuotaCounterRepository repository;

    public QuotaCounterInitializer(QuotaCounterRepository repository) {
        this.repository = repository;
    }

    /** @throws org.springframework.dao.DataIntegrityViolationException if another thread won the race */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(Long customerId, String period, long limit) {
        repository.saveAndFlush(new QuotaCounter(customerId, period, limit));
    }
}
