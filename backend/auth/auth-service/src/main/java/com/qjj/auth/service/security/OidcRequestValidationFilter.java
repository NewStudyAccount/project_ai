package com.qjj.auth.service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 授权请求参数 + redirect_uri 精确白名单校验（spec auth-oidc-login）。 */
public class OidcRequestValidationFilter extends OncePerRequestFilter {

    private final RegisteredClientRepository clientRepository;

    public OidcRequestValidationFilter(RegisteredClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"/oauth2/authorize".equals(request.getRequestURI()) || !"GET".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String responseType = request.getParameter("response_type");
        String challengeMethod = request.getParameter("code_challenge_method");
        String clientId = request.getParameter("client_id");
        String redirectUri = request.getParameter("redirect_uri");
        boolean valid = "code".equals(responseType)
                && hasText(clientId)
                && hasText(redirectUri)
                && hasText(request.getParameter("state"))
                && hasText(request.getParameter("nonce"))
                && hasText(request.getParameter("code_challenge"))
                && "S256".equals(challengeMethod);
        if (!valid) {
            reject(response, "授权请求参数校验失败");
            return;
        }
        RegisteredClient client = clientRepository.findByClientId(clientId);
        if (client == null
                || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE)
                || !client.getRedirectUris().contains(redirectUri)) {
            reject(response, "redirect_uri 不在白名单");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":30001,\"msg\":\"" + msg + "\",\"data\":null}");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
