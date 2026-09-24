package com.qjj.auth.service.repository;

import com.qjj.auth.service.service.AuditService;
import com.qjj.auth.service.service.TokenManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Redis 版 OAuth2AuthorizationService：授权对象序列化入 Redis，
 * 并按 token 建索引；Refresh Token 台账/轮转仍走 TokenManagementService。
 */
@RequiredArgsConstructor
public class RedisOAuth2AuthorizationService implements OAuth2AuthorizationService {
    private final StringRedisTemplate redisTemplate;
    private final TokenManagementService tokenManagementService;
    private final AuditService auditService;

    @Override
    public void save(OAuth2Authorization authorization) {
        byte[] payload = serialize(authorization);
        redisTemplate.opsForValue().set(authKey(authorization.getId()),
                Base64.getEncoder().encodeToString(payload), Duration.ofHours(8));
        indexToken(authorization.getId(), authorization.getToken(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode.class));
        indexToken(authorization.getId(), authorization.getAccessToken());
        indexToken(authorization.getId(), authorization.getRefreshToken());
        indexToken(authorization.getId(), authorization.getToken(org.springframework.security.oauth2.core.oidc.OidcIdToken.class));

        OAuth2Authorization.Token<OAuth2RefreshToken> refreshTokenToken = authorization.getRefreshToken();
        OAuth2RefreshToken refreshToken = refreshTokenToken == null ? null : refreshTokenToken.getToken();
        debug("save authId=" + authorization.getId()
                + " principal=" + authorization.getPrincipalName()
                + " rt=" + (refreshToken == null ? "null" : refreshToken.getTokenValue())
                + " at=" + (authorization.getAccessToken() == null ? "null" : authorization.getAccessToken().getToken().getTokenValue().substring(0, Math.min(12, authorization.getAccessToken().getToken().getTokenValue().length()))));
        if (refreshToken != null) {
            tokenManagementService.saveRefreshToken(
                    authorization.getPrincipalName(),
                    authorization.getRegisteredClientId(),
                    authorization.getId(),
                    refreshToken.getTokenValue(),
                    refreshToken.getExpiresAt());
            auditService.record("TOKEN_ISSUE", "auth_grant", authorization.getId(),
                    "签发令牌 user=" + authorization.getPrincipalName() + " client=" + authorization.getRegisteredClientId());
        }
        PublicClientRefreshTokenGenerator.clearInFlightById(authorization.getId());
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        redisTemplate.delete(authKey(authorization.getId()));
        deleteTokenIndex(authorization.getAccessToken());
        deleteTokenIndex(authorization.getRefreshToken());
        tokenManagementService.removeAuthorization(authorization.getId());
    }

    @Override
    public OAuth2Authorization findById(String id) {
        String raw = redisTemplate.opsForValue().get(authKey(id));
        return raw == null ? null : deserialize(Base64.getDecoder().decode(raw));
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        String authId = redisTemplate.opsForValue().get(tokenIndexKey(token, tokenType));
        debug("findByToken type=" + (tokenType == null ? "null" : tokenType.getValue())
                + " token=" + token
                + " index=" + authId);
        if (authId == null) {
            authId = redisTemplate.opsForValue().get(tokenIndexKey(token, null));
        }
        if (authId == null && OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            authId = tokenManagementService.findAuthorizationIdByRefreshToken(token);
            debug("findByToken rt-fallback authId=" + authId);
        }
        if (authId != null) {
            OAuth2Authorization authorization = findById(authId);
            debug("findByToken loaded=" + (authorization == null ? "null" : authorization.getId())
                    + " rt=" + (authorization == null || authorization.getRefreshToken() == null ? "null"
                    : authorization.getRefreshToken().getToken().getTokenValue()));
            if (authorization != null) {
                return authorization;
            }
        }
        if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType) && tokenManagementService.isRotated(token)) {
            tokenManagementService.revokeRotated(token);
            return null;
        }
        return null;
    }

    private static void debug(String msg) {
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "auth-find.log"),
                    java.time.Instant.now() + " " + msg + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private void indexToken(String authorizationId, OAuth2Authorization.Token<? extends OAuth2Token> token) {
        if (token == null || token.getToken() == null) {
            return;
        }
        String value = token.getToken().getTokenValue();
        OAuth2TokenType type = resolveType(token.getToken());
        redisTemplate.opsForValue().set(tokenIndexKey(value, type), authorizationId, Duration.ofHours(8));
        redisTemplate.opsForValue().set(tokenIndexKey(value, null), authorizationId, Duration.ofHours(8));
    }

    private void deleteTokenIndex(OAuth2Authorization.Token<? extends OAuth2Token> token) {
        if (token == null || token.getToken() == null) {
            return;
        }
        String value = token.getToken().getTokenValue();
        redisTemplate.delete(tokenIndexKey(value, resolveType(token.getToken())));
        redisTemplate.delete(tokenIndexKey(value, null));
    }

    private OAuth2TokenType resolveType(OAuth2Token token) {
        if (token instanceof OAuth2RefreshToken) {
            return OAuth2TokenType.REFRESH_TOKEN;
        }
        if (token instanceof OAuth2AccessToken) {
            return OAuth2TokenType.ACCESS_TOKEN;
        }
        if (token instanceof org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode) {
            return new OAuth2TokenType("code");
        }
        return new OAuth2TokenType(token.getClass().getSimpleName());
    }

    private String authKey(String id) {
        return "auth:authorization:" + id;
    }

    private String tokenIndexKey(String token, OAuth2TokenType type) {
        String t = type == null ? "any" : type.getValue();
        return "auth:token-index:" + t + ":" + token;
    }

    private byte[] serialize(OAuth2Authorization authorization) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(authorization);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("序列化 OAuth2Authorization 失败", ex);
        }
    }

    private OAuth2Authorization deserialize(byte[] payload) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(payload))) {
            return (OAuth2Authorization) ois.readObject();
        } catch (Exception ex) {
            throw new IllegalStateException("反序列化 OAuth2Authorization 失败", ex);
        }
    }

    @SuppressWarnings("unused")
    private static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
