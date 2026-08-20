package org.example.gateway.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateApiKeyRequest(@NotBlank String userId, String label) {
}
