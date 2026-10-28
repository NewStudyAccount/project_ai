package com.qjj.blog.knowledge.dto;

import com.qjj.blog.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 服务商分页入参（api-design.md §3：kind? / status?）
 */
@Getter
@Setter
public class AiProviderPageQuery extends PageQuery {

    private Integer kind;

    private Integer status;
}
