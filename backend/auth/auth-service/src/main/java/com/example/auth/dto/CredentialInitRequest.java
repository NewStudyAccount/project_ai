package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CredentialInitRequest(
        @NotNull Long userId,
        @NotBlank String rawPassword
) {
}
