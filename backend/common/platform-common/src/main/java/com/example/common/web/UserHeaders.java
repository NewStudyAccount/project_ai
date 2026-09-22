package com.example.common.web;

/**
 * 业务系统网关验签后透传的用户头。
 */
public final class UserHeaders {

    private UserHeaders() {
    }

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String AUTHORIZATION = "Authorization";
}
