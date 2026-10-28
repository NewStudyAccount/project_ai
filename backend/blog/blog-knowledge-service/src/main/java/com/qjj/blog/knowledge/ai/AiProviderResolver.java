package com.qjj.blog.knowledge.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.framework.concurrent.KnowledgeAiProperties;
import com.qjj.blog.knowledge.config.KnowledgeAiYmlProperties;
import com.qjj.blog.knowledge.entity.AiProvider;
import com.qjj.blog.knowledge.enums.AiProviderKindEnum;
import com.qjj.blog.knowledge.mapper.AiProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI Provider 解析器（DB 优先 + yml blog.ai.providers[] 引导默认；table-design.md §4.2）：
 * 取各 kind 的启用默认行（is_default=1 & status=1）；DB 行 base_url/api_key 为空时由 yml 同 code 行补齐；
 * 无 DB 行时整个由 yml 行兜底。每次解析现取现建（配置变更即时生效，实例为轻量值对象）。
 */
@Component
@RequiredArgsConstructor
public class AiProviderResolver {

    /** yml 引导默认行 code（与种子 2026-10-28-blog-knowledge.sql 一致） */
    public static final String DEFAULT_EMBED_CODE = "siliconflow-embed";
    public static final String DEFAULT_LLM_CODE = "siliconflow-llm";

    private final AiProviderMapper providerMapper;
    private final KnowledgeAiProperties aiProps;
    private final KnowledgeAiYmlProperties ymlProps;

    /** 生效 Embedding provider（无可用配置抛 203101） */
    public EmbeddingProvider resolveEmbedding() {
        AiProvider row = resolveRow(AiProviderKindEnum.EMBEDDING, DEFAULT_EMBED_CODE);
        if (row.getDim() == null || row.getDim() <= 0) {
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_EMBED_FAILED,
                    "Embedding 服务商未配置向量维度（dim），无法向量化");
        }
        return new SiliconFlowClient(effectiveBaseUrl(row), effectiveApiKey(row), row.getModel(), aiProps);
    }

    /** 生效 LLM provider（无可用配置抛 203102） */
    public LLMProvider resolveLlm() {
        AiProvider row = resolveRow(AiProviderKindEnum.LLM, DEFAULT_LLM_CODE);
        return new SiliconFlowClient(effectiveBaseUrl(row), effectiveApiKey(row), row.getModel(), aiProps);
    }

    /** 管理端连通测试用客户端（按 DB 行 + yml 补齐，不入库） */
    public SiliconFlowClient buildClient(String baseUrl, String apiKey, String model) {
        return new SiliconFlowClient(baseUrl, apiKey, model, aiProps);
    }

    /** yml 引导配置（同 code 行）；无则空值对象 */
    public KnowledgeAiYmlProperties.Provider ymlProvider(String code) {
        return yml(code);
    }

    /** 当前生效行（管理端展示/校验用）；无返回 null */
    public AiProvider activeRow(AiProviderKindEnum kind) {
        return providerMapper.selectOne(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getKind, kind.getCode())
                .eq(AiProvider::getIsDefault, 1)
                .eq(AiProvider::getStatus, 1)
                .last("LIMIT 1"));
    }

    private AiProvider resolveRow(AiProviderKindEnum kind, String fallbackCode) {
        AiProvider row = activeRow(kind);
        if (row == null) {
            row = new AiProvider();
            row.setCode(fallbackCode);
            row.setKind(kind.getCode());
            row.setModel("");
            row.setDim(kind == AiProviderKindEnum.EMBEDDING ? 1024 : 0);
        }
        return row;
    }

    private String effectiveBaseUrl(AiProvider row) {
        if (row.getBaseUrl() != null && !row.getBaseUrl().isBlank()) {
            return row.getBaseUrl();
        }
        return yml(row.getCode()).getBaseUrl();
    }

    private String effectiveApiKey(AiProvider row) {
        if (row.getApiKey() != null && !row.getApiKey().isBlank()) {
            return row.getApiKey();
        }
        return yml(row.getCode()).getApiKey();
    }

    private KnowledgeAiYmlProperties.Provider yml(String code) {
        return ymlProps.getProviders().stream()
                .filter(p -> p.getCode() != null && p.getCode().equals(code))
                .findFirst()
                .orElseGet(() -> {
                    KnowledgeAiYmlProperties.Provider empty = new KnowledgeAiYmlProperties.Provider();
                    empty.setCode(code);
                    empty.setBaseUrl("");
                    empty.setApiKey("");
                    return empty;
                });
    }
}
