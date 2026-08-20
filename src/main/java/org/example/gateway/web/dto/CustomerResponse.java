package org.example.gateway.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import org.example.gateway.domain.Customer;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerResponse(Long id,
                               String name,
                               String email,
                               String status,
                               String tierCode,
                               String subscriptionStatus,
                               Instant createdAt,
                               ApiKeyResponse apiKey) {

    public static CustomerResponse from(Customer customer, String tierCode, String subscriptionStatus,
                                        ApiKeyResponse apiKey) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getEmail(),
                customer.getStatus().name(), tierCode, subscriptionStatus, customer.getCreatedAt(), apiKey);
    }
}
