package com.example.auth.common;

/**
 * 业务异常；禁止吞异常，统一由全局异常处理转换。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.msg());
        this.code = errorCode.code();
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BizException(ErrorCode errorCode, String msg) {
        super(msg);
        this.code = errorCode.code();
    }

    public int getCode() {
        return code;
    }
}
