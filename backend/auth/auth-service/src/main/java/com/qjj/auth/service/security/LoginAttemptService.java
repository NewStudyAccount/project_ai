package com.qjj.auth.service.security;

import com.qjj.auth.framework.core.IdGenerator;
import com.qjj.auth.service.entity.LoginAttempt;
import com.qjj.auth.service.mapper.LoginAttemptMapper;
import com.qjj.auth.service.config.AuthSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {
    private final StringRedisTemplate redisTemplate;
    private final LoginAttemptMapper loginAttemptMapper;
    private final IdGenerator idGenerator;
    private final AuthSecurityProperties properties;

    public void ensureNotLocked(String username, String ip) {
        String userCount = redisTemplate.opsForValue().get(userKey(username));
        String ipCount = redisTemplate.opsForValue().get(ipKey(ip));
        if (Integer.parseInt(userCount == null ? "0" : userCount) >= properties.getLoginMaxFailures()
                || Integer.parseInt(ipCount == null ? "0" : ipCount) >= properties.getLoginMaxFailures()) {
            throw new IllegalStateException("登录失败");
        }
        Long ipRate = redisTemplate.opsForValue().increment(rateKey(ip));
        if (ipRate != null && ipRate == 1L) {
            redisTemplate.expire(rateKey(ip), Duration.ofSeconds(60));
        }
        if (ipRate != null && ipRate > 20L) {
            throw new IllegalStateException("登录失败");
        }
    }

    public void recordSuccess(AuthPrincipal principal, String username, String ip, String userAgent, String clientId) {
        redisTemplate.delete(userKey(username));
        redisTemplate.delete(ipKey(ip));
        insert(principal, username, 1, "", ip, userAgent, clientId);
    }

    public void recordFailure(String username, Long userId, String reason, String ip, String userAgent, String clientId) {
        increment(userKey(username));
        increment(ipKey(ip));
        insert(new AuthPrincipal(userId, username), username, 0, reason, ip, userAgent, clientId);
    }

    private void increment(String key) {
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofSeconds(properties.getLoginLockSeconds()));
    }

    private void insert(AuthPrincipal principal, String username, int success, String reason, String ip, String userAgent, String clientId) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setId(idGenerator.nextId());
        attempt.setUsername(username == null ? "" : username);
        attempt.setUserId(principal == null ? null : principal.userId());
        attempt.setSuccess(success);
        attempt.setFailReason(reason == null ? "" : reason.substring(0, Math.min(reason.length(), 255)));
        attempt.setIp(ip == null ? "" : ip);
        attempt.setUserAgent(userAgent == null ? "" : userAgent.substring(0, Math.min(userAgent.length(), 255)));
        attempt.setClientId(clientId == null ? "" : clientId);
        loginAttemptMapper.insert(attempt);
    }

    private String userKey(String username) {
        return "auth:login-attempt:user:" + (username == null ? "" : username);
    }

    private String ipKey(String ip) {
        return "auth:login-attempt:ip:" + (ip == null ? "" : ip);
    }

    private String rateKey(String ip) {
        return "auth:login-rate:ip:" + (ip == null ? "" : ip);
    }
}
