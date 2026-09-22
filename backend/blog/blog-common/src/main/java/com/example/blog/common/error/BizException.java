package com.example.blog.common.error;

/** 业务异常，携带错误码。 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BizException(ErrorCode errorCode) {
        this(errorCode.code(), errorCode.desc());
    }

    public BizException(ErrorCode errorCode, String msg) {
        this(errorCode.code(), msg);
    }

    public int getCode() {
        return code;
    }

    public static BizException badParam(String msg) {
        return new BizException(CommonErrors.BAD_PARAM, msg == null ? CommonErrors.BAD_PARAM.desc() : msg);
    }

    public static BizException notFound() {
        return new BizException(CommonErrors.NOT_FOUND);
    }

    public static BizException unauthorized() {
        return new BizException(CommonErrors.UNAUTHORIZED);
    }

    public static BizException forbidden() {
        return new BizException(CommonErrors.FORBIDDEN);
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }
}
