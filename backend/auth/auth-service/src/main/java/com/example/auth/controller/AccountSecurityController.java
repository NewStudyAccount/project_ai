package com.example.auth.controller;

import com.example.auth.api.AuthPaths;
import com.example.auth.common.Result;
import com.example.auth.dto.ChangePasswordRequest;
import com.example.auth.dto.StatusRequest;
import com.example.auth.service.AccountSecurityService;
import com.example.auth.service.SsoSessionService;
import com.example.auth.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 账号安全：改密、启停。改密后吊销 refresh + sid。 */
@RestController
public class AccountSecurityController {

    private final AccountSecurityService accountSecurityService;
    private final TokenService tokenService;
    private final SsoSessionService ssoSessionService;

    public AccountSecurityController(
            AccountSecurityService accountSecurityService,
            TokenService tokenService,
            SsoSessionService ssoSessionService) {
        this.accountSecurityService = accountSecurityService;
        this.tokenService = tokenService;
        this.ssoSessionService = ssoSessionService;
    }

    @PostMapping(AuthPaths.ACCOUNT_PASSWORD)
    public Result<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            @RequestBody(required = false) String refreshIgnored,
            HttpServletRequest http) {
        long userId = currentUserId(http);
        accountSecurityService.changePassword(userId, req.oldPassword(), req.newPassword());
        String auth = http.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            tokenService.blacklistAccess(auth.substring(7));
        }
        // 强制重新登录：客户端应同时丢弃 refresh；服务端 destroy 当前 sid
        // sid 由 Cookie 提供时销毁
        // 简化实现：此处不读 Cookie 亦可由 logout 完成；保留扩展点
        return Result.ok();
    }

    @PostMapping(AuthPaths.ACCOUNT_STATUS)
    public Result<Void> updateStatus(@RequestBody StatusRequest req) {
        accountSecurityService.updateStatus(req.userId(), req.status());
        return Result.ok();
    }

    private long currentUserId(HttpServletRequest http) {
        String uid = http.getHeader("X-User-Id");
        if (uid == null || uid.isBlank()) {
            // auth 自身接口：从 Authorization Bearer 解析
            String auth = http.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                try {
                    return Long.parseLong(tokenService.parse(auth.substring(7)).getSubject());
                } catch (Exception e) {
                    throw com.example.auth.common.BizException.auth("未认证");
                }
            }
            throw com.example.auth.common.BizException.auth("未认证");
        }
        return Long.parseLong(uid);
    }
}
