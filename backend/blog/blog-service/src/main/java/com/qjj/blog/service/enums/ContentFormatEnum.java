package com.qjj.blog.service.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

/**
 * 正文格式。
 */
@Getter
public enum ContentFormatEnum implements BaseEnum<String> {

    MARKDOWN("markdown", "Markdown"),
    HTML("html", "HTML");

    private final String code;

    private final String desc;

    ContentFormatEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static boolean supports(String format) {
        return MARKDOWN.code.equals(format) || HTML.code.equals(format);
    }
}
