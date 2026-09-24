package com.qjj.auth.service.repository;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公开客户端可签发 Refresh Token；同一次签发/刷新流程内若被多次 generate，
 * 必须返回同一个值，否则响应里的 RT 与授权对象里保存的不一致，刷新会 invalid_grant。
 */
public class PublicClientRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private static final Map<String, OAuth2RefreshToken> IN_FLIGHT = new ConcurrentHashMap<>();

    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())
                && !AuthorizationGrantType.REFRESH_TOKEN.equals(context.getAuthorizationGrantType())) {
            return null;
        }
        String key = cacheKey(context);
        OAuth2RefreshToken existing = IN_FLIGHT.get(key);
        if (existing != null && existing.getExpiresAt() != null && existing.getExpiresAt().isAfter(Instant.now())) {
            return existing;
        }
        RegisteredClient client = context.getRegisteredClient();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(client.getTokenSettings().getRefreshTokenTimeToLive());
        OAuth2RefreshToken token = new OAuth2RefreshToken(UUID.randomUUID().toString(), issuedAt, expiresAt);
        IN_FLIGHT.put(key, token);
        return token;
    }

    /** 授权对象 save 完成后清理，避免刷新流程复用旧 RT。 */
    public static void clearInFlight(OAuth2TokenContext context) {
        IN_FLIGHT.remove(cacheKey(context));
    }

    public static void clearInFlightById(String authorizationOrClientId) {
        IN_FLIGHT.keySet().removeIf(k -> k.startsWith(authorizationOrClientId + ":"));
    }

    private static String cacheKey(OAuth2TokenContext context) {
        String authId = context.getAuthorization() != null ? context.getAuthorization().getId() : "none";
        return authId + ":" + context.getAuthorizationGrantType().getValue();
    }
}
