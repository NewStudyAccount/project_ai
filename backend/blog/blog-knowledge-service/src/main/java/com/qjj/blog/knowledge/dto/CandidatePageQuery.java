package com.qjj.blog.knowledge.dto;

import com.qjj.blog.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 候选文章分页入参（api-design.md §1.1；orderBy 白名单服务端校验）
 */
@Getter
@Setter
public class CandidatePageQuery extends PageQuery {

    /** 文章标题模糊 */
    private String keyword;

    private Long categoryId;

    /** UNINGESTED | INGESTED | STALE | INVALID；空=全部 */
    private String ingestState;

    /** 白名单：publish_time（默认）| article_update_time */
    private String orderBy;

    /** asc | desc（默认 desc） */
    private String order;
}
