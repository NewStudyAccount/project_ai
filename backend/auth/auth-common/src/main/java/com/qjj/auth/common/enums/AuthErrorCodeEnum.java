package com.qjj.auth.common.enums;

import lombok.Getter;

@Getter
public enum AuthErrorCodeEnum implements ErrorCode {
    LOGIN_FAILED(202001, "登录失败"),
    ACCOUNT_DISABLED(202002, "账号不可用"),
    CLIENT_NOT_FOUND(202003, "客户端不存在"),
    CLIENT_ID_EXISTS(202004, "客户端标识已存在"),
    CLIENT_SECRET_REQUIRED(202005, "机密客户端必须设置密钥"),
    INVALID_REDIRECT_URI(202006, "redirect_uri 不在白名单"),
    INVALID_TOKEN(202007, "令牌无效"),
    REFRESH_TOKEN_REUSED(202008, "Refresh Token 已被轮转"),
    GRANT_NOT_FOUND(202009, "授权不存在"),
    MENU_PERMISSION_INVALID(202010, "权限标识非法"),
    MENU_PERMISSION_DUPLICATE(202011, "权限标识已存在"),
    MENU_NODE_NOT_FOUND(202012, "菜单不存在"),
    MENU_HAS_CHILDREN(202013, "存在子节点，禁止删除"),
    ROLE_CODE_EXISTS(202014, "角色编码已存在"),
    ROLE_NOT_FOUND(202015, "角色不存在"),
    DUPLICATE_REQUEST_IN_PROGRESS(202900, "请求处理中，请勿重复提交");

    private final int code;
    private final String desc;

    AuthErrorCodeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
