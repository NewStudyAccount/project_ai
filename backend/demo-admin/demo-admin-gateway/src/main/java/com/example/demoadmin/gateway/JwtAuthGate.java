package com.example.demoadmin.gateway;

import com.example.common.jwt.JwtVerifier;
import com.example.common.web.UserHeaders;
import io.jsonwebtoken.Claims;

/**
 * 网关 JWT 验签逻辑（伪过滤器骨架）：验签后写 X-User-Id。
 * 真实 Spring Cloud Gateway Filter 在接入 SCG 依赖后替换实现。
 */
public class JwtAuthGate {

    private final JwtVerifier verifier;

    public JwtAuthGate(JwtVerifier verifier) {
        this.verifier = verifier;
    }

    public AuthResult authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return AuthResult.unauthorized();
        }
        try {
            Claims claims = verifier.parse(authorizationHeader.substring(7));
            String uid = verifier.uid(claims);
            return AuthResult.ok(uid, claims.get("username") == null ? "" : String.valueOf(claims.get("username")));
        } catch (Exception ex) {
            return AuthResult.unauthorized();
        }
    }

    public record AuthResult(boolean ok, String uid, String username) {
        static AuthResult ok(String uid, String username) {
            return new AuthResult(true, uid, username);
        }

        static AuthResult unauthorized() {
            return new AuthResult(false, null, null);
        }

        public String userIdHeader() {
            return UserHeaders.USER_ID;
        }
    }
}
