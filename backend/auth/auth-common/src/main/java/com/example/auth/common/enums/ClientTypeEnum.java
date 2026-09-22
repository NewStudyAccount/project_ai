package com.example.auth.common.enums;

import com.example.auth.common.BaseEnum;

/**
 * OAuth 客户端类型。
 */
public enum ClientTypeEnum implements BaseEnum<String> {
    PUBLIC("PUBLIC", "公开客户端"),
    CONFIDENTIAL("CONFIDENTIAL", "机密客户端");

    private final String code;
    private final String desc;

    ClientTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
