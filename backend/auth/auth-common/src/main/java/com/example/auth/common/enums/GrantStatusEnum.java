package com.example.auth.common.enums;

import com.example.auth.common.BaseEnum;

/**
 * 授权台账状态。
 */
public enum GrantStatusEnum implements BaseEnum<Integer> {
    ACTIVE(1, "活跃"),
    REVOKED(0, "吊销");

    private final Integer code;
    private final String desc;

    GrantStatusEnum(Integer code, String desc) {
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
