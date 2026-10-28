package com.qjj.blog.framework.concurrent;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 知识库 AI 线程池装配（blog-knowledge-base 提案 tasks 3.5）。
 * 命名线程池 bean 归属 framework（业务模块禁止自建线程池，CLAUDE.md §6.10）：
 * <ul>
 *   <li>core=2 / max=8 / queue=100；拒绝策略 AbortPolicy——
 *       队列满抛 RejectedExecutionException，由调用方映射：问答侧 429+10003，异步入库侧 203103。</li>
 *   <li>承载两类任务：异步入库/向量化任务执行、RAG 问答 LLM 流式生成。</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(KnowledgeAiProperties.class)
public class KnowledgeExecutorConfiguration {

    public static final String KNOWLEDGE_AI_EXECUTOR = "knowledgeAiExecutor";

    @Bean(name = KNOWLEDGE_AI_EXECUTOR)
    public ExecutorService knowledgeAiExecutor() {
        AtomicInteger seq = new AtomicInteger(1);
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "knowledge-ai-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(
                2, 8, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
