package com.qjj.blog.service.vo;

import lombok.Data;

import java.util.List;

@Data
public class CategoryVO {
    private String id;
    private String parentId;
    private String name;
    private String slug;
    private String description;
    private Integer sort;
    private Integer status;
    private Long articleCount;
    private List<CategoryVO> children;
}
