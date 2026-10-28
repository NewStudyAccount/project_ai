package com.qjj.blog.knowledge.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

/**
 * 知识库任务类型（knowledge_task.task_type；api-design.md §0.2）
 */
@Getter
public enum KnowledgeTaskTypeEnum implements BaseEnum<String> {

    INGEST("INGEST", "入库+向量化"),
    REVECTORIZE("REVECTORIZE", "单文档重建"),
    INVALIDATE("INVALIDATE", "下架/删除同步"),
    FULL_REINDEX("FULL_REINDEX", "全量重建"),
    SYNC_ARTICLES("SYNC_ARTICLES", "对账补偿");

    private final String code;

    private final String desc;

    KnowledgeTaskTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
