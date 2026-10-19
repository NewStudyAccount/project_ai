package com.qjj.blog.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "服务商启停入参")
public class StorageProviderStatusRequest {

    @Schema(description = "0 停用 / 1 启用", example = "1")
    @NotNull(message = "status 不能为空")
    private Integer status;
}