package com.qjj.blog.knowledge.controller;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.common.result.Result;
import com.qjj.blog.knowledge.dto.CandidatePageQuery;
import com.qjj.blog.knowledge.dto.FromArticlesRequest;
import com.qjj.blog.knowledge.service.ArticleIndexService;
import com.qjj.blog.knowledge.service.KnowledgeDocService;
import com.qjj.blog.knowledge.vo.ArticleIndexSyncResultVO;
import com.qjj.blog.knowledge.vo.CandidateSummaryVO;
import com.qjj.blog.knowledge.vo.CandidateVO;
import com.qjj.blog.knowledge.vo.FromArticlesResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 · 文章入库 API（api-design.md §1；权限位 blog:knowledge:ingest，网关鉴权）
 */
@Tag(name = "知识库 · 文章入库", description = "候选列表/统计/同步文章索引/批量入库")
@RestController
@RequestMapping("/api/v1/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeArticleController {

    private final ArticleIndexService articleIndexService;
    private final KnowledgeDocService docService;

    @Operation(summary = "候选文章分页（ingestState/docStatus 两维分离）")
    @GetMapping("/candidates")
    public Result<PageResult<CandidateVO>> candidates(CandidatePageQuery query) {
        return Result.ok(articleIndexService.candidates(query));
    }

    @Operation(summary = "候选统计（四态聚合）")
    @GetMapping("/candidates/summary")
    public Result<CandidateSummaryVO> candidatesSummary() {
        return Result.ok(articleIndexService.candidatesSummary());
    }

    @Operation(summary = "同步文章索引（投影 upsert + 全量覆盖 + 失效同步）")
    @PostMapping("/article-index/sync")
    public Result<ArticleIndexSyncResultVO> sync() {
        return Result.ok(articleIndexService.sync());
    }

    @Operation(summary = "从文章入库（≤100 条/次；受理即返回）")
    @PostMapping("/docs/from-articles")
    public Result<FromArticlesResultVO> fromArticles(@Valid @RequestBody FromArticlesRequest request) {
        return Result.ok(docService.fromArticles(request));
    }
}
