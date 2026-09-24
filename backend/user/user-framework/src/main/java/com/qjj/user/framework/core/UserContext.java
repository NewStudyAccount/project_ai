package com.qjj.user.framework.core;

import com.qjj.user.common.constants.CommonConstants;
import lombok.Getter;
import lombok.Setter;

/**
 * 当前操作者上下文：user_id 以网关注入的 X-User-Id（已验签令牌 sub）为准，不信客户端头（CLAUDE.md 5.2）。
 */
@Getter
@Setter
public class UserContext {

    private Long userId;

    private String userName;

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    public static void set(UserContext ctx) {
        HOLDER.set(ctx);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    /** 当前操作者 id（无则 0，兼容系统动作） */
    public static Long userIdOrSystem() {
        UserContext ctx = HOLDER.get();
        return ctx == null || ctx.getUserId() == null ? 0L : ctx.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static final String HEADER_USER_ID = CommonConstants.HEADER_USER_ID;
    public static final String HEADER_USER_NAME = CommonConstants.HEADER_USER_NAME;
}
