package com.qjj.auth.service.security;

import com.qjj.auth.service.config.AuthSecurityProperties;
import com.qjj.auth.service.service.GrantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OIDC 统一登出：最高优先级独占 /connect/logout。
 * SAS 会把该路径当作 end_session_endpoint 并要求 Bearer，故在安全过滤器之前处理
 * Cookie 会话登出 + RT/grant 吊销，并校验 post_logout_redirect_uri 白名单（禁开放重定向）。
 */
@Slf4j
@RequiredArgsConstructor
public class OidcLogoutFilter extends OncePerRequestFilter {
    private final SsoSessionService ssoSessionService;
    private final GrantService grantService;
    private final AuthSecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"/connect/logout".equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String postLogoutRedirectUri = request.getParameter("post_logout_redirect_uri");
        String state = request.getParameter("state");

        String sessionId = ssoSessionService.resolveSessionId(request);
        AuthPrincipal principal = sessionId == null ? null : ssoSessionService.resolve(sessionId);
        if (principal != null) {
            String userId = String.valueOf(principal.userId());
            grantService.revokeByUser(userId, "unified-logout");
            ssoSessionService.clearSession(sessionId, request, response);
            log.info("统一登出 userId={} sessionId={}", userId, sessionId);
        } else {
            ssoSessionService.clearSession(sessionId, request, response);
            log.info("统一登出（无有效会话）");
        }

        if (securityProperties.isAllowedPostLogoutRedirect(postLogoutRedirectUri)) {
            String target = postLogoutRedirectUri;
            if (state != null && !state.isBlank()) {
                target = target + (target.contains("?") ? "&" : "?")
                        + "state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
            }
            response.sendRedirect(target);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8");
        response.getWriter().write("""
                <!DOCTYPE html>
                <html lang="zh-CN"><head><meta charset="UTF-8"><title>已登出</title></head>
                <body><p>已退出登录。</p></body></html>
                """);
    }
}
