package com.qjj.blog.knowledge.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

/**
 * 知识文档来源（knowledge_doc.source；api-design.md §0.2）
 */
@Getter
public enum KnowledgeDocSourceEnum implements BaseEnum<Integer> {

    ARTICLE(1, "文章"),
    MANUAL(2, "手动导入"),
    FILE(3, "文件导入(P1)");

    private final Integer code;

    private final String desc;

    KnowledgeDocSourceEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
