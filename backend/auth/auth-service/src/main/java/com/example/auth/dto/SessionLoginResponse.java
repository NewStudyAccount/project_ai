package com.example.auth.dto;

public record SessionLoginResponse(
        String sessionToken,
        Long userId,
        String username
) {
}
