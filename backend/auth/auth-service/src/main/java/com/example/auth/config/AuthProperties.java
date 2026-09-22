package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证配置。生产密钥通过环境变量/Nacos 注入，禁止写入仓库。
 */
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** JWT 签发方 */
    private String issuer = "auth-service";

    /** HS256 对称密钥（本地/test 可用配置；生产走密钥管理） */
    private String jwtSecret = "change-me-local-dev-secret-not-for-prod";

    /** Access 过期分钟 */
    private long accessTtlMinutes = 30;

    /** Refresh 天 */
    private long refreshTtlDays = 7;

    /** SSO sid 天 */
    private long sidTtlDays = 7;

    /** SSO Cookie 名 */
    private String sidCookieName = "AUTH_SID";

    /** SSO Cookie 域（父域共享用） */
    private String cookieDomain = "";

    /** 登录失败最大次数 */
    private int loginMaxFail = 5;

    /** 失败锁定秒 */
    private int loginLockSeconds = 300;

    /** 允许的 return_url 前缀（逗号分隔） */
    private String returnUrlWhitelist = "http://localhost";

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getAccessTtlMinutes() {
        return accessTtlMinutes;
    }

    public void setAccessTtlMinutes(long accessTtlMinutes) {
        this.accessTtlMinutes = accessTtlMinutes;
    }

    public long getRefreshTtlDays() {
        return refreshTtlDays;
    }

    public void setRefreshTtlDays(long refreshTtlDays) {
        this.refreshTtlDays = refreshTtlDays;
    }

    public long getSidTtlDays() {
        return sidTtlDays;
    }

    public void setSidTtlDays(long sidTtlDays) {
        this.sidTtlDays = sidTtlDays;
    }

    public String getSidCookieName() {
        return sidCookieName;
    }

    public void setSidCookieName(String sidCookieName) {
        this.sidCookieName = sidCookieName;
    }

    public String getCookieDomain() {
        return cookieDomain;
    }

    public void setCookieDomain(String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    public int getLoginMaxFail() {
        return loginMaxFail;
    }

    public void setLoginMaxFail(int loginMaxFail) {
        this.loginMaxFail = loginMaxFail;
    }

    public int getLoginLockSeconds() {
        return loginLockSeconds;
    }

    public void setLoginLockSeconds(int loginLockSeconds) {
        this.loginLockSeconds = loginLockSeconds;
    }

    public String getReturnUrlWhitelist() {
        return returnUrlWhitelist;
    }

    public void setReturnUrlWhitelist(String returnUrlWhitelist) {
        this.returnUrlWhitelist = returnUrlWhitelist;
    }
}
