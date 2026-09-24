package com.qjj.user.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    private String realName;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    private Integer status;

    private Long deptId;

    private LocalDateTime lastLoginTime;

    private String remark;
}
