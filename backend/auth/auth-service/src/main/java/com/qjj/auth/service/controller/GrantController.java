package com.qjj.auth.service.controller;

import com.qjj.auth.common.result.PageResult;
import com.qjj.auth.common.result.Result;
import com.qjj.auth.framework.idempotent.Idempotent;
import com.qjj.auth.framework.idempotent.RateLimit;
import com.qjj.auth.service.dto.GrantQuery;
import com.qjj.auth.service.service.GrantService;
import com.qjj.auth.service.vo.GrantVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/grants")
@RequiredArgsConstructor
public class GrantController {
    private final GrantService grantService;

    @GetMapping
    @PreAuthorize("hasAuthority('auth:grant:list')")
    public Result<PageResult<GrantVO>> page(@Valid GrantQuery query) {
        return Result.ok(grantService.page(query));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('auth:grant:revoke')")
    @Idempotent(keyPrefix = "grant-revoke")
    @RateLimit(keyPrefix = "grant-revoke", permits = 10, windowSeconds = 60)
    public Result<Void> revoke(@PathVariable String id, @RequestParam(required = false) String reason) {
        grantService.revoke(id, reason);
        return Result.ok();
    }

    @PostMapping("/users/{userId}/revoke")
    @PreAuthorize("hasAuthority('auth:grant:revoke')")
    @Idempotent(keyPrefix = "grant-revoke-user")
    @RateLimit(keyPrefix = "grant-revoke-user", permits = 10, windowSeconds = 60)
    public Result<Void> revokeUser(@PathVariable String userId, @RequestParam(required = false) String reason) {
        grantService.revokeByUser(userId, reason);
        return Result.ok();
    }
}
