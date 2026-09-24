package com.qjj.auth.framework.core;

import com.qjj.auth.common.constants.CommonConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AuthContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String userId = request.getHeader(CommonConstants.HEADER_USER_ID);
            if (userId != null && !userId.isBlank()) {
                AuthContext ctx = new AuthContext();
                try {
                    ctx.setUserId(Long.valueOf(userId.trim()));
                } catch (NumberFormatException ignored) {
                }
                ctx.setUserName(request.getHeader(CommonConstants.HEADER_USER_NAME));
                AuthContext.set(ctx);
            } else {
                fillFromSecurityContext();
            }
            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    /** 认证中心 formLogin 会话：从 SecurityContext 回填操作员身份。 */
    private void fillFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return;
        }
        Object principal = auth.getPrincipal();
        if (principal.getClass().getName().endsWith("AuthPrincipal")) {
            AuthContext ctx = new AuthContext();
            try {
                var userId = principal.getClass().getMethod("userId").invoke(principal);
                if (userId instanceof Long id) {
                    ctx.setUserId(id);
                }
            } catch (Exception ignored) {
            }
            ctx.setUserName(auth.getName());
            if (ctx.getUserId() != null) {
                AuthContext.set(ctx);
            }
        }
    }
}
