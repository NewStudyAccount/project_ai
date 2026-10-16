package com.qjj.blog.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章标签（扁平）。
 */
@Getter
@Setter
@TableName("blog_tag")
public class BlogTag extends BaseEntity {

    private String name;

    private String slug;

    private Integer sort;

    private Integer status;
}
