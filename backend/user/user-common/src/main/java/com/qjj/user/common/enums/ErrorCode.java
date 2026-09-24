package com.qjj.user.common.enums;

/**
 * 错误码契约：code + 文案，统一供 Result/异常使用。
 */
public interface ErrorCode {

    int getCode();

    String getDesc();
}
