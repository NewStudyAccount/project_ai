package com.qjj.user.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleRequest {

    @NotBlank
    @Size(max = 64)
    private String roleCode;

    @Size(max = 64)
    private String roleName;

    @NotNull
    @Min(1)
    @Max(3)
    private Integer dataScope = 1;

    private Integer sort = 0;

    private Integer status = 1;

    @Size(max = 255)
    private String remark = "";
}
