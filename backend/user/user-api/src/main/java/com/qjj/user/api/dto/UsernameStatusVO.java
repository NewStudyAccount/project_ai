package com.qjj.user.api.dto;

import lombok.Data;

/**
 * 登录解析契约：只返回 user.id 与账号状态。
 */
@Data
public class UsernameStatusVO {

    private String id;

    private String username;

    private Integer status;
}
