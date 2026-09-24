package com.qjj.auth.service.security;

import com.qjj.auth.service.service.CredentialService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RequiredArgsConstructor
public class CredentialAuthenticationProvider implements AuthenticationProvider {
    private final CredentialService credentialService;
    private final LoginAttemptService loginAttemptService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = String.valueOf(authentication.getPrincipal());
        String password = String.valueOf(authentication.getCredentials());
        HttpServletRequest request = currentRequest();
        String ip = request == null ? "" : request.getRemoteAddr();
        String userAgent = request == null ? "" : request.getHeader("User-Agent");
        String clientId = request == null ? "" : request.getParameter("client_id");
        try {
            loginAttemptService.ensureNotLocked(username, ip);
            AuthPrincipal principal = credentialService.authenticate(username, password);
            loginAttemptService.recordSuccess(principal, username, ip, userAgent, clientId);
            return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        } catch (Exception ex) {
            loginAttemptService.recordFailure(username, null, "认证失败", ip, userAgent, clientId);
            throw new BadCredentialsException("登录失败");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes servletAttrs ? servletAttrs.getRequest() : null;
    }
}
