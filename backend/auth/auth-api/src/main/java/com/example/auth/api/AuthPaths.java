package com.example.auth.api;

/**
 * 认证与令牌契约常量路径。
 */
public final class AuthPaths {

    private AuthPaths() {
    }

    public static final String PREFIX = "/auth";
    public static final String LOGIN = PREFIX + "/login";
    public static final String REFRESH = PREFIX + "/refresh";
    public static final String LOGOUT = PREFIX + "/logout";
    public static final String SSO_AUTHORIZE = PREFIX + "/sso/authorize";
    public static final String SSO_TOKEN = PREFIX + "/sso/token";
    public static final String USERS = PREFIX + "/users";
    public static final String USERS_BATCH = PREFIX + "/users/batch";
    public static final String ACCOUNT_PASSWORD = PREFIX + "/account/password";
    public static final String ACCOUNT_STATUS = PREFIX + "/account/status";
}
