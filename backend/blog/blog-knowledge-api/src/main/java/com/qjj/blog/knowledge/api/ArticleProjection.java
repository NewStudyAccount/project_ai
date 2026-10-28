package com.qjj.blog.knowledge.api;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 已发布文章投影 DTO（blog-knowledge-base 提案 tasks 5.1；字段对齐 table-design.md §2.7 投影表）。
 * 仅承载投影同步所需字段：候选列表/指纹判定（live_file_id=内容指纹）。
 */
@Getter
@Setter
public class ArticleProjection {

    /** blog_article.id */
    private Long articleId;

    /** 文章标题 */
    private String title;

    /** 分类 id；0=未分类 */
    private Long categoryId;

    /** 分类名快照（未分类为空串） */
    private String categoryName;

    /** 最近发布时间 */
    private LocalDateTime publishTime;

    /** 文章 update_time 快照 */
    private LocalDateTime articleUpdateTime;

    /** 当前 live 正文 sys_file.id（内容指纹；0=无正文） */
    private Long liveFileId;

    /** 线上字数 */
    private Integer wordCount;

    /** 文章 slug（公开详情 URL 组装用：/articles/{slug}） */
    private String slug;
}
