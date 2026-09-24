package com.qjj.user.api.dto;

import lombok.Data;

/**
 * OIDC / 展示用资料投影。手机号、邮箱由服务端脱敏。
 */
@Data
public class UserProfileVO {

    private String id;

    private String username;

    private String realName;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    private Integer gender;

    private String birthday;

    private String address;

    private String extraJson;
}
