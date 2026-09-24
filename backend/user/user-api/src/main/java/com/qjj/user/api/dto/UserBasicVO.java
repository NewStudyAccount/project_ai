package com.qjj.user.api.dto;

import lombok.Data;

/**
 * 用户基础资料投影。id 按全局契约序列化为 String。
 */
@Data
public class UserBasicVO {

    private String id;

    private String username;

    private String realName;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    private Integer status;

    private String remark;
}
