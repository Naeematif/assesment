package org.example.gateway.repository;

import java.util.List;
import org.example.gateway.domain.UsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {

    List<UsageEvent> findByCustomerIdAndBillingPeriod(Long customerId, String billingPeriod);

    long countByCustomerIdAndBillingPeriod(Long customerId, String billingPeriod);

    /** Customers that produced any billable traffic in the period - the aggregation job's work list. */
    @Query("select distinct e.customerId from UsageEvent e where e.billingPeriod = :period and e.billable = true")
    List<Long> findCustomerIdsWithUsage(@Param("period") String period);

    /**
     * Per-endpoint rollup for one customer and period. Aggregating in the database keeps the job's
     * memory footprint flat regardless of how many events the month produced.
     */
    @Query("""
            select e.endpoint, e.httpMethod, count(e), coalesce(avg(e.latencyMs), 0), max(e.tierCode)
              from UsageEvent e
             where e.customerId = :customerId
               and e.billingPeriod = :period
               and e.billable = true
             group by e.endpoint, e.httpMethod
             order by count(e) desc
            """)
    List<Object[]> aggregateByEndpoint(@Param("customerId") Long customerId, @Param("period") String period);
}
