package com.qjj.auth.service.controller;

import com.qjj.auth.service.config.AuthSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 唯一登录门面在 auth-portal：GET /login 只做跳转，不在认证中心渲染表单。
 * POST /login 仍由 Spring Security formLogin 处理（portal 代理提交账密）。
 */
@Controller
@RequiredArgsConstructor
public class LoginPortalRedirectController {
    private final AuthSecurityProperties properties;

    @GetMapping("/login")
    public void toPortal(@RequestParam(value = "return_url", required = false) String returnUrl,
                         jakarta.servlet.http.HttpServletResponse response) throws IOException {
        String portal = properties.getLoginPortalBase();
        if (portal == null || portal.isBlank()) {
            portal = "http://localhost:5174";
        }
        String target = portal + "/login";
        if (returnUrl != null && !returnUrl.isBlank()) {
            target = target + "?return_url=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
        }
        response.sendRedirect(target);
    }
}
