package com.qjj.auth.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Configuration
public class OAuth2TokenConfig {
    private static final OAuth2TokenType ID_TOKEN = new OAuth2TokenType("id_token");

    @Bean
    public OAuth2TokenCustomizer<org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext> jwtCustomizer() {
        return context -> {
            if (!context.getTokenType().equals(OAuth2TokenType.ACCESS_TOKEN)
                    && !context.getTokenType().equals(ID_TOKEN)) {
                return;
            }
            Set<String> allowed = Set.of("iss", "sub", "aud", "exp", "iat", "jti", "auth_time", "preferred_username");
            context.getClaims().claims(claims -> {
                Map<String, Object> minimal = new HashMap<>();
                allowed.forEach(key -> {
                    if (claims.containsKey(key)) {
                        minimal.put(key, claims.get(key));
                    }
                });
                minimal.putIfAbsent("jti", UUID.randomUUID().toString());
                minimal.putIfAbsent("auth_time", Instant.now().getEpochSecond());
                Object principal = context.getPrincipal();
                if (principal instanceof org.springframework.security.core.Authentication authentication) {
                    principal = authentication.getPrincipal();
                }
                String preferred = "";
                if (principal instanceof com.qjj.auth.service.security.AuthPrincipal ap) {
                    preferred = ap.displayName();
                } else if (principal != null) {
                    preferred = principal.toString();
                }
                minimal.put("preferred_username", preferred);
                claims.clear();
                claims.putAll(minimal);
            });
        };
    }
}
