package com.qjj.user.common.enums;

import lombok.Getter;

/**
 * 用户中心业务错误码（业务段 2xxxxx = 2 + 系统号 01 + 业务序号，系统号登记见 docs/error-code-ranges.md §2）。
 * 新增业务码先在本系统序号内分配，禁止跨系统同码不同义。
 */
@Getter
public enum UserErrorCodeEnum implements ErrorCode {

    USERNAME_EXISTS(201001, "用户名已存在"),
    USER_NOT_FOUND(201002, "用户不存在"),
    USER_STATUS_INVALID(201003, "用户状态不允许该操作"),
    MENU_PERMISSION_INVALID(201010, "权限标识非法（首段必须为 user）"),
    MENU_PERMISSION_DUPLICATE(201011, "权限标识已存在"),
    MENU_NODE_NOT_FOUND(201012, "菜单不存在"),
    MENU_HAS_CHILDREN(201013, "存在子节点，禁止删除"),
    ROLE_CODE_EXISTS(201020, "角色编码已存在"),
    ROLE_NOT_FOUND(201021, "角色不存在"),
    DUPLICATE_REQUEST_IN_PROGRESS(201900, "请求处理中，请勿重复提交");

    private final int code;

    private final String desc;

    UserErrorCodeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
