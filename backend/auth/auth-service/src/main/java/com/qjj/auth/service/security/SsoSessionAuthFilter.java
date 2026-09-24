package com.qjj.auth.service.security;

import com.qjj.auth.service.service.RbacService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 用 AUTH_SSO_SESSION 恢复 SecurityContext，并从本库 RBAC 装载 authorities（供 @PreAuthorize）。
 * Cookie 与 IdP 同主机时，跨端口 authorize 可免登。
 */
@RequiredArgsConstructor
public class SsoSessionAuthFilter extends OncePerRequestFilter {
    private final SsoSessionService ssoSessionService;
    private final RbacService rbacService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var existing = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = existing == null
                || !existing.isAuthenticated()
                || "anonymousUser".equals(existing.getPrincipal());
        if (anonymous) {
            String sessionId = ssoSessionService.resolveSessionId(request);
            AuthPrincipal principal = ssoSessionService.resolve(sessionId);
            if (principal != null) {
                Set<String> permissions = rbacService.permissionsForUser(principal.userId());
                AuthPrincipal withPerms = new AuthPrincipal(principal.userId(), principal.username(), permissions);
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(withPerms, null, withPerms.getAuthorities()));
            }
        }
        chain.doFilter(request, response);
    }
}
