package com.qjj.blog.knowledge.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 知识文档出参（api-design.md §2.1 KnowledgeDocVO）
 */
@Getter
@Setter
public class KnowledgeDocVO {

    private String id;

    /** 1 文章 / 2 手动导入 */
    private Integer source;

    /** 文章 id（source=1）；否则 null */
    private String sourceId;

    private String title;

    /** 逗号分隔 */
    private String tags;

    private Integer status;

    private Integer chunkCount;

    /** embed_status=2 块数（向量健康度 = ready/chunkCount） */
    private Integer readyChunkCount;

    private String failReason;

    private String embedModel;

    private Integer embedDim;

    private String contentVersion;

    /** 文章公开 URL；手动导入为 "" */
    private String docUrl;

    private String updateTime;
}
