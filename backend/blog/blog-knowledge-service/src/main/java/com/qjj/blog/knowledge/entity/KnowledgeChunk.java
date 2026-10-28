package com.qjj.blog.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识块（knowledge_chunk；table-design.md §2.1）——切块业务真相源，向量在 ES（id=ES 文档 _id）。
 */
@Getter
@Setter
@TableName("knowledge_chunk")
public class KnowledgeChunk extends KnowledgeBaseEntity {

    /** → knowledge_doc.id */
    private Long docId;

    /** 块序号 */
    private Integer seq;

    /** 所属标题链 */
    private String heading;

    /** 块文本（含上下文增强前缀）；同步至 ES content 字段 */
    private String content;

    /** 估算 token 数 */
    private Integer tokenCount;

    /** 0 PENDING / 1 VECTORIZING / 2 READY / 3 FAILED */
    private Integer embedStatus;

    /** 失败原因（provider/ES 错误码） */
    private String failReason;
}
