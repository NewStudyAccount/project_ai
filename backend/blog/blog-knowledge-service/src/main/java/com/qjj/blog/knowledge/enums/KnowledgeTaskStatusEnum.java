package com.qjj.blog.knowledge.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

/**
 * 知识库任务状态（knowledge_task.status；api-design.md §0.2 taskStatus）
 */
@Getter
public enum KnowledgeTaskStatusEnum implements BaseEnum<Integer> {

    PENDING(0, "待执行"),
    RUNNING(1, "执行中"),
    SUCCESS(2, "成功"),
    FAILED(3, "失败"),
    DEAD(4, "超重试上限");

    private final Integer code;

    private final String desc;

    KnowledgeTaskStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
