package com.qjj.blog.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识文档（knowledge_doc；table-design.md §2.0）。
 * 唯一键语义：source=1 复用既有 doc 行（UPDATE）；source=2/3 source_id=NULL。
 */
@Getter
@Setter
@TableName("knowledge_doc")
public class KnowledgeDoc extends KnowledgeBaseEntity {

    /** 1=文章 / 2=手动导入 / 3=文件导入(P1) */
    private Integer source;

    /** source=1 时=blog_article.id；source=2/3 时为 NULL */
    private Long sourceId;

    /** 知识标题 */
    private String title;

    /** 内容版本指纹：文章=live sys_file.id；手动=knowledge_file.id */
    private String contentVersion;

    /** source=2/3 时 → knowledge_file.id */
    private Long fileId;

    /** 标签（逗号分隔） */
    private String tags;

    /** 0 PENDING / 1 VECTORIZING / 2 READY / 3 FAILED / 4 INVALID */
    private Integer status;

    /** 切块数（READY 口径） */
    private Integer chunkCount;

    /** doc 级失败原因 */
    private String failReason;

    /** embedding 模型（重建后覆盖；与 ES dims 校验） */
    private String embedModel;

    /** 向量维度 */
    private Integer embedDim;
}
