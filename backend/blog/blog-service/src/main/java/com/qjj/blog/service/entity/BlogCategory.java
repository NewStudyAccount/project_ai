package com.qjj.blog.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章分类（树，应用层校验最多 2 级）。
 */
@Getter
@Setter
@TableName("blog_category")
public class BlogCategory extends BaseEntity {

    private Long parentId;

    private String name;

    private String slug;

    private String description;

    private Integer sort;

    private Integer status;
}
