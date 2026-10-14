package com.qjj.blog.service.dto;

import lombok.Data;

/**
 * 文章列表筛选（管理端/用户端）。
 */
@Data
public class ArticlePageQuery extends com.qjj.blog.common.page.PageQuery {

    private String keyword;

    private String categoryId;

    private String tagId;

    private Integer status;

    private String orderBy;

    private String order;
}
