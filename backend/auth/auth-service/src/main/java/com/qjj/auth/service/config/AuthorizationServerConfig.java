package com.qjj.auth.service.config;

import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.qjj.auth.service.mapper.OAuthClientMapper;
import com.qjj.auth.service.repository.AuthRegisteredClientRepository;
import com.qjj.auth.service.repository.RedisOAuth2AuthorizationService;
import com.qjj.auth.service.repository.PublicClientRefreshTokenGenerator;
import com.qjj.auth.service.service.TokenManagementService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/** SAS 协议内核配置：只负责 OIDC 端点与令牌生成。 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            RegisteredClientRepository registeredClientRepository,
            org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource,
            com.qjj.auth.service.security.SsoSessionService ssoSessionService,
            com.qjj.auth.service.service.RbacService rbacService,
            com.qjj.auth.service.config.AuthSecurityProperties authSecurityProperties) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        // SPA 跨域 POST /oauth2/token 必须在本链处理 CORS，否则响应无 ACAO，浏览器拦下换票结果
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        http.csrf(csrf -> csrf.disable());
        // 必须在 OAuth2AuthorizationEndpointFilter 之前恢复 SSO：
        // 该 filter 在资源所有者未认证时会 chain.doFilter 穿过（源码 193 行），期望后续 EntryPoint 拦截；
        // 若 SSO 恢复发生在它之后，会 AuthorizationFilter 放行后掉进 MVC 静态资源，表现为 10004。
        http.addFilterAfter(new com.qjj.auth.service.security.SsoSessionAuthFilter(ssoSessionService, rbacService),
                org.springframework.security.web.context.SecurityContextHolderFilter.class);
        http.exceptionHandling(ex -> ex.authenticationEntryPoint(
                new com.qjj.auth.service.config.PortalLoginAuthenticationEntryPoint(authSecurityProperties)));
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .clientAuthentication(client -> {
                    client.authenticationConverters(converters ->
                            converters.add(0, new com.qjj.auth.service.repository.PublicClientRefreshAuthenticationConverter()));
                    client.authenticationProvider(
                            new com.qjj.auth.service.repository.PublicClientRefreshAuthenticationProvider(registeredClientRepository));
                })
                .tokenRevocationEndpoint(Customizer.withDefaults())
                .oidc(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(OAuthClientMapper mapper) {
        return new AuthRegisteredClientRepository(mapper);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(StringRedisTemplate redisTemplate,
                                                            TokenManagementService tokenManagementService,
                                                            com.qjj.auth.service.service.AuditService auditService) {
        return new RedisOAuth2AuthorizationService(redisTemplate, tokenManagementService, auditService);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("auth-rs256")
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        try {
            JWKSelector selector = new JWKSelector(new JWKMatcher.Builder().keyID("auth-rs256").build());
            RSAKey rsaKey = (RSAKey) jwkSource.get(selector, null).get(0);
            return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        } catch (Exception ex) {
            throw new IllegalStateException("初始化 JWT 解码器失败", ex);
        }
    }

    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(
            JWKSource<SecurityContext> jwkSource,
            OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        jwtGenerator.setJwtCustomizer(jwtCustomizer);
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, new PublicClientRefreshTokenGenerator());
    }

    public static AuthorizationGrantType authorizationCode() {
        return AuthorizationGrantType.AUTHORIZATION_CODE;
    }

    public static ClientAuthenticationMethod none() {
        return ClientAuthenticationMethod.NONE;
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("生成 RS256 密钥失败", ex);
        }
    }
}
