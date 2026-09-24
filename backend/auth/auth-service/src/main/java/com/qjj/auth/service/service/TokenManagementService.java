package com.qjj.auth.service.service;

import com.qjj.auth.framework.core.IdGenerator;
import com.qjj.auth.service.entity.AuthGrant;
import com.qjj.auth.service.entity.OAuthClient;
import com.qjj.auth.service.mapper.AuthGrantMapper;
import com.qjj.auth.service.mapper.OAuthClientMapper;
import com.qjj.auth.service.security.SsoSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

/** Redis 管 Token/会话，MySQL 只维护授权台账。 */
@Service
@RequiredArgsConstructor
public class TokenManagementService {
    private final StringRedisTemplate redisTemplate;
    private final AuthGrantMapper grantMapper;
    private final OAuthClientMapper clientMapper;
    private final SsoSessionService ssoSessionService;
    private final AuditService auditService;
    private final IdGenerator idGenerator;

    public void saveRefreshToken(String userId, String registeredClientId, String grantId,
                                 String refreshToken, Instant expiresAt) {
        String clientId = resolveClientId(registeredClientId);
        String bizGrantId = resolveBizGrantId(grantId);
        String hash = sha256(refreshToken);
        Duration ttl = ttl(expiresAt);
        redisTemplate.opsForValue().set(refreshKey(hash), bizGrantId + ":" + userId + ":" + clientId, ttl);
        redisTemplate.opsForValue().set(refreshAuthKey(hash), grantId, ttl);
        redisTemplate.opsForSet().add(grantRefreshKey(bizGrantId), hash);
        redisTemplate.expire(grantRefreshKey(bizGrantId), ttl);
        redisTemplate.opsForSet().add(userGrantKey(userId), bizGrantId);
        redisTemplate.expire(userGrantKey(userId), Duration.ofDays(7));
        upsertGrant(bizGrantId, userId, clientId);
    }

    /** 用 Refresh Token 反查 SAS authorizationId（findByToken 兜底）。 */
    public String findAuthorizationIdByRefreshToken(String refreshToken) {
        return redisTemplate.opsForValue().get(refreshAuthKey(sha256(refreshToken)));
    }

    /** SAS authorizationId 多为 UUID，映射为 16 位业务 grantId，保证 Redis 与 MySQL 台账同键。 */
    private String resolveBizGrantId(String authorizationOrGrantId) {
        try {
            return String.valueOf(Long.parseLong(authorizationOrGrantId));
        } catch (NumberFormatException ignored) {
        }
        String mapKey = "auth:authorization-grant:" + authorizationOrGrantId;
        String existing = redisTemplate.opsForValue().get(mapKey);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String bizId = String.valueOf(idGenerator.nextId());
        redisTemplate.opsForValue().set(mapKey, bizId, Duration.ofDays(7));
        return bizId;
    }

    public boolean consumeRefreshToken(String refreshToken) {
        String hash = sha256(refreshToken);
        String key = refreshKey(hash);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return isRotatedHash(hash);
        redisTemplate.delete(key);
        redisTemplate.opsForValue().set(rotatedKey(hash), value, Duration.ofDays(7));
        return true;
    }

    public boolean isRotated(String refreshToken) {
        return isRotatedHash(sha256(refreshToken));
    }

    public void revokeGrant(String grantId, String userId) {
        Set<String> hashes = redisTemplate.opsForSet().members(grantRefreshKey(grantId));
        if (hashes != null) hashes.forEach(hash -> {
            redisTemplate.delete(refreshKey(hash));
            redisTemplate.delete(rotatedKey(hash));
        });
        redisTemplate.delete(grantRefreshKey(grantId));
        if (userId != null && !userId.isBlank()) {
            redisTemplate.opsForSet().remove(userGrantKey(userId), grantId);
            ssoSessionService.revoke(Long.valueOf(userId), null);
        }
    }

    public void revokeRotated(String refreshToken) {
        String value = redisTemplate.opsForValue().get(rotatedKey(sha256(refreshToken)));
        if (value == null) return;
        String[] parts = value.split(":", 3);
        if (parts.length == 3) {
            revokeGrant(parts[0], parts[1]);
            auditService.record("REVOKE", "auth_grant", parts[0], "Refresh Token 重用，吊销 grant 全链");
        }
    }

    public void removeAuthorization(String grantId) {
        redisTemplate.delete("auth:authorization:" + grantId);
    }

    private void upsertGrant(String grantId, String userId, String clientId) {
        Long id = Long.valueOf(grantId);
        if (grantMapper.selectById(id) != null) return;
        AuthGrant grant = new AuthGrant();
        grant.setId(id);
        grant.setUserId(Long.valueOf(userId));
        grant.setClientId(clientId);
        grant.setScopes("");
        grant.setStatus(1);
        grant.setRevokeReason("");
        grantMapper.insert(grant);
    }

    private String resolveClientId(String registeredClientId) {
        try {
            OAuthClient client = clientMapper.selectById(Long.valueOf(registeredClientId));
            return client == null ? registeredClientId : client.getClientId();
        } catch (NumberFormatException ex) {
            return registeredClientId;
        }
    }

    private boolean isRotatedHash(String hash) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(rotatedKey(hash)));
    }

    private Duration ttl(Instant expiresAt) {
        Duration ttl = expiresAt == null ? Duration.ofDays(7) : Duration.between(Instant.now(), expiresAt);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofDays(7) : ttl;
    }

    private String refreshKey(String hash) { return "auth:refresh-token:" + hash; }
    private String refreshAuthKey(String hash) { return "auth:refresh-token-auth:" + hash; }
    private String rotatedKey(String hash) { return "auth:refresh-token:rotated:" + hash; }
    private String grantRefreshKey(String grantId) { return "auth:grant-refresh-tokens:" + grantId; }
    private String userGrantKey(String userId) { return "auth:user-grants:" + userId; }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
