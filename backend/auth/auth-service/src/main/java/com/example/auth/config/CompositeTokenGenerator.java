package com.example.auth.config;

import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * 组合 JWT Access Token 与自定义 Refresh Token 生成器。
 */
public class CompositeTokenGenerator implements OAuth2TokenGenerator<OAuth2Token> {

    private final OAuth2TokenGenerator<?> accessTokenGenerator;
    private final OAuth2TokenGenerator<?> refreshTokenGenerator;

    @SuppressWarnings("unchecked")
    public CompositeTokenGenerator(OAuth2TokenGenerator<?> accessTokenGenerator,
                                   OAuth2TokenGenerator<?> refreshTokenGenerator) {
        this.accessTokenGenerator = accessTokenGenerator;
        this.refreshTokenGenerator = refreshTokenGenerator;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuth2Token generate(OAuth2TokenContext context) {
        OAuth2Token token = ((OAuth2TokenGenerator<OAuth2Token>) accessTokenGenerator).generate(context);
        if (token != null) {
            return token;
        }
        return ((OAuth2TokenGenerator<OAuth2Token>) refreshTokenGenerator).generate(context);
    }
}
