package com.example.auth.user.dto;

/**
 * OIDC profile 类资料（userinfo 用）。
 */
public record UserProfileDto(
        Long id,
        String username,
        String realName,
        String nickname,
        String email,
        String phone,
        String avatar
) {
}
