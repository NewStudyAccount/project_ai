package com.qjj.user.common.enums;

import lombok.Getter;

/**
 * 系统段 1xxxx / 校验段 3xxxx 固定码值（唯一登记处 docs/error-code-ranges.md §1，各系统原样对齐）。
 */
@Getter
public enum SysErrorCodeEnum implements ErrorCode {

    SUCCESS(0, "成功"),
    UNAUTHENTICATED(10001, "未认证"),
    FORBIDDEN(10002, "无权限"),
    RATE_LIMITED(10003, "请求过于频繁"),
    SYSTEM_ERROR(10004, "系统异常"),
    VALIDATION_FAILED(30001, "参数校验失败");

    private final int code;

    private final String desc;

    SysErrorCodeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
