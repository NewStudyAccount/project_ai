package com.qjj.auth.service.config;

import com.qjj.auth.service.security.CredentialAuthenticationProvider;
import com.qjj.auth.service.security.LoginAttemptService;
import com.qjj.auth.service.security.OidcLogoutFilter;
import com.qjj.auth.service.security.OidcRequestValidationFilter;
import com.qjj.auth.service.security.SsoSessionAuthFilter;
import com.qjj.auth.service.security.SsoSessionService;
import com.qjj.auth.service.service.CredentialService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.HashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(AuthSecurityProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        return new DelegatingPasswordEncoder("bcrypt", encoders, "{", "}");
    }

    @Bean
    public AuthenticationProvider credentialAuthenticationProvider(
            CredentialService credentialService, LoginAttemptService loginAttemptService) {
        return new CredentialAuthenticationProvider(credentialService, loginAttemptService);
    }

    @Bean
    public FilterRegistrationBean<OidcRequestValidationFilter> oidcRequestValidationFilter(
            org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clientRepository) {
        FilterRegistrationBean<OidcRequestValidationFilter> registration =
                new FilterRegistrationBean<>(new OidcRequestValidationFilter(clientRepository));
        registration.addUrlPatterns("/oauth2/authorize");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<OidcLogoutFilter> oidcLogoutFilter(
            SsoSessionService ssoSessionService,
            com.qjj.auth.service.service.GrantService grantService,
            AuthSecurityProperties authSecurityProperties) {
        FilterRegistrationBean<OidcLogoutFilter> registration =
                new FilterRegistrationBean<>(new OidcLogoutFilter(ssoSessionService, grantService, authSecurityProperties));
        registration.addUrlPatterns("/connect/logout");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AuthSecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthenticationProvider credentialAuthenticationProvider,
                                                   SsoSessionService ssoSessionService,
                                                   com.qjj.auth.service.service.RbacService rbacService,
                                                   AuthSecurityProperties authSecurityProperties) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(authSecurityProperties)))
                .authenticationProvider(credentialAuthenticationProvider)
                .addFilterAfter(new SsoSessionAuthFilter(ssoSessionService, rbacService),
                        org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/**", "/oauth2/**", "/.well-known/**",
                                "/login", "/api/login", "/connect/logout").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                // JWT 不含权限码：验签后按 sub 从 RBAC 装载 authorities，否则 @PreAuthorize 恒 10002
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new com.qjj.auth.service.security.RbacJwtAuthenticationConverter(rbacService))))
                .formLogin(form -> form
                        // 验密与 GET /login（跳转 portal）分离，避免与页面路由同名
                        .loginProcessingUrl("/api/login")
                        .successHandler(loginSuccessHandler(ssoSessionService))
                        .failureHandler(loginFailureHandler())
                        .permitAll())
                // 必须写在 formLogin 之后：否则 FormLogin 会把 EntryPoint 改回相对路径 /login，GET 9080/login → 10004
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new PortalLoginAuthenticationEntryPoint(authSecurityProperties)))
                .logout(logout -> logout.permitAll());
        return http.build();
    }

    private SavedRequestAwareAuthenticationSuccessHandler loginSuccessHandler(SsoSessionService ssoSessionService) {
        SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(jakarta.servlet.http.HttpServletRequest request,
                                                jakarta.servlet.http.HttpServletResponse response,
                                                org.springframework.security.core.Authentication authentication) throws java.io.IOException, jakarta.servlet.ServletException {
                if (authentication.getPrincipal() instanceof com.qjj.auth.service.security.AuthPrincipal principal) {
                    ssoSessionService.create(principal, request, response);
                }
                super.onAuthenticationSuccess(request, response, authentication);
            }
        };
        handler.setDefaultTargetUrl("http://localhost:5174/login");
        return handler;
    }

    private AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":202001,\"msg\":\"登录失败\",\"data\":null}");
        };
    }
}
