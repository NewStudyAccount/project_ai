package com.example.auth.vo;

public record TokenVo(
        String accessToken,
        String refreshToken,
        long accessExpiresIn,
        Long userId,
        String username,
        String realName
) {
}
