package com.qjj.blog.knowledge.service;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.knowledge.dto.CandidatePageQuery;
import com.qjj.blog.knowledge.vo.ArticleIndexSyncResultVO;
import com.qjj.blog.knowledge.vo.CandidateSummaryVO;
import com.qjj.blog.knowledge.vo.CandidateVO;

import java.time.LocalDateTime;

/**
 * 文章投影/候选服务（api-design.md §1.1–§1.3；table-design.md §2.7）
 */
public interface ArticleIndexService {

    /** 同步文章索引：分页拉取已发布投影 → upsert + 全量覆盖 → 失效同步（INVALIDATE 任务） */
    ArticleIndexSyncResultVO sync();

    PageResult<CandidateVO> candidates(CandidatePageQuery query);

    CandidateSummaryVO candidatesSummary();

    /** 入库成功后增量刷新投影行（VectorizeService.ProjectionRefresher 委托点） */
    void refresh(Long articleId, LocalDateTime articleUpdateTime);
}
