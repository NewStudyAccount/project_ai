package com.qjj.user.common.result;

import com.qjj.user.common.enums.ErrorCode;
import com.qjj.user.common.enums.SysErrorCodeEnum;
import lombok.Getter;

/**
 * 统一返回体（CLAUDE.md 6.4.1）：{code, msg, data}
 */
@Getter
public class Result<T> {

    private int code;

    private String msg;

    private T data;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = SysErrorCodeEnum.SUCCESS.getCode();
        r.msg = SysErrorCodeEnum.SUCCESS.getDesc();
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getDesc());
    }

    public static <T> Result<T> fail(int code, String msg) {
        return fail(code, msg, null);
    }

    public static <T> Result<T> fail(int code, String msg, T data) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        r.data = data;
        return r;
    }
}
