package com.example.auth.dto;

/**
 * secret 明文仅创建/重置返回一次。
 */
public record SecretResetVo(OauthClientVo client, String clientSecret) {
}
