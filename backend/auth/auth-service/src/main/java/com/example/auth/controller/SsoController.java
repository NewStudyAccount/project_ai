package com.example.auth.controller;

import com.example.auth.api.AuthPaths;
import com.example.auth.common.BizException;
import com.example.auth.common.Result;
import com.example.auth.dto.SsoTokenRequest;
import com.example.auth.entity.SysUser;
import com.example.auth.mapper.SysUserMapper;
import com.example.auth.service.SsoSessionService;
import com.example.auth.service.TokenService;
import com.example.auth.vo.TokenVo;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO：authorize 有 sid 则发 code 回跳；token 用 code 换 JWT。
 */
@RestController
public class SsoController {

    private final SsoSessionService ssoSessionService;
    private final TokenService tokenService;
    private final SysUserMapper userMapper;

    public SsoController(
            SsoSessionService ssoSessionService,
            TokenService tokenService,
            SysUserMapper userMapper) {
        this.ssoSessionService = ssoSessionService;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
    }

    @GetMapping(AuthPaths.SSO_AUTHORIZE)
    public void authorize(
            @RequestParam("client_id") String clientId,
            @RequestParam("return_url") String returnUrl,
            @CookieValue(value = "AUTH_SID", required = false) String sid,
            HttpServletResponse response) throws IOException {
        if (!ssoSessionService.isReturnUrlAllowed(returnUrl)) {
            throw BizException.badParam("return_url 不在白名单");
        }
        SsoSessionService.SidInfo info = ssoSessionService.resolveSid(sid);
        if (info == null) {
            String login = "/auth/login-ui?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&return_url=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
            response.sendRedirect(login);
            return;
        }
        String code = ssoSessionService.createCode(info.userId(), info.username(), clientId);
        String join = returnUrl.contains("?") ? "&" : "?";
        response.sendRedirect(returnUrl + join + "code=" + code);
    }

    @PostMapping(AuthPaths.SSO_TOKEN)
    public Result<TokenVo> token(@Valid @RequestBody SsoTokenRequest req) {
        SsoSessionService.SidInfo info = ssoSessionService.consumeCode(req.code(), req.clientId());
        if (info == null) {
            throw BizException.auth("授权码无效或已使用");
        }
        SysUser user = userMapper.selectById(info.userId());
        if (user == null) {
            throw BizException.notFound();
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.disabled();
        }
        TokenService.TokenPair pair = tokenService.issue(user.getId(), user.getUsername(), req.clientId());
        return Result.ok(new TokenVo(
                pair.accessToken(), pair.refreshToken(), pair.accessExpiresIn(),
                user.getId(), user.getUsername(), user.getRealName()));
    }

    @GetMapping("/auth/login-ui")
    public Map<String, String> loginUiHint(
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "return_url", required = false) String returnUrl) {
        return Map.of(
                "hint", "POST /auth/login 建立 sid 后再次 GET /auth/sso/authorize",
                "clientId", clientId == null ? "" : clientId,
                "returnUrl", returnUrl == null ? "" : returnUrl);
    }
}
