package com.qjj.blog.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagRequest {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 64, message = "标签名最长 64")
    private String name;

    @NotBlank(message = "slug 不能为空")
    @Size(max = 64, message = "slug 最长 64")
    private String slug;

    private Integer sort;

    private Integer status;
}
