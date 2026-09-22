package com.example.blog.common.error;

/** 通用错误码（blog 业务码放业务侧常量，勿塞满本枚举）。 */
public enum CommonErrors implements ErrorCode {

    SYSTEM_BUSY(10001, "系统繁忙，请稍后重试"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    BAD_PARAM(30001, "参数不合法"),
    NOT_FOUND(20001, "资源不存在");

    private final int code;
    private final String desc;

    CommonErrors(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String desc() {
        return desc;
    }
}
