package com.qjj.auth.common.enums;

import lombok.Getter;

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
