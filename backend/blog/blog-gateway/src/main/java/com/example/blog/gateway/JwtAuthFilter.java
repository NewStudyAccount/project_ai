package com.example.blog.gateway;

import com.example.blog.common.auth.BlogConstants;
import com.example.blog.common.auth.JwtVerifier;
import com.example.blog.common.auth.UserHeaders;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** JWT 验签：成功写 X-User-Id / X-Username；失败 401。public 路径放行。 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtVerifier verifier;
    private final List<String> publicPrefixes;

    public JwtAuthFilter(
            @Value("${blog.auth.issuer:auth-service}") String issuer,
            @Value("${blog.auth.jwt-secret:change-me-local-dev-secret-not-for-prod}") String secret,
            @Value("${blog.auth.public-prefixes:#{null}}") List<String> publicPrefixes) {
        this.verifier = new JwtVerifier(issuer, secret);
        this.publicPrefixes = publicPrefixes == null || publicPrefixes.isEmpty()
                ? List.of(BlogConstants.PUBLIC_PATH_PREFIX)
                : publicPrefixes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublic(path) || path.startsWith("/actuator")) {
            filterChain.doFilter(new HeaderClearRequest(request), response);
            return;
        }
        String header = request.getHeader(UserHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return;
        }
        try {
            Claims claims = verifier.parse(header.substring(7));
            String uid = verifier.uid(claims);
            String username = claims.get("username") == null ? "" : String.valueOf(claims.get("username"));
            if (uid == null || uid.isBlank()) {
                writeUnauthorized(response);
                return;
            }
            filterChain.doFilter(new IdentityRequest(request, uid, username), response);
        } catch (Exception ex) {
            writeUnauthorized(response);
        }
    }

    private boolean isPublic(String path) {
        for (String prefix : publicPrefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"未授权\",\"data\":null}");
    }

    private static class IdentityRequest extends HttpServletRequestWrapper {
        private final String uid;
        private final String username;

        IdentityRequest(HttpServletRequest request, String uid, String username) {
            super(request);
            this.uid = uid;
            this.username = username;
        }

        @Override
        public String getHeader(String name) {
            if (UserHeaders.USER_ID.equalsIgnoreCase(name)) {
                return uid;
            }
            if (UserHeaders.USERNAME.equalsIgnoreCase(name)) {
                return username;
            }
            return super.getHeader(name);
        }
    }

    private static class HeaderClearRequest extends HttpServletRequestWrapper {
        HeaderClearRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if (UserHeaders.USER_ID.equalsIgnoreCase(name) || UserHeaders.USERNAME.equalsIgnoreCase(name)) {
                return null;
            }
            return super.getHeader(name);
        }
    }
}
