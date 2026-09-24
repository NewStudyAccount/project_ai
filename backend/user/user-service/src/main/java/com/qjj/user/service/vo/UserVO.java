package com.qjj.user.service.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private String id;

    private String username;

    private String realName;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    private Integer status;

    private String remark;

    private Integer gender;

    private LocalDateTime birthday;

    private String address;

    private String extraJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
