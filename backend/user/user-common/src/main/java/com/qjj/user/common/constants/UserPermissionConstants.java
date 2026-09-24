package com.qjj.user.common.constants;

/**
 * 权限标识（CLAUDE.md 5.2：system:resource:action 全小写；首段 = user）。
 * 前端路由/按钮与后端 @PreAuthorize 使用同一字符串。
 */
public final class UserPermissionConstants {

    public static final String USER_LIST = "user:user:list";
    public static final String USER_CREATE = "user:user:create";
    public static final String USER_UPDATE = "user:user:update";
    public static final String USER_STATUS = "user:user:status";

    public static final String MENU_LIST = "user:menu:list";
    public static final String MENU_CREATE = "user:menu:create";
    public static final String MENU_UPDATE = "user:menu:update";
    public static final String MENU_DELETE = "user:menu:delete";

    public static final String ROLE_LIST = "user:role:list";
    public static final String ROLE_CREATE = "user:role:create";
    public static final String ROLE_UPDATE = "user:role:update";
    public static final String ROLE_DELETE = "user:role:delete";
    public static final String ROLE_ASSIGN = "user:role:assign";

    public static final String USER_ROLE_ASSIGN = "user:user:assign-role";

    public static final String AUDIT_LIST = "user:audit:list";

    private UserPermissionConstants() {
    }
}
