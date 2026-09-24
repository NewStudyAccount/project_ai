package com.qjj.user.service.enums;

import com.qjj.user.common.enums.BaseEnum;
import lombok.Getter;

@Getter
public enum MenuTypeEnum implements BaseEnum<Integer> {

    DIRECTORY(1, "目录"),
    MENU(2, "菜单"),
    BUTTON(3, "按钮"),
    API(4, "接口");

    private final Integer code;

    private final String desc;

    MenuTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
