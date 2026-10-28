package com.qjj.blog.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识对象登记（knowledge_file；table-design.md §2.2；手动导入源文，不混登记 sys_file）
 */
@Getter
@Setter
@TableName("knowledge_file")
public class KnowledgeFile extends KnowledgeBaseEntity {

    /** 桶（共用 blog 桶） */
    private String bucket;

    /** knowledge/{docId}/{uuid}.md */
    private String objectKey;

    /** 上传原始文件名 */
    private String originalName;

    /** text/markdown */
    private String mimeType;

    /** 字节数（展示用） */
    private Long fileSize;

    /** KNOWLEDGE_SOURCE */
    private String bizType;

    /** → knowledge_doc.id */
    private Long bizId;

    /** 对齐 sys_storage_provider.code / sys_file.storage_code */
    private String storageCode;
}
