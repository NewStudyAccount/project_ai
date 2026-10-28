package com.qjj.blog.framework.concurrent;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 知识库云 API（Embedding / LLM）超时配置（blog-knowledge-base 提案；键名 kebab-case，禁止散落 @Value）。
 * 线程池 {@code knowledgeAiExecutor} 归属 framework（业务模块禁止自建线程池，CLAUDE.md §6.10），
 * 本配置项仅承载云 API 超时口径，由 blog-knowledge-service 消费。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "blog.knowledge.ai")
public class KnowledgeAiProperties {

    /** 云 API 连接超时（Embedding / LLM 共用） */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** Embedding 批量接口读超时 */
    private Duration embedReadTimeout = Duration.ofSeconds(15);

    /** LLM 流式接口读超时（SSE 分片间隔超时，非整流时长） */
    private Duration llmReadTimeout = Duration.ofSeconds(60);

    /** Embedding 批量大小（单次 embed 调用的 chunk 上限） */
    private int embedBatchSize = 32;
}
