package com.qjj.blog.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qjj.blog.common.enums.SysErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.framework.concurrent.KnowledgeExecutorConfiguration;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.core.UserContext;
import com.qjj.blog.knowledge.ai.AiProviderResolver;
import com.qjj.blog.knowledge.ai.LLMProvider;
import com.qjj.blog.knowledge.dto.AskRequest;
import com.qjj.blog.knowledge.entity.KnowledgeChatMessage;
import com.qjj.blog.knowledge.entity.KnowledgeChatSession;
import com.qjj.blog.knowledge.es.KnowledgeChunkIndexService;
import com.qjj.blog.knowledge.mapper.KnowledgeChatMessageMapper;
import com.qjj.blog.knowledge.mapper.KnowledgeChatSessionMapper;
import com.qjj.blog.knowledge.service.KnowledgeAskService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RAG 流式问答实现（api-design.md §4 契约）：
 * <ul>
 *   <li>流开始前（请求线程同步）：校验/日配额/会话/向量化/ES 检索——失败直接 JSON Result（401/403/429/503/30001），不进 SSE；</li>
 *   <li>流开始后（knowledgeAiExecutor 承载 LLM）：meta → delta… → refs → done；LLM 不可用 → KEYWORD_FALLBACK（match 片段 + 模板说明，不算 error）；流中故障 → error 帧；</li>
 *   <li>心跳：15s 无数据发注释帧 ": ping"（请求线程 poll 超时驱动）；</li>
 *   <li>落库：done 前写 Q 行 + A 行（refs JSON/cost_ms）；error/中断只留 Q 行。</li>
 * </ul>
 */
