package com.qjj.auth.service.security;

import com.qjj.auth.service.config.AuthSecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SsoSessionService {
    public static final String COOKIE_NAME = "AUTH_SSO_SESSION";
    private final StringRedisTemplate redisTemplate;
    private final AuthSecurityProperties properties;

    public void create(AuthPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        Duration ttl = Duration.ofSeconds(properties.getSsoSessionTtlSeconds());
        redisTemplate.opsForValue().set(sessionKey(sessionId), principal.userId() + ":" + principal.username(), ttl);
        redisTemplate.opsForSet().add(userSessionsKey(principal.userId()), sessionId);
        redisTemplate.expire(userSessionsKey(principal.userId()), ttl);
        Cookie cookie = new Cookie(COOKIE_NAME, sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath("/");
        cookie.setMaxAge((int) properties.getSsoSessionTtlSeconds());
        response.addCookie(cookie);
    }

    public AuthPrincipal resolve(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        String value = redisTemplate.opsForValue().get(sessionKey(sessionId));
        if (value == null || !value.contains(":")) return null;
        String[] parts = value.split(":", 2);
        return new AuthPrincipal(Long.valueOf(parts[0]), parts[1]);
    }

    public void revoke(Long userId, String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) redisTemplate.delete(sessionKey(sessionId));
        if (userId != null) redisTemplate.delete(userSessionsKey(userId));
    }

    private String sessionKey(String sessionId) {
        return "auth:sso-session:" + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return "auth:user-sessions:" + userId;
    }
}
