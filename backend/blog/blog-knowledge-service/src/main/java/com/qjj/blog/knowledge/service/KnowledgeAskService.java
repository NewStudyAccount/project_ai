package com.qjj.blog.knowledge.service;

import com.qjj.blog.knowledge.dto.AskRequest;
import com.qjj.blog.knowledge.es.KnowledgeChunkIndexService;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * RAG 流式问答（api-design.md §4：SSE 帧格式契约；限流 5/min + 20/day；会话落库）
 */
public interface KnowledgeAskService {

    /** 日配额（20 次/天/用户）校验：超限抛 429·10003「今日配额已用尽」 */
    void checkDailyQuota();

    /** 会话解析/新建（流开始前调用；sessionId 非法或不存在则新建） */
    String resolveSession(String sessionId);

    /** 流开始前阶段（请求线程同步执行）：会话解析 + 问题向量化 + ES 混合检索；失败抛 BizException（503/30001） */
    List<KnowledgeChunkIndexService.EsSearchHit> search(String question, String sessionId, HttpServletResponse response);

    /** SSE 流式问答：meta/delta/refs/done/error 帧 + 15s 心跳注释帧；结束后写会话/消息 */
    void stream(AskRequest request, String sessionIdResolved, List<KnowledgeChunkIndexService.EsSearchHit> hits,
                HttpServletResponse response);
}
