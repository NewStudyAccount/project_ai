package com.qjj.user.common.constants;

/**
 * 通用常量（Header 契约见 CLAUDE.md 5.2「身份透传 Header」）。
 */
public final class CommonConstants {

    /** 网关认证后注入的用户主键（服务内以注入值为准，不信客户端） */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 网关注入的登录名/显示名 */
    public static final String HEADER_USER_NAME = "X-User-Name";

    /** 幂等键（缺省用 X-Request-Id，CLAUDE.md 6.11） */
    public static final String HEADER_IDEMPOTENT_KEY = "Idempotent-Key";

    /** 请求标识（幂等缺省键 / 网关可补） */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    /** 逻辑删除：未删 */
    public static final int NOT_DELETED = 0;

    /** 逻辑删除：已删 */
    public static final int DELETED = 1;

    private CommonConstants() {
    }
}
