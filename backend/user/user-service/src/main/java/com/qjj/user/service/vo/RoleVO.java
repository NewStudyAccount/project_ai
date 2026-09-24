package com.qjj.user.service.vo;

import lombok.Data;

import java.util.List;

@Data
public class RoleVO {

    private String id;

    private String roleCode;

    private String roleName;

    private Integer dataScope;

    private Integer sort;

    private Integer status;

    private String remark;

    private List<String> menuIds;
}
