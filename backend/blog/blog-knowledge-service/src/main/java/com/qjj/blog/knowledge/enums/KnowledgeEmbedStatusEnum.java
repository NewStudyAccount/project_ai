package com.qjj.blog.knowledge.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

/**
 * 知识块向量化状态（knowledge_chunk.embed_status；READY = 向量生成 + ES index 成功）
 */
@Getter
public enum KnowledgeEmbedStatusEnum implements BaseEnum<Integer> {

    PENDING(0, "待向量化"),
    VECTORIZING(1, "向量化中"),
    READY(2, "可用"),
    FAILED(3, "失败");

    private final Integer code;

    private final String desc;

    KnowledgeEmbedStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
