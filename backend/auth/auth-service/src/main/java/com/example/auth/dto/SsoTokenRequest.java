package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SsoTokenRequest(
        @NotBlank String code,
        @NotBlank String clientId
) {
}
