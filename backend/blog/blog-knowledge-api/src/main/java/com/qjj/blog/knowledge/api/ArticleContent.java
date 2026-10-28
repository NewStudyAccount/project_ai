package com.qjj.blog.knowledge.api;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文章正文 + 元数据 DTO（blog-knowledge-base 提案 tasks 5.1②；切块入库的完整回拉形态）。
 */
@Getter
@Setter
public class ArticleContent {

    /** blog_article.id */
    private Long articleId;

    private String title;

    /** 文章 slug（doc_url=/articles/{slug}） */
    private String slug;

    private Long categoryId;

    private String categoryName;

    /** 标签名列表（blog_article_tag → blog_tag） */
    private List<String> tags = new ArrayList<>();

    private LocalDateTime publishTime;

    /** 文章 update_time 快照 */
    private LocalDateTime articleUpdateTime;

    /** 当前 live 正文 sys_file.id（内容指纹） */
    private Long liveFileId;

    private Integer wordCount;

    /** Markdown 正文（live 轨；经 ArticleContentStorageService.readContent 回源） */
    private String content;
}
