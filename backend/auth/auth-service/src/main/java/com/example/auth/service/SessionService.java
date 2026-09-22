package com.example.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.auth.common.PageQuery;
import com.example.auth.common.PageResult;
import com.example.auth.entity.AuthAuditLog;
import com.example.auth.entity.AuthSession;
import com.example.auth.entity.LoginAttempt;
import com.example.auth.framework.IdService;
import com.example.auth.mapper.AuthAuditLogMapper;
import com.example.auth.mapper.AuthSessionMapper;
import com.example.auth.mapper.LoginAttemptMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SSO 会话生命周期 + 审计查询。
 */
@Service
public class SessionService {

    private final AuthSessionMapper sessionMapper;
    private final LoginAttemptMapper loginAttemptMapper;
    private final AuthAuditLogMapper auditLogMapper;
    private final IdService idService;

    public SessionService(AuthSessionMapper sessionMapper, LoginAttemptMapper loginAttemptMapper,
                          AuthAuditLogMapper auditLogMapper, IdService idService) {
        this.sessionMapper = sessionMapper;
        this.loginAttemptMapper = loginAttemptMapper;
        this.auditLogMapper = auditLogMapper;
        this.idService = idService;
    }

    @Transactional
    public String createSession(Long userId, String ip, String userAgent, int ttlHours) {
        String raw = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        AuthSession row = new AuthSession();
        row.setId(idService.nextId("auth_session"));
        row.setSessionTokenHash(hash(raw));
        row.setUserId(userId);
        row.setAuthTime(LocalDateTime.now());
        row.setExpiresAt(LocalDateTime.now().plusHours(ttlHours));
        row.setUserAgent(userAgent == null ? "" : userAgent);
        row.setIp(ip == null ? "" : ip);
        sessionMapper.insert(row);
        return raw;
    }

    @Transactional(readOnly = true)
    public AuthSession resolveActive(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        AuthSession row = sessionMapper.selectOne(new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getSessionTokenHash, hash(rawToken))
                .last("LIMIT 1"));
        if (row == null || row.getRevokedAt() != null) {
            return null;
        }
        if (row.getExpiresAt() != null && row.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return row;
    }

    @Transactional
    public void revokeByToken(String rawToken) {
        sessionMapper.update(null, new LambdaUpdateWrapper<AuthSession>()
                .eq(AuthSession::getSessionTokenHash, hash(rawToken))
                .set(AuthSession::getRevokedAt, LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public PageResult<LoginAttempt> pageLoginAttempts(String username, String ip, Integer success, PageQuery query) {
        Page<LoginAttempt> page = loginAttemptMapper.selectPage(new Page<>(query.current(), query.size()),
                new LambdaQueryWrapper<LoginAttempt>()
                        .eq(username != null && !username.isBlank(), LoginAttempt::getUsername, username)
                        .eq(ip != null && !ip.isBlank(), LoginAttempt::getIp, ip)
                        .eq(success != null, LoginAttempt::getSuccess, success)
                        .orderByDesc(LoginAttempt::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), query.size(), query.current());
    }

    @Transactional(readOnly = true)
    public PageResult<AuthAuditLog> pageAuditLogs(String action, Long actorUserId, PageQuery query) {
        Page<AuthAuditLog> page = auditLogMapper.selectPage(new Page<>(query.current(), query.size()),
                new LambdaQueryWrapper<AuthAuditLog>()
                        .eq(action != null && !action.isBlank(), AuthAuditLog::getAction, action)
                        .eq(actorUserId != null, AuthAuditLog::getActorUserId, actorUserId)
                        .orderByDesc(AuthAuditLog::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), query.size(), query.current());
    }

    public static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("hash failed", ex);
        }
    }
}
