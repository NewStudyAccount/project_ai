package com.qjj.user.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("sys_user_profile")
public class SysUserProfile extends BaseEntity {

    private Long userId;

    private Integer gender;

    private LocalDateTime birthday;

    private String address;

    private String extraJson;
}
