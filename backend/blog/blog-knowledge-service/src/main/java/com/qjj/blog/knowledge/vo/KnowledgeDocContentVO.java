package com.qjj.blog.knowledge.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 知识文档原文出参（api-design.md §2.9 KnowledgeDocContentVO）。
 * content 为当前源文 Markdown（非入库快照）；stale = contentVersion ≠ liveVersion。
 */
@Getter
@Setter
public class KnowledgeDocContentVO {

    private String id;

    /** 1 文章 / 2 手动导入 */
    private Integer source;

    /** 文章 id（source=1）；否则 null */
    private String sourceId;

    private String title;

    /** Markdown 源文 */
    private String content;

    /** 入库指纹（最近一次成功向量化的源文指纹） */
    private String contentVersion;

    /** 当前源文指纹 */
    private String liveVersion;

    /** 源文已变更、向量索引待重建 */
    private Boolean stale;

    /** source=1 为 /articles/{slug}；source=2 为 "" */
    private String docUrl;
}
