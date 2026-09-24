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
import java.util.Map;

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

    /**
     * 回填操作员身份：
     * 1) formLogin/SSO 的 AuthPrincipal；
     * 2) 资源服务器 JWT（OIDC Access Token，sub = 用户中心 user.id）。
     * 否则 /me/* 会按 user_id=0 查询导致空菜单/空权限。
     */
    private void fillFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return;
        }
        Object principal = auth.getPrincipal();
        if (fillFromAuthPrincipal(auth, principal)) {
            return;
        }
        fillFromJwt(principal);
    }

    private boolean fillFromAuthPrincipal(Authentication auth, Object principal) {
        if (!principal.getClass().getName().endsWith("AuthPrincipal")) {
            return false;
        }
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
            return true;
        }
        return false;
    }

    /** Jwt principal：getSubject() 或 claims.sub → userId；preferred_username → userName。 */
    private void fillFromJwt(Object principal) {
        String sub = null;
        String preferred = null;
        try {
            Object subject = principal.getClass().getMethod("getSubject").invoke(principal);
            if (subject instanceof String s && !s.isBlank()) {
                sub = s;
            }
        } catch (Exception ignored) {
        }
        try {
            Object claims = principal.getClass().getMethod("getClaims").invoke(principal);
            if (claims instanceof Map<?, ?> map) {
                if (sub == null && map.get("sub") instanceof String s && !s.isBlank()) {
                    sub = s;
                }
                if (map.get("preferred_username") instanceof String s && !s.isBlank()) {
                    preferred = s;
                }
            }
        } catch (Exception ignored) {
        }
        if (sub == null) {
            return;
        }
        AuthContext ctx = new AuthContext();
        try {
            ctx.setUserId(Long.valueOf(sub.trim()));
        } catch (NumberFormatException ignored) {
            return;
        }
        ctx.setUserName(preferred != null ? preferred : sub);
        AuthContext.set(ctx);
    }
}
