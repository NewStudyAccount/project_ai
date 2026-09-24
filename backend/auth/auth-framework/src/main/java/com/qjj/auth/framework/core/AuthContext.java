package com.qjj.auth.framework.core;

import com.qjj.auth.common.constants.CommonConstants;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthContext {
    private Long userId;
    private String userName;
    private static final ThreadLocal<AuthContext> HOLDER = new ThreadLocal<>();

    public static void set(AuthContext ctx) {
        HOLDER.set(ctx);
    }

    public static AuthContext get() {
        return HOLDER.get();
    }

    public static Long userIdOrSystem() {
        AuthContext ctx = HOLDER.get();
        return ctx == null || ctx.getUserId() == null ? 0L : ctx.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static final String HEADER_USER_ID = CommonConstants.HEADER_USER_ID;
    public static final String HEADER_USER_NAME = CommonConstants.HEADER_USER_NAME;
}
