package com.example.auth.common;

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

    public static BizException auth(String msg) {
        return new BizException(20001, msg);
    }

    public static BizException disabled() {
        return new BizException(20002, "账号已停用");
    }

    public static BizException locked() {
        return new BizException(20003, "登录失败次数过多，请稍后再试");
    }

    public static BizException notFound() {
        return new BizException(20004, "用户不存在");
    }

    public static BizException badParam(String msg) {
        return new BizException(30001, msg);
    }
}
