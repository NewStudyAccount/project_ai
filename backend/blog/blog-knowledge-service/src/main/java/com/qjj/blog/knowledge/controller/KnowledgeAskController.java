package com.qjj.blog.knowledge.controller;

import com.qjj.blog.common.enums.SysErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.common.result.Result;
import com.qjj.blog.framework.idempotent.RateLimit;
import com.qjj.blog.knowledge.dto.AskRequest;
import com.qjj.blog.knowledge.es.KnowledgeChunkIndexService;
import com.qjj.blog.knowledge.service.KnowledgeAskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端 · 问知识库（api-design.md §4；登录态，网关 JWT；无匿名通道，不入 public-paths）。
 * SSE 契约：流开始前错误返回 JSON Result（401/403/429/503/30001，本控制器直写响应体保证状态码语义）；
 * 流开始后 meta/delta/refs/done/error 帧 + 15s 心跳注释帧。
 */
@Slf4j
@Tag(name = "知识库 · 问答", description = "RAG 流式问答（SSE）")
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeAskController {

    private final KnowledgeAskService askService;

    @Operation(summary = "问知识库（SSE；限流 5 次/分钟/用户 + 20 次/天/用户）")
    @PostMapping("/ask")
    @RateLimit(keyPrefix = "knowledge-ask", permits = 5, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public void ask(@Valid @RequestBody AskRequest request, HttpServletResponse response) throws Exception {
        // 流开始前阶段（同步；异常直写 JSON，保证 429/503 状态码语义）
        try {
            askService.checkDailyQuota();
            String sessionId = askService.resolveSession(request.getSessionId());
            List<KnowledgeChunkIndexService.EsSearchHit> hits = askService.search(request.getQuestion(),
                    sessionId, response);
            askService.stream(request, sessionId, hits, response);
        } catch (BizException e) {
            writeJsonError(response, e);
        }
    }

    /** 流开始前错误：JSON Result（429/503 → HTTP 状态码；其余 200 + code） */
    private void writeJsonError(HttpServletResponse response, BizException e) throws Exception {
        int httpStatus = HttpStatus.OK.value();
        if (e.getCode() == SysErrorCodeEnum.RATE_LIMITED.getCode()) {
            httpStatus = HttpStatus.TOO_MANY_REQUESTS.value();
        } else if (e.getCode() == com.qjj.blog.common.enums.BlogErrorCodeEnum.KNOWLEDGE_SEARCH_UNAVAILABLE.getCode()
                || e.getCode() == com.qjj.blog.common.enums.BlogErrorCodeEnum.KNOWLEDGE_EMBED_FAILED.getCode()) {
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE.value();
        }
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(Result.fail(e.getCode(), e.getMessage())));
    }
}
