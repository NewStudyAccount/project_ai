package com.example.auth.common.enums;

import com.example.auth.common.BaseEnum;

/**
 * 凭证类型；首期仅 PASSWORD。
 */
public enum CredentialTypeEnum implements BaseEnum<String> {
    PASSWORD("PASSWORD", "密码"),
    SMS("SMS", "短信"),
    EMAIL("EMAIL", "邮箱"),
    TOTP("TOTP", "动态口令");

    private final String code;
    private final String desc;

    CredentialTypeEnum(String code, String desc) {
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
