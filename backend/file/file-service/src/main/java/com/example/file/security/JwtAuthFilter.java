package com.example.file.security;

import com.example.file.config.FileProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bearer JWT 验签（与 auth 同 iss/密钥），成功写入 SecurityContext。 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final FileProperties properties;

    public JwtAuthFilter(FileProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    Claims claims = parse(header.substring(7));
                    String uid = claims.get("uid") == null
                            ? (claims.getSubject() == null ? "" : claims.getSubject())
                            : String.valueOf(claims.get("uid"));
                    String username = claims.get("username") == null ? "" : String.valueOf(claims.get("username"));
                    if (!uid.isBlank()) {
                        long userId = Long.parseLong(uid);
                        CurrentUser.set(userId, username);
                        var authentication = new UsernamePasswordAuthenticationToken(
                                String.valueOf(userId), null, List.of());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception ignored) {
                    // 非法 token：不写身份，后续 401
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            CurrentUser.clear();
            SecurityContextHolder.clearContext();
        }
    }

    public Claims parse(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
                properties.getAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getAuth().getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
