package com.qjj.blog.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 已发布文章只读投影（knowledge_article_index；table-design.md §2.7）。
 * 候选列表/筛选/统计在 knowledge 单库内完成；blog 侧只读契约供数。
 */
@Getter
@Setter
@TableName("knowledge_article_index")
public class KnowledgeArticleIndex extends KnowledgeBaseEntity {

    /** blog_article.id */
    private Long articleId;

    /** 文章标题快照 */
    private String title;

    /** 分类 id */
    private Long categoryId;

    /** 分类名快照 */
    private String categoryName;

    /** 最近发布时间 */
    private LocalDateTime publishTime;

    /** 文章 update_time 快照 */
    private LocalDateTime articleUpdateTime;

    /** 当前 live 正文 sys_file.id（内容指纹） */
    private Long liveFileId;

    /** 线上字数 */
    private Integer wordCount;

    /** 本行同步时间 */
    private LocalDateTime syncedAt;
}
