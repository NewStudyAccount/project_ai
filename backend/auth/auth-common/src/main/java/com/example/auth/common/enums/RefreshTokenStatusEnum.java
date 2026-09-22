package com.example.auth.common.enums;

import com.example.auth.common.BaseEnum;

/**
 * Refresh Token 状态。
 */
public enum RefreshTokenStatusEnum implements BaseEnum<Integer> {
    ACTIVE(1, "有效"),
    ROTATED(0, "已轮转作废"),
    REVOKED(2, "已吊销");

    private final Integer code;
    private final String desc;

    RefreshTokenStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
