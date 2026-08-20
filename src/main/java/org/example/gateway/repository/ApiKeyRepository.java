package org.example.gateway.repository;

import java.util.List;
import java.util.Optional;
import org.example.gateway.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /**
     * Single query that resolves a credential all the way to its tier, so the hot path costs one
     * round trip on a cache miss.
     */
    @Query("select k from ApiKey k join fetch k.customer where k.keyHash = :keyHash")
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByCustomerId(Long customerId);
}
