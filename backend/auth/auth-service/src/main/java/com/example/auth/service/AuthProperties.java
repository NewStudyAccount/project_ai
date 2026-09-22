package com.example.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证可配置项；禁止散落 @Value。
 */
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** SSO 会话绝对时长（小时） */
    private int sessionTtlHours = 12;

    /** Access Token TTL 秒 */
    private int accessTokenTtlSec = 600;

    /** Refresh Token TTL 秒 */
    private int refreshTokenTtlSec = 604800;

    /** issuer */
    private String issuer = "http://auth.local:8081";

    public int getSessionTtlHours() {
        return sessionTtlHours;
    }

    public void setSessionTtlHours(int sessionTtlHours) {
        this.sessionTtlHours = sessionTtlHours;
    }

    public int getAccessTokenTtlSec() {
        return accessTokenTtlSec;
    }

    public void setAccessTokenTtlSec(int accessTokenTtlSec) {
        this.accessTokenTtlSec = accessTokenTtlSec;
    }

    public int getRefreshTokenTtlSec() {
        return refreshTokenTtlSec;
    }

    public void setRefreshTokenTtlSec(int refreshTokenTtlSec) {
        this.refreshTokenTtlSec = refreshTokenTtlSec;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
