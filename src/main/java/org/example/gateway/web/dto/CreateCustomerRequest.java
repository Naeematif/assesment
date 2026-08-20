package org.example.gateway.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(@NotBlank String name,
                                    @NotBlank @Email String email,
                                    @NotBlank String tierCode,
                                    String userId) {

    public String effectiveUserId() {
        return userId == null || userId.isBlank() ? "default-user" : userId;
    }
}
