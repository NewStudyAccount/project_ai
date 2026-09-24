package com.qjj.user.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MenuRequest {

    @NotNull
    private Long parentId = 0L;

    @NotNull
    @Min(1)
    @Max(4)
    private Integer type;

    @NotBlank
    @Size(max = 64)
    private String name;

    @Size(max = 128)
    private String permission = "";

    @Size(max = 255)
    private String path = "";

    @Size(max = 255)
    private String component = "";

    @Size(max = 64)
    private String icon = "";

    private Integer hidden = 0;

    private Integer requiresAuth = 1;

    private Integer sort = 0;

    private Integer status = 1;
}
