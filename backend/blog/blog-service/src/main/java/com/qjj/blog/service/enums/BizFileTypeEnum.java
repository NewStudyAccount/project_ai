package com.qjj.blog.service.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

/**
 * sys_file.biz_type 对象用途（docs/blog-system/table-design.md §2.0）。
 */
@Getter
public enum BizFileTypeEnum implements BaseEnum<String> {

    FILE("FILE", "通用附件"),
    ARTICLE_COVER("ARTICLE_COVER", "文章封面"),
    ARTICLE_IMAGE("ARTICLE_IMAGE", "正文插图"),
    ARTICLE_LIVE("ARTICLE_LIVE", "线上正文"),
    ARTICLE_DRAFT("ARTICLE_DRAFT", "草稿正文");

    private final String code;

    private final String desc;

    BizFileTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static BizFileTypeEnum fromCode(String code) {
        for (BizFileTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return FILE;
    }
}
