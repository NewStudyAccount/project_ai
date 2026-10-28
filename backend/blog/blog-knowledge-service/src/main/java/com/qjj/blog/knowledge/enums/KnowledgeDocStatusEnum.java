package com.qjj.blog.knowledge.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

/**
 * 知识文档状态（knowledge_doc.status；api-design.md §0.2 docStatus，与 ES doc_status 严格同源）
 */
@Getter
public enum KnowledgeDocStatusEnum implements BaseEnum<Integer> {

    PENDING(0, "待向量化"),
    VECTORIZING(1, "向量化中"),
    READY(2, "可用"),
    FAILED(3, "失败"),
    INVALID(4, "已失效");

    private final Integer code;

    private final String desc;

    KnowledgeDocStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
