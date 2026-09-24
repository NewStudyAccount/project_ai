package com.qjj.user.service.enums;

import com.qjj.user.common.enums.BaseEnum;
import lombok.Getter;

@Getter
public enum UserStatusEnum implements BaseEnum<Integer> {

    DISABLED(0, "停用"),
    NORMAL(1, "正常"),
    LOCKED(2, "锁定");

    private final Integer code;

    private final String desc;

    UserStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
