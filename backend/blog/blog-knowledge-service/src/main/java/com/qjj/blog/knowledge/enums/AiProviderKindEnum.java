package com.qjj.blog.knowledge.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

/**
 * AI 服务商类型（ai_provider.kind；api-design.md §0.2/§3）
 */
@Getter
public enum AiProviderKindEnum implements BaseEnum<Integer> {

    EMBEDDING(1, "Embedding"),
    LLM(2, "LLM"),
    COMPATIBLE(3, "兼容");

    private final Integer code;

    private final String desc;

    AiProviderKindEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AiProviderKindEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AiProviderKindEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
