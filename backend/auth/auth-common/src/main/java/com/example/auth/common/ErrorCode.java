package com.example.auth.common;

/**
 * 错误码契约：1xxxx 系统、2xxxx 业务、3xxxx 参数校验。
 */
public enum ErrorCode {
    SYSTEM_ERROR(10000, "系统异常"),
    USER_CENTER_UNAVAILABLE(10001, "用户中心暂不可用"),
    UNAUTHORIZED(10002, "未认证"),
    FORBIDDEN(10003, "无权限"),

    BIZ_ERROR(20000, "业务异常"),
    CREDENTIAL_NOT_FOUND(20001, "凭证不存在"),
    CREDENTIAL_INVALID(20002, "用户名或密码错误"),
    ACCOUNT_DISABLED(20003, "账号不可用"),
    LOGIN_LOCKED(20004, "尝试过于频繁，请稍后再试"),
    CLIENT_NOT_FOUND(20005, "客户端不存在"),
    CLIENT_ID_EXISTS(20006, "client_id 已存在"),
    CLIENT_DISABLED(20007, "客户端已停用"),
    TOKEN_REVOKED(20008, "令牌已吊销"),
    SESSION_EXPIRED(20009, "会话已过期"),

    VALIDATION_ERROR(30000, "参数校验失败");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int code() {
        return code;
    }

    public String msg() {
        return msg;
    }
}
