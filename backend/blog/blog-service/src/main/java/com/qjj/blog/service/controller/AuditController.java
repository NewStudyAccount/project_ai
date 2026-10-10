package com.qjj.blog.service.controller;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.common.result.Result;
import com.qjj.blog.service.dto.AuditPageQuery;
import com.qjj.blog.service.service.AuditService;
import com.qjj.blog.service.vo.AuditVO;
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