@Slf4j
@Service
public class KnowledgeAskServiceImpl implements KnowledgeAskService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TOP_K = 5;
    private static final long DAILY_LIMIT = 20;
    private static final long HEARTBEAT_SECONDS = 15;
    private static final String SENTINEL = "\u0000__END__";

    private final KnowledgeChatSessionMapper sessionMapper;
    private final KnowledgeChatMessageMapper messageMapper;
    private final AiProviderResolver providerResolver;
    private final KnowledgeChunkIndexService chunkIndexService;
    private final KnowledgeMetricsService metrics;
    private final IdGenerator idGenerator;
    private final RedissonClient redissonClient;
    private final java.util.concurrent.ExecutorService knowledgeAiExecutor;

    public KnowledgeAskServiceImpl(KnowledgeChatSessionMapper sessionMapper,
                                   KnowledgeChatMessageMapper messageMapper,
                                   AiProviderResolver providerResolver,
                                   KnowledgeChunkIndexService chunkIndexService,
                                   KnowledgeMetricsService metrics,
                                   IdGenerator idGenerator,
                                   RedissonClient redissonClient,
                                   @Qualifier(KnowledgeExecutorConfiguration.KNOWLEDGE_AI_EXECUTOR)
                                   java.util.concurrent.ExecutorService knowledgeAiExecutor) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.providerResolver = providerResolver;
        this.chunkIndexService = chunkIndexService;
        this.metrics = metrics;
        this.idGenerator = idGenerator;
        this.redissonClient = redissonClient;
        this.knowledgeAiExecutor = knowledgeAiExecutor;
    }

    @Override
    public void checkDailyQuota() {
        Long userId = UserContext.userIdOrSystem();
        String key = "user:ratelimit:knowledge-ask-day:" + userId + ":" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        org.redisson.api.RAtomicLong counter = redissonClient.getAtomicLong(key);
        long used = counter.incrementAndGet();
        counter.expire(Duration.ofHours(25));
        if (used > DAILY_LIMIT) {
            throw new BizException(SysErrorCodeEnum.RATE_LIMITED.getCode(), "今日配额已用尽，请明日再试");
        }
    }

    @Override
    public List<KnowledgeChunkIndexService.EsSearchHit> search(String question, String sessionId,
                                                               HttpServletResponse response) {
        if (!chunkIndexService.healthy()) {
            throw new BizException(com.qjj.blog.common.enums.BlogErrorCodeEnum.KNOWLEDGE_SEARCH_UNAVAILABLE);
        }
        // 问题向量化（语义检索无降级路径：失败 503·203101）
        List<float[]> vectors = providerResolver.resolveEmbedding().embed(List.of(question));
        metrics.incEmbed();
        List<Float> queryVector = new java.util.ArrayList<>(vectors.get(0).length);
        for (float f : vectors.get(0)) {
            queryVector.add(f);
        }
        return chunkIndexService.search(queryVector, question, TOP_K);
    }

    @Override
    public void stream(AskRequest request, String sessionIdResolved,
                       List<KnowledgeChunkIndexService.EsSearchHit> hits, HttpServletResponse response) {
        long start = System.currentTimeMillis();
        // Q 行先落（error/中断只留 Q 行）
        KnowledgeChatMessage qRow = insertMessage(sessionIdResolved, "Q", request.getQuestion(), null, 0);

        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        frames.offer(frame("meta", jsonMeta(sessionIdResolved, "RAG", currentLlmModel())));
        AtomicReference<String> mode = new AtomicReference<>("RAG");
        StringBuilder answer = new StringBuilder();
        AtomicBoolean finished = new AtomicBoolean(false);

        // LLM 流式在 knowledgeAiExecutor 执行（api 帧 via 队列）
        Runnable worker = () -> {
            StringBuilder prompt = buildPrompt(request.getQuestion(), hits);
            AtomicBoolean firstDelta = new AtomicBoolean(true);
            try {
                LLMProvider llm = providerResolver.resolveLlm();
                llm.chatStream(prompt.toString(), delta -> {
                    if (firstDelta.getAndSet(false)) {
                        metrics.incChat();
                    }
                    answer.append(delta);
                    frames.offer(frame("delta", jsonText(delta)));
                });
                sendRefsAndDone(frames, hits, sessionIdResolved, qRow, answer, start, mode.get());
            } catch (BizException e) {
                if (e.getCode() == com.qjj.blog.common.enums.BlogErrorCodeEnum.KNOWLEDGE_LLM_UNAVAILABLE.getCode()
                        && answer.length() == 0) {
                    // LLM 不可用 → KEYWORD_FALLBACK 降级（match 片段 + 模板化说明；不算 error）
                    log.warn("LLM 不可用，进入 KEYWORD_FALLBACK：{}", e.getMessage());
                    mode.set("KEYWORD_FALLBACK");
                    frames.clear();
                    frames.offer(frame("meta", jsonMeta(sessionIdResolved, "KEYWORD_FALLBACK", currentLlmModel())));
                    StringBuilder fallback = new StringBuilder();
                    fallback.append("AI 组织暂不可用，以下为关键词检索结果：\n\n");
                    int n = Math.min(hits.size(), 3);
                    for (int i = 0; i < n; i++) {
                        KnowledgeChunkIndexService.EsSearchHit h = hits.get(i);
                        fallback.append("**片段 ").append(i + 1).append("**（").append(h.docTitle()).append("）：")
                                .append(abbreviate(h.content(), 200)).append("\n\n");
                    }
                    if (n == 0) {
                        fallback.append("未检索到相关内容。");
                    }
                    answer.append(fallback);
                    frames.offer(frame("delta", jsonText(fallback.toString())));
                    sendRefsAndDone(frames, hits, sessionIdResolved, qRow, answer, start, mode.get());
                } else if (e.getCode() == SysErrorCodeEnum.RATE_LIMITED.getCode()) {
                    frames.offer(frame("error", jsonError(SysErrorCodeEnum.RATE_LIMITED.getCode(),
                            "服务繁忙，请稍后重试")));
                    frames.offer(SENTINEL);
                } else {
                    // 流已开始后发生故障：error 帧（前端保留已收内容）
                    frames.offer(frame("error", jsonError(e.getCode(), e.getMessage())));
                    frames.offer(SENTINEL);
                }
            } catch (Exception e) {
                log.warn("ask worker failed", e);
                frames.offer(frame("error", jsonError(SysErrorCodeEnum.SYSTEM_ERROR.getCode(), "系统异常")));
                frames.offer(SENTINEL);
            }
        };
        try {
            knowledgeAiExecutor.execute(worker);
        } catch (RejectedExecutionException e) {
            // 流开始前 AI 线程池满：429·10003（api-design.md §4.2）
            throw new BizException(SysErrorCodeEnum.RATE_LIMITED.getCode(), "服务繁忙，请稍后重试");
        }

        try {
            response.setContentType("text/event-stream;charset=UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("X-Accel-Buffering", "no");
            response.setHeader("Connection", "keep-alive");
            PrintWriter writer = response.getWriter();
            writer.flush();
            while (!finished.get()) {
                String f = frames.poll(HEARTBEAT_SECONDS, TimeUnit.SECONDS);
                if (f == null) {
                    // 15s 心跳注释帧
                    writer.write(": ping\n\n");
                    writer.flush();
                    continue;
                }
                if (SENTINEL.equals(f)) {
                    finished.set(true);
                    break;
                }
                writer.write(f);
                writer.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // 客户端断开：只落 Q 行（A 行不写）
            log.info("ask 客户端断开 sessionId={}", sessionIdResolved);
        }
    }

    // ---------- 私有 ----------

    private void sendRefsAndDone(BlockingQueue<String> frames, List<KnowledgeChunkIndexService.EsSearchHit> hits,
                                 String sessionId, KnowledgeChatMessage qRow, StringBuilder answer,
                                 long start, String mode) {
        try {
            String refsJson = jsonRefs(hits);
            frames.offer(frame("refs", refsJson));
            long costMs = System.currentTimeMillis() - start;
            // done 前写 A 行（含 refs JSON/cost_ms）
            KnowledgeChatMessage aRow = new KnowledgeChatMessage();
            aRow.setId(idGenerator.nextId());
            aRow.setSessionId(Long.valueOf(sessionId));
            aRow.setRole("A");
            aRow.setContent(answer.toString());
            aRow.setRefs(refsJson);
            aRow.setCostMs((int) costMs);
            messageMapper.insert(aRow);
            ObjectNode done = MAPPER.createObjectNode();
            done.put("messageId", String.valueOf(aRow.getId()));
            done.put("sessionId", sessionId);
            done.put("costMs", costMs);
            done.put("refCount", hits.size());
            frames.offer(frame("done", done.toString()));
            frames.offer(SENTINEL);
        } catch (Exception e) {
            log.error("sendRefsAndDone failed sessionId={}", sessionId, e);
            frames.offer(frame("error", jsonError(SysErrorCodeEnum.SYSTEM_ERROR.getCode(), "会话落库失败")));
            frames.offer(SENTINEL);
        }
    }

    private StringBuilder buildPrompt(String question, List<KnowledgeChunkIndexService.EsSearchHit> hits) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是本站知识库的问答助手。仅依据下方检索上下文回答用户问题；")
                .append("若上下文不足以回答，请明确说明\"未检索到相关内容\"，不要编造。")
                .append("回答使用简体中文，简洁准确。\n\n【检索上下文】\n");
        if (hits.isEmpty()) {
            prompt.append("（无命中）\n");
        } else {
            int i = 1;
            for (KnowledgeChunkIndexService.EsSearchHit h : hits) {
                prompt.append("[出处").append(i++).append("] ").append(h.docTitle()).append("\n")
                        .append(abbreviate(h.content(), 600)).append("\n\n");
            }
        }
        prompt.append("【用户问题】\n").append(question);
        return prompt;
    }

    private KnowledgeChatMessage insertMessage(String sessionId, String role, String content, String refs, int costMs) {
        KnowledgeChatMessage m = new KnowledgeChatMessage();
        m.setId(idGenerator.nextId());
        m.setSessionId(Long.valueOf(sessionId));
        m.setRole(role);
        m.setContent(content);
        m.setRefs(refs);
        m.setCostMs(costMs);
        messageMapper.insert(m);
        return m;
    }

    /** 会话解析/新建（流开始前调用，供 meta 首帧与落库） */
    @Override
    public String resolveSession(String sessionId) {
        Long userId = UserContext.userIdOrSystem();
        if (sessionId != null && !sessionId.isBlank()) {
            try {
                KnowledgeChatSession s = sessionMapper.selectById(Long.valueOf(sessionId));
                if (s != null) {
                    return sessionId;
                }
            } catch (NumberFormatException ignore) {
                // 非法 sessionId 视为新建
            }
        }
        KnowledgeChatSession s = new KnowledgeChatSession();
        s.setId(idGenerator.nextId());
        sessionMapper.insert(s);
        return String.valueOf(s.getId());
    }

    private String currentLlmModel() {
        var row = providerResolver.activeRow(com.qjj.blog.knowledge.enums.AiProviderKindEnum.LLM);
        return row == null || row.getModel() == null || row.getModel().isBlank() ? "Qwen/Qwen3-8B" : row.getModel();
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static String frame(String event, String data) {
        // 单行 JSON；不使用 retry 与多行 data（api-design.md §4.2）
        return "event: " + event + "\ndata: " + data.replace("\n", " ") + "\n\n";
    }

    private static String jsonMeta(String sessionId, String mode, String model) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("sessionId", sessionId);
        n.put("mode", mode);
        n.put("model", model == null ? "" : model);
        return n.toString();
    }

    private static String jsonText(String text) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("text", text);
        return n.toString();
    }

    private static String jsonError(int code, String msg) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("code", code);
        n.put("msg", msg == null ? "" : msg);
        return n.toString();
    }

    private static String jsonRefs(List<KnowledgeChunkIndexService.EsSearchHit> hits) {
        ObjectNode n = MAPPER.createObjectNode();
        ArrayNode refs = n.putArray("refs");
        for (KnowledgeChunkIndexService.EsSearchHit h : hits) {
            ObjectNode r = refs.addObject();
            r.put("chunkId", h.chunkId());
            r.put("docId", h.docId());
            r.put("docTitle", h.docTitle());
            r.put("docUrl", h.docUrl());
            r.put("score", Math.round(h.score() * 10000) / 10000.0);
        }
        return n.toString();
    }
}
