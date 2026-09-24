package com.qjj.user.service.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuVO {

    private String id;

    private String parentId;

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

    private List<MenuVO> children = new ArrayList<>();
}
