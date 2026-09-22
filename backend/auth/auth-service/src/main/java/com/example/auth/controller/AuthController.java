package com.example.auth.controller;

import com.example.auth.api.AuthPaths;
import com.example.auth.common.BizException;
import com.example.auth.common.Result;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RefreshRequest;
import com.example.auth.entity.SysUser;
import com.example.auth.service.CredentialService;
import com.example.auth.service.SsoSessionService;
import com.example.auth.service.TokenService;
import com.example.auth.vo.TokenVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 登录 / 刷新 / 登出。 */
@RestController
public class AuthController {

    private final CredentialService credentialService;
    private final TokenService tokenService;
    private final SsoSessionService ssoSessionService;

    public AuthController(
            CredentialService credentialService,
            TokenService tokenService,
            SsoSessionService ssoSessionService) {
        this.credentialService = credentialService;
        this.tokenService = tokenService;
        this.ssoSessionService = ssoSessionService;
    }

    @PostMapping(AuthPaths.LOGIN)
    public Result<TokenVo> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest http,
            @CookieValue(value = "AUTH_SID", required = false) String sid) {
        String ip = clientIp(http);
        String ua = http.getHeader("User-Agent");
        SysUser user = credentialService.authenticate(req.username(), req.password(), ip, ua);
        String newSid = ssoSessionService.createSid(user.getId(), user.getUsername());
        TokenService.TokenPair pair = tokenService.issue(user.getId(), user.getUsername(), newSid);
        return Result.ok(new TokenVo(
                pair.accessToken(), pair.refreshToken(), pair.accessExpiresIn(),
                user.getId(), user.getUsername(), user.getRealName()));
    }

    @PostMapping(AuthPaths.REFRESH)
    public Result<TokenVo> refresh(@Valid @RequestBody RefreshRequest req) {
        // username 从旧 refresh 绑定的 user 再查
        String valHint = req.refreshToken();
        TokenService.TokenPair pair;
        try {
            pair = tokenService.refresh(valHint, "");
        } catch (BizException ex) {
            throw ex;
        }
        // subject 里带 uid；username 由后续解析补齐时可扩展
        return Result.ok(new TokenVo(
                pair.accessToken(), pair.refreshToken(), pair.accessExpiresIn(),
                null, "", ""));
    }

    @PostMapping(AuthPaths.LOGOUT)
    public Result<Void> logout(
            @RequestBody(required = false) RefreshRequest req,
            @CookieValue(value = "AUTH_SID", required = false) String sid,
            jakarta.servlet.http.HttpServletRequest http) {
        String auth = http.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            tokenService.blacklistAccess(auth.substring(7));
        }
        if (req != null && req.refreshToken() != null) {
            tokenService.revokeRefresh(req.refreshToken());
        }
        ssoSessionService.destroySid(sid);
        return Result.ok();
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
