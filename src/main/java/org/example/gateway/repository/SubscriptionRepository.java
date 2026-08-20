package org.example.gateway.repository;

import java.util.List;
import java.util.Optional;
import org.example.gateway.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query("select s from Subscription s join fetch s.tier join fetch s.customer where s.customer.id = :customerId")
    Optional<Subscription> findByCustomerId(Long customerId);

    @Query("select s from Subscription s join fetch s.tier join fetch s.customer")
    List<Subscription> findAllWithTier();

    boolean existsByTierId(Long tierId);
}
