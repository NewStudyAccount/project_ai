package com.qjj.blog.common.enums;

import lombok.Getter;

/**
 * blog 业务错误码（业务段 2xxxxx = 2 + 系统号 03 + 业务序号，系统号登记见 docs/error-code-ranges.md §2）。
 * 新增业务码先在本系统序号内分配，禁止跨系统同码不同义。
 */
@Getter
public enum BlogErrorCodeEnum implements ErrorCode {

    MENU_PERMISSION_INVALID(203010, "权限标识非法（首段必须为 blog）"),
    MENU_PERMISSION_DUPLICATE(203011, "权限标识已存在"),
    MENU_NODE_NOT_FOUND(203012, "菜单不存在"),
    MENU_HAS_CHILDREN(203013, "存在子节点，禁止删除"),
    MENU_DEPTH_EXCEEDED(203014, "菜单层级过深"),
    MENU_ID_EXHAUSTED(203015, "本层/本段 id 已用尽"),
    MENU_PARENT_INVALID(203016, "父节点非法（按钮/接口必须挂在菜单下）"),
    ROLE_CODE_EXISTS(203020, "角色编码已存在"),
    ROLE_NOT_FOUND(203021, "角色不存在"),
    FILE_NOT_FOUND(203030, "文件不存在"),
    FILE_TYPE_NOT_ALLOWED(203031, "文件类型不在白名单"),
    FILE_TOO_LARGE(203032, "文件超过大小限制"),
    FILE_UPLOAD_FAILED(203033, "文件上传失败"),
    DUPLICATE_REQUEST_IN_PROGRESS(203900, "请求处理中，请勿重复提交");

    private final int code;

    private final String desc;

    BlogErrorCodeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
