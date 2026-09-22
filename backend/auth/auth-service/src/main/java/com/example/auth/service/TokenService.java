package com.example.auth.service;

import com.example.auth.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Access JWT + Refresh（Redis 可吊销/轮转）。
 */
@Service
public class TokenService {

    private static final String REFRESH_KEY = "auth:refresh:";
    private static final String BL_ACCESS_KEY = "auth:bl:access:";

    private final AuthProperties properties;
    private final StringRedisTemplate redis;

    public TokenService(AuthProperties properties, StringRedisTemplate redis) {
        this.properties = properties;
        this.redis = redis;
    }

    public record TokenPair(String accessToken, String refreshToken, long accessExpiresIn) {
    }

    public TokenPair issue(Long userId, String username, String sid) {
        Instant now = Instant.now();
        Instant accessExp = now.plus(Duration.ofMinutes(properties.getAccessTtlMinutes()));
        String jti = UUID.randomUUID().toString().replace("-", "");
        String access = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .claim("uid", String.valueOf(userId))
                .claim("username", username)
                .claim("sid", sid)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(accessExp))
                .signWith(secretKey())
                .compact();

        String refreshJti = UUID.randomUUID().toString().replace("-", "");
        Duration refreshTtl = Duration.ofDays(properties.getRefreshTtlDays());
        redis.opsForValue().set(REFRESH_KEY + refreshJti, userId + "|" + sid, refreshTtl);
        return new TokenPair(access, refreshJti, Duration.between(now, accessExp).toSeconds());
    }

    /** Refresh 轮转：校验并作废旧 refresh，签发新对。 */
    public TokenPair refresh(String refreshToken, String username) {
        String val = redis.opsForValue().get(REFRESH_KEY + refreshToken);
        if (val == null) {
            throw com.example.auth.common.BizException.auth("Refresh 无效或已过期");
        }
        redis.delete(REFRESH_KEY + refreshToken);
        long userId = Long.parseLong(val.split("\\|")[0]);
        String sid = val.contains("|") ? val.split("\\|", 2)[1] : "";
        return issue(userId, username, sid);
    }

    public void revokeRefresh(String refreshToken) {
        redis.delete(REFRESH_KEY + refreshToken);
    }

    public void revokeAllRefreshBySidPrefix(Long userId) {
        // 简化：登出/改密时按用户维度扫描可后置；当前按传入 jti 删除 + 黑名单策略
        // 改密调用方应收集用户 refresh 列表；此处提供按 sid 删会话后的短 Access 策略
    }

    public void revokeRefreshByUser(Long userId, String refreshToken) {
        if (refreshToken != null) {
            String val = redis.opsForValue().get(REFRESH_KEY + refreshToken);
            if (val != null && val.startsWith(userId + "|")) {
                redis.delete(REFRESH_KEY + refreshToken);
            }
        }
    }

    public void blacklistAccess(String accessToken) {
        try {
            Claims claims = parse(accessToken);
            Date exp = claims.getExpiration();
            long ttl = exp == null ? 60 : Math.max(1, (exp.getTime() - System.currentTimeMillis()) / 1000);
            if (claims.getId() != null) {
                redis.opsForValue().set(BL_ACCESS_KEY + claims.getId(), "1", Duration.ofSeconds(ttl));
            }
        } catch (Exception ignored) {
            // 非法 token 直接忽略黑名单
        }
    }

    public boolean isAccessBlacklisted(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(BL_ACCESS_KEY + jti));
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey secretKey() {
        byte[] bytes = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }
}
