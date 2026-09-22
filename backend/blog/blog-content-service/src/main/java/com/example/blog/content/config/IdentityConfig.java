package com.example.blog.content.config;

import com.example.blog.common.auth.BlogConstants;
import com.example.blog.common.auth.JwtVerifier;
import com.example.blog.common.audit.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 服务侧身份注入：信任网关头，同时支持直连时 Bearer 验签（双重鉴权中的 AuthN 补强）。
 */
@Configuration
public class IdentityConfig {

    @Bean
    public JwtVerifier jwtVerifier(
            @Value("${blog.auth.issuer:auth-service}") String issuer,
            @Value("${blog.auth.jwt-secret:change-me-local-dev-secret-not-for-prod}") String secret) {
        return new JwtVerifier(issuer, secret);
    }

    @Bean
    public OncePerRequestFilter currentUserFilter(JwtVerifier jwtVerifier) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                try {
                    String uid = request.getHeader(com.example.blog.common.auth.UserHeaders.USER_ID);
                    String username = request.getHeader(com.example.blog.common.auth.UserHeaders.USERNAME);
                    if (uid == null || uid.isBlank()) {
                        String auth = request.getHeader(com.example.blog.common.auth.UserHeaders.AUTHORIZATION);
                        if (auth != null && auth.startsWith("Bearer ")) {
                            try {
                                var claims = jwtVerifier.parse(auth.substring(7));
                                uid = jwtVerifier.uid(claims);
                                username = claims.get("username") == null
                                        ? "" : String.valueOf(claims.get("username"));
                            } catch (Exception ignored) {
                                // 无有效身份
                            }
                        }
                    }
                    if (uid != null && !uid.isBlank()) {
                        CurrentUser.set(Long.parseLong(uid), username);
                    }
                    filterChain.doFilter(request, response);
                } finally {
                    CurrentUser.clear();
                }
            }
        };
    }
}
