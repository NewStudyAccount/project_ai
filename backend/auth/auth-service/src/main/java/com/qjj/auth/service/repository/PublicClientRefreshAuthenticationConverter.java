package com.qjj.auth.service.repository;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.HashMap;

/**
 * 公开客户端 refresh_token 换票只带 client_id。
 * SAS 1.2 PublicClientAuthenticationConverter 仅匹配 PKCE token 请求（要求 code_verifier），
 * 刷新会落到 invalid_client；此处单独放行 refresh_token + client_id。
 */
public class PublicClientRefreshAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue()
                .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (!StringUtils.hasText(clientId)) {
            return null;
        }
        return new OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null, new HashMap<>());
    }
}
