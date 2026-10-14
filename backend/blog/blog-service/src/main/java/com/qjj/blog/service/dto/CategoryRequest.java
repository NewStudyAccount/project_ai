package com.qjj.blog.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {

    private String parentId;

    @NotBlank(message = "分类名不能为空")
    @Size(max = 64, message = "分类名最长 64")
    private String name;

    @NotBlank(message = "slug 不能为空")
    @Size(max = 64, message = "slug 最长 64")
    private String slug;

    @Size(max = 255, message = "描述最长 255")
    private String description;

    private Integer sort;

    private Integer status;
}
