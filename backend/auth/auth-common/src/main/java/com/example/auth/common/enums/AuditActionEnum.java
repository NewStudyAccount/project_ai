package com.example.auth.common.enums;

import com.example.auth.common.BaseEnum;

/**
 * 安全审计动作。
 */
public enum AuditActionEnum implements BaseEnum<String> {
    LOGIN("LOGIN", "登录"),
    LOGIN_FAIL("LOGIN_FAIL", "登录失败"),
    TOKEN_ISSUE("TOKEN_ISSUE", "签发令牌"),
    REFRESH("REFRESH", "刷新令牌"),
    REVOKE("REVOKE", "吊销"),
    CLIENT_CREATE("CLIENT_CREATE", "创建客户端"),
    CLIENT_UPDATE("CLIENT_UPDATE", "更新客户端"),
    CLIENT_SECRET_RESET("CLIENT_SECRET_RESET", "重置密钥"),
    KICK_OFFLINE("KICK_OFFLINE", "踢下线");

    private final String code;
    private final String desc;

    AuditActionEnum(String code, String desc) {
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
