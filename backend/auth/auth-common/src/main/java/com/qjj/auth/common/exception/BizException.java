package com.qjj.auth.common.exception;

import com.qjj.auth.common.enums.ErrorCode;
import com.qjj.auth.common.enums.SysErrorCodeEnum;
import lombok.Getter;

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

    public static BizException system() {
        return new BizException(SysErrorCodeEnum.SYSTEM_ERROR);
    }
}
