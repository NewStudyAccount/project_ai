package com.qjj.blog.service.vo;

import lombok.Data;

@Data
public class TagVO {
    private String id;
    private String name;
    private String slug;
    private Integer sort;
    private Integer status;
    private Long articleCount;
}
