package com.qjj.blog.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文章主表（列表/摘要/状态/归属；正文在 detail 双轨）。
 */
@Getter
@Setter
@TableName("blog_article")
public class BlogArticle extends BaseEntity {

    private String slug;

    private Long categoryId;

    private String title;

    private String summary;

    private Long coverFileId;

    private Long authorId;

    private String authorName;

    private Integer status;

    private LocalDateTime publishTime;

    private Integer isTop;

    private Long viewCount;
}
