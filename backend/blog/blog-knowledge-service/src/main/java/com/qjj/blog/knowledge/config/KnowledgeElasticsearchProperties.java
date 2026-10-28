package com.qjj.blog.knowledge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库 ES 连接配置（blog-knowledge-base 提案 tasks 3.4；键名 kebab-case，禁止散落 @Value）。
 * 仅 blog-knowledge-service 消费（CLAUDE.md §2.1 例外条款：ES client 落点为本模块）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "blog.knowledge.elasticsearch")
public class KnowledgeElasticsearchProperties {

    /** ES index 契约固定 dims（table-design.md §3；ai_provider.dim 校验基准，203114） */
    public static final int INDEX_EMBEDDING_DIMS = 1024;

    /** ES 节点列表，如 http://192.168.99.100:9200 */
    private List<String> uris = new ArrayList<>();

    /** 基础认证（可选） */
    private String username;

    /** 基础认证（可选） */
    private String password;

    /** 建连超时 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 读超时（问答 knn/RRF 检索亦走此值） */
    private Duration socketTimeout = Duration.ofSeconds(30);

    /** 检索 index 名（当前唯一：knowledge_chunk_v1） */
    private String indexName = "knowledge_chunk_v1";

    /** RAG Top-K（RRF 融合后返回条数，api-design §8.1） */
    private int topK = 5;
}
