package com.example.auth.service;

import com.example.auth.common.BizException;
import com.example.auth.common.ErrorCode;
import com.example.auth.common.enums.AuditActionEnum;
import com.example.auth.dto.LoginRequest;
import com.example.auth.entity.AuthSession;
import com.example.auth.user.dto.UserSummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 账密登录编排：风控 → 用户中心解析 → 本地验凭证 → 建会话。
 * Feign 调用均在事务外（本服务无类级 @Transactional）。
 */
@Service
public class LoginService {

    private final UserResolveService userResolveService;
    private final CredentialService credentialService;
    private final LoginAttemptService loginAttemptService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final AuthProperties authProperties;

    public LoginService(UserResolveService userResolveService, CredentialService credentialService,
                        LoginAttemptService loginAttemptService, SessionService sessionService,
                        AuditService auditService, AuthProperties authProperties) {
        this.userResolveService = userResolveService;
        this.credentialService = credentialService;
        this.loginAttemptService = loginAttemptService;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.authProperties = authProperties;
    }

    public String login(LoginRequest request, HttpServletRequest http) {
        String ip = clientIp(http);
        String userAgent = http.getHeader("User-Agent");
        String username = request.username() == null ? "" : request.username().trim();

        if (loginAttemptService.isLocked(username, ip)) {
            loginAttemptService.record(username, null, false, "locked", ip, userAgent, request.clientId());
            throw new BizException(ErrorCode.LOGIN_LOCKED);
        }

        UserSummaryDto user;
        try {
            user = userResolveService.getByUsername(username);
        } catch (BizException ex) {
            loginAttemptService.record(username, null, false, "user_center_unavailable", ip, userAgent, request.clientId());
            auditService.record(AuditActionEnum.LOGIN_FAIL, null, "user", username, "user_center_unavailable", ip);
            throw ex;
        }

        if (user == null || user.id() == null) {
            loginAttemptService.record(username, null, false, "not_found", ip, userAgent, request.clientId());
            auditService.record(AuditActionEnum.LOGIN_FAIL, null, "user", username, "not_found", ip);
            throw new BizException(ErrorCode.CREDENTIAL_INVALID);
        }

        if (user.status() != null && user.status() != 1) {
            loginAttemptService.record(username, user.id(), false, "disabled", ip, userAgent, request.clientId());
            auditService.record(AuditActionEnum.LOGIN_FAIL, user.id(), "user", username, "disabled", ip);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        boolean matched = credentialService.matchesPassword(user.id(), request.password());
        if (!matched) {
            loginAttemptService.record(username, user.id(), false, "bad_password", ip, userAgent, request.clientId());
            auditService.record(AuditActionEnum.LOGIN_FAIL, user.id(), "user", username, "bad_password", ip);
            throw new BizException(ErrorCode.CREDENTIAL_INVALID);
        }

        String sessionRaw = sessionService.createSession(user.id(), ip, userAgent, authProperties.getSessionTtlHours());
        loginAttemptService.record(username, user.id(), true, "", ip, userAgent, request.clientId());
        auditService.record(AuditActionEnum.LOGIN, user.id(), "user", username, "login ok", ip);
        return sessionRaw;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
