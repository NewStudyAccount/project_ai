package com.qjj.blog.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewaySecurityProperties.class)
@RequiredArgsConstructor
public class GatewayConfig {

    private final GatewaySecurityProperties properties;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        if (properties.isLocalPassThrough()) {
            return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                    .build();
        }
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthorityPrefix("");
        authorities.setAuthoritiesClaimName("permission");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(properties.getPublicPaths().toArray(String[]::new)).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        new ReactiveJwtAuthenticationConverterAdapter(converter))))
                .build();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getTrustedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    @Bean
    public GlobalFilter userHeaderFilter() {
        return new UserHeaderFilter(properties);
    }

    static class UserHeaderFilter implements GlobalFilter, Ordered {

        private final GatewaySecurityProperties properties;

        UserHeaderFilter(GatewaySecurityProperties properties) {
            this.properties = properties;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            ServerHttpRequest request = exchange.getRequest();
            // 韬唤鏉ユ簮浼樺厛绾э細JWT(sub) > local X-Dev-*锛堜粎 local-pass-through锛? 瀹㈡埛绔?X-User-*锛堜笉淇′换锛屼粎鍏滃簳锛?
            // 蹇呴』浠?Bearer 瑙ｆ瀽 sub锛屽惁鍒欎笟鍔′晶 BlogUserContext.userId=0锛?me/* 绌恒€乤dmin 鍏ㄩ噺涓嶇敓鏁?
            String[] idAndName = resolveIdentity(request);
            String finalUserId = idAndName[0];
            String finalUserName = idAndName[1];
            ServerHttpRequest mutated = request.mutate().headers(headers -> {
                headers.remove("X-User-Id");
                headers.remove("X-User-Name");
                if (finalUserId != null && !finalUserId.isBlank()) {
                    headers.set("X-User-Id", finalUserId);
                }
                if (finalUserName != null && !finalUserName.isBlank()) {
                    headers.set("X-User-Name", finalUserName);
                }
            }).build();
            return chain.filter(exchange.mutate().request(mutated).build());
        }

        private String[] resolveIdentity(ServerHttpRequest request) {
            String[] fromJwt = parseBearerIdentity(request.getHeaders().getFirst("Authorization"));
            if (fromJwt[0] != null && !fromJwt[0].isBlank()) {
                return fromJwt;
            }
            if (properties.isLocalPassThrough()) {
                String devId = request.getHeaders().getFirst("X-Dev-User-Id");
                String devName = request.getHeaders().getFirst("X-Dev-User-Name");
                return new String[] {devId, devName};
            }
            return new String[] {null, null};
        }

        /** 瑙ｆ瀽 JWT payload锛坰ub / preferred_username锛夈€傜綉鍏抽獙绛惧悗浠嶅簲浠?SecurityContext 涓哄噯鏃跺彲鍐嶆敹绱с€?*/
        private String[] parseBearerIdentity(String authorization) {
            String empty[] = new String[] {null, null};
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return empty;
            }
            String token = authorization.substring(7).trim();
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return empty;
            }
            try {
                byte[] raw = java.util.Base64.getUrlDecoder().decode(parts[1]);
                String json = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
                String sub = extractJsonString(json, "sub");
                String preferred = extractJsonString(json, "preferred_username");
                return new String[] {sub, preferred != null ? preferred : sub};
            } catch (Exception ex) {
                return empty;
            }
        }

        private String extractJsonString(String json, String field) {
            String key = "\"" + field + "\"";
            int i = json.indexOf(key);
            if (i < 0) {
                return null;
            }
            int colon = json.indexOf(':', i + key.length());
            if (colon < 0) {
                return null;
            }
            int startQuote = json.indexOf('"', colon + 1);
            if (startQuote < 0) {
                return null;
            }
            int endQuote = json.indexOf('"', startQuote + 1);
            if (endQuote < 0) {
                return null;
            }
            return json.substring(startQuote + 1, endQuote);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE + 10;
        }
    }
}

