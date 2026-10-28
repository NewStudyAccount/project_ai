package com.qjj.blog.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 服务商新增/编辑入参（api-design.md §3）。
 * 编辑时 apiKey 空=不修改；dim 必须与 ES index dims 一致（203114）。
 */
@Getter
@Setter
public class AiProviderRequest {

    @NotBlank(message = "code 不能为空")
    @Size(max = 32, message = "code 最长 32")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$", message = "code 仅允许小写字母/数字/中划线")
    private String code;

    @NotNull(message = "kind 不能为空")
    @Min(value = 1, message = "kind 取值 1/2/3")
    @Max(value = 3, message = "kind 取值 1/2/3")
    private Integer kind;

    @NotBlank(message = "type 不能为空")
    private String type;

    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 最长 64")
    private String name;

    @Size(max = 255, message = "baseUrl 最长 255")
    private String baseUrl;

    @NotBlank(message = "model 不能为空")
    @Size(max = 64, message = "model 最长 64")
    private String model;

    @Min(value = 0, message = "dim 不能为负")
    private Integer dim;

    /** 空=编辑时不修改 */
    @Size(max = 512, message = "apiKey 最长 512")
    private String apiKey;

    @Min(value = 0, message = "isDefault 取值 0/1")
    @Max(value = 1, message = "isDefault 取值 0/1")
    private Integer isDefault;

    @Min(value = 0, message = "status 取值 0/1")
    @Max(value = 1, message = "status 取值 0/1")
    private Integer status;
}
