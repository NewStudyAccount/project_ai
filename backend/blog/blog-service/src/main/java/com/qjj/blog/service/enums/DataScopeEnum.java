package com.qjj.blog.service.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

@Getter
public enum DataScopeEnum implements BaseEnum<Integer> {

    ALL(1, "全部"),
    DEPARTMENT(2, "本部门"),
    SELF(3, "仅本人");

    private final Integer code;

    private final String desc;

    DataScopeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
