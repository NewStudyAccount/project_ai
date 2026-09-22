package com.example.auth.controller;

import com.example.auth.common.Result;
import com.example.auth.dto.CredentialInitRequest;
import com.example.auth.service.CredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 凭证初始化（供用户中心建号后调用）；用户中心不回调验密。
 */
@Tag(name = "凭证")
@RestController
@RequestMapping("/api/v1/auth/credentials")
public class CredentialController {

    private final CredentialService credentialService;

    public CredentialController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/init")
    @Operation(summary = "初始化/重置密码凭证")
    public Result<Void> init(@Valid @RequestBody CredentialInitRequest request) {
        credentialService.initPasswordCredential(request.userId(), request.rawPassword());
        return Result.ok();
    }
}
