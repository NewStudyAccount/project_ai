package com.qjj.user.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    private Long parentId;

    private Integer type;

    private String name;

    private String permission;

    private String path;

    private String component;

    private String icon;

    private Integer hidden;

    private Integer requiresAuth;

    private Integer sort;

    private Integer status;
}
