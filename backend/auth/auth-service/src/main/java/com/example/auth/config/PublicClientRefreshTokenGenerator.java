package com.example.auth.config;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * 路线 1：公开客户端 authorization_code 也签发 Refresh Token。
 * 安全补偿：强制 PKCE + 轮转 + 重用检测（TokenLedgerService）。
 */
public class PublicClientRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private final StringKeyGenerator refreshTokenGenerator = () ->
            UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!"refresh_token".equals(context.getTokenType().getValue())) {
            return null;
        }
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(context.getRegisteredClient()
                .getTokenSettings().getRefreshTokenTimeToLive());
        return new OAuth2RefreshToken(this.refreshTokenGenerator.generateKey(), issuedAt, expiresAt);
    }
}
