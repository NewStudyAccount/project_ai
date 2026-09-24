package com.qjj.auth.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.security")
public class AuthSecurityProperties {
    private List<String> allowedOrigins = new ArrayList<>();
    /** /connect/logout 允许的精确 post_logout_redirect_uri 白名单（不改 oauth_client 表） */
    private List<String> postLogoutRedirectUris = new ArrayList<>();
    /** 唯一登录门面（auth-portal）；未登录 authorize 跳此处 */
    private String loginPortalBase = "http://localhost:5174";
    private int loginMaxFailures = 5;
    private long loginLockSeconds = 900;
    private long ssoSessionTtlSeconds = 43200;

    public boolean isAllowedPostLogoutRedirect(String uri) {
        return uri != null && !uri.isBlank() && postLogoutRedirectUris.contains(uri);
    }
}
