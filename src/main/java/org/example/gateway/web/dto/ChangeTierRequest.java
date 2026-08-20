package org.example.gateway.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeTierRequest(@NotBlank String tierCode) {
}
