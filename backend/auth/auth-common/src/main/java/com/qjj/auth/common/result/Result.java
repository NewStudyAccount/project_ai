package com.qjj.auth.common.result;

import com.qjj.auth.common.enums.ErrorCode;
import com.qjj.auth.common.enums.SysErrorCodeEnum;
import lombok.Getter;

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
        return fail(errorCode.getCode(), errorCode.getDesc(), null);
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
