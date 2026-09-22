package com.example.file.common;

/**
 * 业务异常与错误码（1xxxx 系统 / 2xxxx 业务 / 3xxxx 校验）。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BizException typeNotAllowed() {
        return new BizException(20001, "文件类型不在白名单");
    }

    public static BizException tooLarge() {
        return new BizException(20002, "文件大小超过限制");
    }

    public static BizException notFound() {
        return new BizException(20003, "文件不存在");
    }

    public static BizException storage(String msg) {
        return new BizException(20004, msg);
    }

    public static BizException badParam(String msg) {
        return new BizException(30001, msg);
    }
}
