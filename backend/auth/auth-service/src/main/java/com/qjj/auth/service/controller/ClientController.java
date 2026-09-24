package com.qjj.auth.service.controller;

import com.qjj.auth.common.result.PageResult;
import com.qjj.auth.common.result.Result;
import com.qjj.auth.framework.idempotent.Idempotent;
import com.qjj.auth.framework.idempotent.RateLimit;
import com.qjj.auth.service.dto.ClientRequest;
import com.qjj.auth.service.dto.ClientStatusRequest;
import com.qjj.auth.service.service.ClientService;
import com.qjj.auth.service.vo.ClientSecretVO;
import com.qjj.auth.service.vo.ClientVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {
    private final ClientService clientService;

    @GetMapping
    @PreAuthorize("hasAuthority('auth:client:list')")
    public Result<PageResult<ClientVO>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String clientId,
                                             @RequestParam(required = false) String systemCode,
                                             @RequestParam(required = false) Integer enabled) {
        return Result.ok(clientService.page(current, size, clientId, systemCode, enabled));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('auth:client:create')")
    @Idempotent(keyPrefix = "client-create")
    @RateLimit(keyPrefix = "client-create", permits = 20, windowSeconds = 60)
    public Result<ClientSecretVO> create(@Valid @RequestBody ClientRequest request) {
        return Result.ok(clientService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:client:update')")
    @Idempotent(keyPrefix = "client-update")
    @RateLimit(keyPrefix = "client-update", permits = 30, windowSeconds = 60)
    public Result<ClientVO> update(@PathVariable String id, @Valid @RequestBody ClientRequest request) {
        return Result.ok(clientService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('auth:client:update')")
    @Idempotent(keyPrefix = "client-status")
    public Result<ClientVO> status(@PathVariable String id, @Valid @RequestBody ClientStatusRequest request) {
        return Result.ok(clientService.updateStatus(id, request.getEnabled()));
    }

    @PostMapping("/{id}/secret")
    @PreAuthorize("hasAuthority('auth:client:secret')")
    @Idempotent(keyPrefix = "client-secret")
    public Result<ClientSecretVO> resetSecret(@PathVariable String id) {
        return Result.ok(clientService.resetSecret(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:client:delete')")
    @Idempotent(keyPrefix = "client-delete")
    public Result<Void> delete(@PathVariable String id) {
        clientService.delete(id);
        return Result.ok();
    }
}
