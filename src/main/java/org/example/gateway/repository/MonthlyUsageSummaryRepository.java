package org.example.gateway.repository;

import java.util.List;
import java.util.Optional;
import org.example.gateway.domain.MonthlyUsageSummary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyUsageSummaryRepository extends JpaRepository<MonthlyUsageSummary, Long> {

    @EntityGraph(attributePaths = "endpoints")
    Optional<MonthlyUsageSummary> findByCustomerIdAndBillingPeriod(Long customerId, String billingPeriod);

    @EntityGraph(attributePaths = "endpoints")
    List<MonthlyUsageSummary> findByBillingPeriodOrderByTotalChargeDesc(String billingPeriod);
}
