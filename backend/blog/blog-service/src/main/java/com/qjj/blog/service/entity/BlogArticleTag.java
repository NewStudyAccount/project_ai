package com.qjj.blog.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章-标签关联（保存时整组替换）。
 */
@Getter
@Setter
@TableName("blog_article_tag")
public class BlogArticleTag extends BaseEntity {

    private Long articleId;

    private Long tagId;
}
