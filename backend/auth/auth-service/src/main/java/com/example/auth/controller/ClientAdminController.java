package com.example.auth.controller;

import com.example.auth.common.PageQuery;
import com.example.auth.common.PageResult;
import com.example.auth.common.Result;
import com.example.auth.dto.ClientCreateRequest;
import com.example.auth.dto.ClientUpdateRequest;
import com.example.auth.dto.OauthClientVo;
import com.example.auth.dto.SecretResetVo;
import com.example.auth.entity.AuthGrant;
import com.example.auth.entity.AuthAuditLog;
import com.example.auth.entity.LoginAttempt;
import com.example.auth.service.OauthClientAdminService;
import com.example.auth.service.SessionService;
import com.example.auth.service.TokenLedgerService;
import com.example.auth.service.AuditService;
import com.example.auth.common.enums.AuditActionEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth Client 运营 + 踢下线 + 审计查询。
 */
@Tag(name = "客户端运营")
@RestController
@RequestMapping("/api/v1/admin")
public class ClientAdminController {

    private final OauthClientAdminService clientAdminService;
    private final TokenLedgerService tokenLedgerService;
    private final SessionService sessionService;
    private final AuditService auditService;

    public ClientAdminController(OauthClientAdminService clientAdminService,
                                 TokenLedgerService tokenLedgerService,
                                 SessionService sessionService,
                                 AuditService auditService) {
        this.clientAdminService = clientAdminService;
        this.tokenLedgerService = tokenLedgerService;
        this.sessionService = sessionService;
        this.auditService = auditService;
    }

    @GetMapping("/clients")
    @Operation(summary = "客户端分页")
    public Result<PageResult<OauthClientVo>> pageClients(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(clientAdminService.page(keyword, enabled, PageQuery.of(current, size)));
    }

    @GetMapping("/clients/{clientId}")
    @Operation(summary = "客户端详情")
    public Result<OauthClientVo> getClient(@PathVariable String clientId) {
        return Result.ok(clientAdminService.getByClientId(clientId));
    }

    @PostMapping("/clients")
    @Operation(summary = "新建客户端；机密客户端 secret 仅返回一次")
    public Result<SecretResetVo> createClient(@Valid @RequestBody ClientCreateRequest request,
                                              HttpServletRequest http) {
        return Result.ok(clientAdminService.create(request, operatorId(http), ip(http)));
    }

    @PutMapping("/clients/{clientId}")
    @Operation(summary = "编辑客户端")
    public Result<Void> updateClient(@PathVariable String clientId,
                                     @RequestBody ClientUpdateRequest request,
                                     HttpServletRequest http) {
        clientAdminService.update(clientId, request, operatorId(http), ip(http));
        return Result.ok();
    }

    @PostMapping("/clients/{clientId}/enabled")
    @Operation(summary = "启停客户端")
    public Result<Void> setEnabled(@PathVariable String clientId,
                                   @RequestParam boolean enabled,
                                   HttpServletRequest http) {
        clientAdminService.setEnabled(clientId, enabled, operatorId(http), ip(http));
        return Result.ok();
    }

    @PostMapping("/clients/{clientId}/secret")
    @Operation(summary = "重置密钥；明文仅返回一次")
    public Result<SecretResetVo> resetSecret(@PathVariable String clientId, HttpServletRequest http) {
        return Result.ok(clientAdminService.resetSecret(clientId, operatorId(http), ip(http)));
    }

    @GetMapping("/grants")
    @Operation(summary = "授权台账分页")
    public Result<PageResult<AuthGrant>> pageGrants(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String clientId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(tokenLedgerService.pageGrants(userId, clientId, PageQuery.of(current, size)));
    }

    @PostMapping("/grants/kick/user/{userId}")
    @Operation(summary = "按用户踢下线")
    public Result<Integer> kickUser(@PathVariable Long userId, HttpServletRequest http) {
        int n = tokenLedgerService.kickByUserId(userId, "kick_offline");
        auditService.record(AuditActionEnum.KICK_OFFLINE, operatorId(http), "user", String.valueOf(userId),
                "kicked grants=" + n, ip(http));
        return Result.ok(n);
    }

    @PostMapping("/grants/kick/client/{clientId}")
    @Operation(summary = "按客户端踢下线")
    public Result<Integer> kickClient(@PathVariable String clientId, HttpServletRequest http) {
        int n = tokenLedgerService.kickByClientId(clientId, "kick_offline");
        auditService.record(AuditActionEnum.KICK_OFFLINE, operatorId(http), "oauth_client", clientId,
                "kicked grants=" + n, ip(http));
        return Result.ok(n);
    }

    @GetMapping("/login-attempts")
    @Operation(summary = "登录审计")
    public Result<PageResult<LoginAttempt>> pageLoginAttempts(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) Integer success,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sessionService.pageLoginAttempts(username, ip, success, PageQuery.of(current, size)));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "安全审计")
    public Result<PageResult<AuthAuditLog>> pageAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sessionService.pageAuditLogs(action, actorUserId, PageQuery.of(current, size)));
    }

    private Long operatorId(HttpServletRequest http) {
        String header = http.getHeader("X-User-Id");
        return header == null || header.isBlank() ? 0L : Long.parseLong(header);
    }

    private String ip(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
