package com.qjj.blog.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章详细表：正文双轨文件引用（content_file_id / draft_file_id），不存正文。
 */
@Getter
@Setter
@TableName("blog_article_detail")
public class BlogArticleDetail extends BaseEntity {

    private Long articleId;

    private Long contentFileId;

    private Long draftFileId;

    private String contentFormat;

    private Integer wordCount;

    private String draftContentFormat;

    private Integer draftWordCount;

    private String seoTitle;

    private String seoDescription;

    private String draftSeoTitle;

    private String draftSeoDescription;
}
