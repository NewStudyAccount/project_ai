package com.qjj.auth.service.config;

import com.qjj.auth.service.security.CredentialAuthenticationProvider;
import com.qjj.auth.service.security.LoginAttemptService;
import com.qjj.auth.service.security.OidcRequestValidationFilter;
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
                                                   AuthSecurityProperties authSecurityProperties) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(authSecurityProperties)))
                .authenticationProvider(credentialAuthenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/**", "/oauth2/**", "/.well-known/**", "/login").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .successHandler(loginSuccessHandler(ssoSessionService))
                        .failureHandler(loginFailureHandler())
                        .permitAll())
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
        handler.setDefaultTargetUrl("/");
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
