package com.qjj.user.service.controller;

import com.qjj.user.common.result.PageResult;
import com.qjj.user.common.result.Result;
import com.qjj.user.service.dto.AuditPageQuery;
import com.qjj.user.service.service.AuditService;
import com.qjj.user.service.vo.AuditVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public Result<PageResult<AuditVO>> page(@Valid AuditPageQuery query) {
        return Result.ok(auditService.page(query));
    }
}
