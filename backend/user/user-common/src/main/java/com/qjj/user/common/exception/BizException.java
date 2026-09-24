package com.qjj.user.common.exception;

import com.qjj.user.common.enums.ErrorCode;
import com.qjj.user.common.enums.SysErrorCodeEnum;
import lombok.Getter;

/**
 * 业务/校验失败异常（HTTP 200，语义由 code 表达，CLAUDE.md 5.3）。
 * 禁止吞异常；统一走全局异常处理。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getDesc());
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BizException(ErrorCode errorCode, String msg) {
        this(errorCode.getCode(), msg);
    }

    public static BizException notFound(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException system() {
        return new BizException(SysErrorCodeEnum.SYSTEM_ERROR);
    }
}
