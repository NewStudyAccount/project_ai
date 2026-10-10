package com.qjj.blog.service.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

@Getter
public enum AuditActionEnum implements BaseEnum<String> {

    MENU_CREATE("MENU_CREATE", "创建菜单"),
    MENU_UPDATE("MENU_UPDATE", "更新菜单"),
    MENU_DELETE("MENU_DELETE", "删除菜单"),
    ROLE_CREATE("ROLE_CREATE", "创建角色"),
    ROLE_UPDATE("ROLE_UPDATE", "更新角色"),
    ROLE_DELETE("ROLE_DELETE", "删除角色"),
    ROLE_MENU("ROLE_MENU_UPDATE", "更新角色菜单"),
    ROLE_ASSIGN("ROLE_ASSIGN", "分配用户角色"),
    FILE_UPLOAD("FILE_UPLOAD", "上传文件"),
    FILE_DELETE("FILE_DELETE", "删除文件");

    private final String code;

    private final String desc;

    AuditActionEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
