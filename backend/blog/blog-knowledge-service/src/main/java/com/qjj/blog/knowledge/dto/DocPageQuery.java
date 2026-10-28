package com.qjj.blog.knowledge.dto;

import com.qjj.blog.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识文档分页入参（api-design.md §2.1：keyword/source/status/orderBy 白名单）
 */
@Getter
@Setter
public class DocPageQuery extends PageQuery {

    /** 标题模糊 */
    private String keyword;

    /** 1 文章 / 2 手动导入 */
    private Integer source;

    /** docStatus */
    private Integer status;

    /** 白名单：update_time（默认）| chunk_count */
    private String orderBy;

    /** asc | desc */
    private String order;
}
