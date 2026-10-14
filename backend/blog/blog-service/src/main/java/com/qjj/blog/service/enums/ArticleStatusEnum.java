package com.qjj.blog.service.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

/**
 * 文章状态（docs/blog-system/table-design.md §2.3）。
 */
@Getter
public enum ArticleStatusEnum implements BaseEnum<Integer> {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布"),
    OFFLINE(2, "已下架");

    private final Integer code;

    private final String desc;

    ArticleStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
