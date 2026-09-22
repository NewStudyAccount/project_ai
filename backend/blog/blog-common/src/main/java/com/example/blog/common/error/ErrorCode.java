package com.example.blog.common.error;

/** 错误码契约：1xxxx 系统 / 2xxxx 业务 / 3xxxx 校验。 */
public interface ErrorCode {

    int code();

    String desc();
}
