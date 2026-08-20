package org.example.gateway.repository;

import java.util.List;
import java.util.Optional;
import org.example.gateway.domain.Tier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TierRepository extends JpaRepository<Tier, Long> {

    Optional<Tier> findByCode(String code);

    List<Tier> findAllByOrderByMonthlyPriceAsc();

    boolean existsByCode(String code);
}
