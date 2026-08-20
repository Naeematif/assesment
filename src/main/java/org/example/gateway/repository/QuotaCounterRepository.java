package org.example.gateway.repository;

import java.time.Instant;
import java.util.Optional;
import org.example.gateway.domain.QuotaCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotaCounterRepository extends JpaRepository<QuotaCounter, Long> {

    Optional<QuotaCounter> findByCustomerIdAndBillingPeriod(Long customerId, String billingPeriod);

    boolean existsByCustomerIdAndBillingPeriod(Long customerId, String billingPeriod);

    /**
     * Atomically consumes one unit of quota. Returns 1 when the request fits inside the limit and 0
     * when it does not, so the check and the increment can never interleave across instances.
     *
     * <p>The limit is passed in rather than read from the row, which is what makes tier changes take
     * effect immediately: raising the Pro quota unblocks customers on their very next request.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update QuotaCounter q
               set q.used = q.used + 1,
                   q.quotaLimit = :limit,
                   q.updatedAt = :now
             where q.customerId = :customerId
               and q.billingPeriod = :period
               and q.used < :limit
            """)
    int tryConsume(@Param("customerId") Long customerId,
                   @Param("period") String period,
                   @Param("limit") long limit,
                   @Param("now") Instant now);

    /** Unconditional increment, used when the tier permits billable overage beyond the quota. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update QuotaCounter q
               set q.used = q.used + 1,
                   q.quotaLimit = :limit,
                   q.updatedAt = :now
             where q.customerId = :customerId
               and q.billingPeriod = :period
            """)
    int consumeUnbounded(@Param("customerId") Long customerId,
                         @Param("period") String period,
                         @Param("limit") long limit,
                         @Param("now") Instant now);

    /** Gives back a unit when the downstream call failed for a reason that is our fault. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update QuotaCounter q
               set q.used = q.used - 1,
                   q.updatedAt = :now
             where q.customerId = :customerId
               and q.billingPeriod = :period
               and q.used > 0
            """)
    int refund(@Param("customerId") Long customerId,
               @Param("period") String period,
               @Param("now") Instant now);
}
