package com.qjj.auth.service.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 未登录浏览器请求统一跳到 auth-portal 唯一登录门面，并带上 return_url。 */
public class PortalLoginAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final AuthSecurityProperties properties;

    public PortalLoginAuthenticationEntryPoint(AuthSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws java.io.IOException {
        String full = request.getRequestURL().toString();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            full = full + "?" + request.getQueryString();
        }
        String portal = properties.getLoginPortalBase();
        if (portal == null || portal.isBlank()) {
            portal = "http://localhost:5174";
        }
        String target = portal + "/login?return_url="
                + URLEncoder.encode(full, StandardCharsets.UTF_8);
        response.sendRedirect(target);
    }
}
