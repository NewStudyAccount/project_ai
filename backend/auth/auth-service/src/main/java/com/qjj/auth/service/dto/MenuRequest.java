package com.qjj.auth.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MenuRequest {
    @NotNull
    private Long parentId = 0L;
    @NotNull
    private Integer type;
    @NotBlank
    private String name;
    private String permission = "";
    private String path = "";
    private String component = "";
    private String icon = "";
    private Integer hidden = 0;
    private Integer requiresAuth = 1;
    private Integer sort = 0;
    private Integer status = 1;
}
