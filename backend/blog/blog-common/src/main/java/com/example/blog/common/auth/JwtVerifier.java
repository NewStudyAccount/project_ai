package com.example.blog.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

/** 与 auth 同 iss/密钥的 JWT 验签（blog 内自实现，不依赖 platform-common）。 */
public class JwtVerifier {

    private final String issuer;
    private final SecretKey key;

    public JwtVerifier(String issuer, String secret) {
        this.issuer = issuer;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String uid(Claims claims) {
        Object uid = claims.get("uid");
        return uid == null ? claims.getSubject() : String.valueOf(uid);
    }
}
