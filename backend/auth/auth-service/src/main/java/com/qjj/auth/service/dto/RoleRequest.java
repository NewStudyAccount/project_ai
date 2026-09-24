package com.qjj.auth.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleRequest {
    @NotBlank
    private String roleCode;
    private String roleName = "";
    private Integer dataScope = 1;
    private Integer sort = 0;
    private Integer status = 1;
    private String remark = "";
}
