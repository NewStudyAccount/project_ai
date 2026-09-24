package com.qjj.auth.service.controller;

import com.qjj.auth.common.result.PageResult;
import com.qjj.auth.common.result.Result;
import com.qjj.auth.service.dto.AuditQuery;
import com.qjj.auth.service.dto.LoginAttemptQuery;
import com.qjj.auth.service.service.AuditService;
import com.qjj.auth.service.vo.AuditVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('auth:audit:list')")
    public Result<PageResult<AuditVO>> page(@Valid AuditQuery query) {
        return Result.ok(auditService.page(query));
    }

    @GetMapping("/login-attempts")
    @PreAuthorize("hasAuthority('auth:audit:list')")
    public Result<PageResult<AuditVO>> loginAttempts(@Valid LoginAttemptQuery query) {
        return Result.ok(auditService.loginAttempts(query));
    }
}
