package com.example.auth.service;

import com.example.auth.config.AuthProperties;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * SSO 会话 sid（Redis）+ 一次性 code（绑 client_id）。
 */
@Service
public class SsoSessionService {

    private static final String SID_KEY = "sso:sid:";
    private static final String CODE_KEY = "sso:code:";

    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    public SsoSessionService(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public record SidInfo(long userId, String username) {
    }

    public String createSid(long userId, String username) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(SID_KEY + sid, userId + "|" + username,
                Duration.ofDays(properties.getSidTtlDays()));
        return sid;
    }

    public SidInfo resolveSid(String sid) {
        if (sid == null || sid.isBlank()) {
            return null;
        }
        String val = redis.opsForValue().get(SID_KEY + sid);
        if (val == null) {
            return null;
        }
        String[] parts = val.split("\\|", 2);
        return new SidInfo(Long.parseLong(parts[0]), parts.length > 1 ? parts[1] : "");
    }

    public void destroySid(String sid) {
        if (sid != null && !sid.isBlank()) {
            redis.delete(SID_KEY + sid);
        }
    }

    /** 一次性授权码，绑定 client_id，60 秒有效。 */
    public String createCode(long userId, String username, String clientId) {
        String code = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(CODE_KEY + code, userId + "|" + username + "|" + clientId,
                Duration.ofSeconds(60));
        return code;
    }

    public SidInfo consumeCode(String code, String clientId) {
        String key = CODE_KEY + code;
        String val = redis.opsForValue().get(key);
        if (val == null) {
            return null;
        }
        redis.delete(key);
        String[] parts = val.split("\\|", 3);
        if (parts.length < 3 || !parts[2].equals(clientId)) {
            return null;
        }
        return new SidInfo(Long.parseLong(parts[0]), parts[1]);
    }

    public boolean isReturnUrlAllowed(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return false;
        }
        String whitelist = properties.getReturnUrlWhitelist();
        if (whitelist == null || whitelist.isBlank()) {
            return false;
        }
        for (String prefix : whitelist.split(",")) {
            String p = prefix.trim();
            if (!p.isEmpty() && returnUrl.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
