package com.qjj.blog.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.idempotent.Idempotent;
import com.qjj.blog.framework.idempotent.RateLimit;
import com.qjj.blog.knowledge.ai.AiProviderResolver;
import com.qjj.blog.knowledge.config.KnowledgeElasticsearchProperties;
import com.qjj.blog.knowledge.dto.AiProviderPageQuery;
import com.qjj.blog.knowledge.dto.AiProviderRequest;
import com.qjj.blog.knowledge.entity.AiProvider;
import com.qjj.blog.knowledge.enums.AiProviderKindEnum;
import com.qjj.blog.knowledge.mapper.AiProviderMapper;
import com.qjj.blog.knowledge.service.AiProviderService;
import com.qjj.blog.knowledge.service.impl.KnowledgeAuditService;
import com.qjj.blog.knowledge.vo.AiProviderTestResultVO;
import com.qjj.blog.knowledge.vo.AiProviderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * AI 服务商管理实现（api-design.md §3）。
 * apiKey 明文永不回显（掩码 sk-****abcd）；dim 与 ES index dims 一致校验（203114）；
 * 生效默认行禁删/禁停（203113）；kind 维度唯一默认由应用层保证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderServiceImpl implements AiProviderService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> VALID_TYPES = Set.of("SILICONFLOW", "DASHSCOPE", "ZHIPU", "OPENAI");
    private static final String TYPE_SILICONFLOW = "SILICONFLOW";

    private final AiProviderMapper providerMapper;
    private final IdGenerator idGenerator;
    private final AiProviderResolver resolver;
    private final KnowledgeAuditService auditService;

    @Override
    @RateLimit(keyPrefix = "ai-provider-list", permits = 60, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public PageResult<AiProviderVO> page(AiProviderPageQuery query) {
        long current = query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = query.getSize() < 1 ? 10 : Math.min(query.getSize(), 200);
        LambdaQueryWrapper<AiProvider> wrapper = new LambdaQueryWrapper<>();
        if (query.getKind() != null) {
            wrapper.eq(AiProvider::getKind, query.getKind());
        }
        if (query.getStatus() != null) {
            wrapper.eq(AiProvider::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(AiProvider::getKind).orderByDesc(AiProvider::getCreateTime).orderByDesc(AiProvider::getId);
        Page<AiProvider> page = providerMapper.selectPage(new Page<>(current, size), wrapper);
        List<AiProviderVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), size, current);
    }

    @Override
    @Transactional
    @Idempotent(keyPrefix = "ai-provider-create")
    @RateLimit(keyPrefix = "ai-provider-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public AiProviderVO create(AiProviderRequest request) {
        AiProviderKindEnum kind = requireKind(request.getKind());
        requireType(request.getType());
        requireDim(kind, request.getDim());
        Long count = providerMapper.selectCount(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getCode, request.getCode()));
        if (count != null && count > 0) {
            throw new BizException(BlogErrorCodeEnum.AI_PROVIDER_CODE_EXISTS);
        }
        AiProvider entity = new AiProvider();
        entity.setId(idGenerator.nextId());
        entity.setCode(request.getCode());
        entity.setKind(request.getKind());
        entity.setType(request.getType());
        entity.setName(request.getName());
        entity.setBaseUrl(request.getBaseUrl() == null ? "" : request.getBaseUrl());
        entity.setModel(request.getModel());
        entity.setDim(request.getDim() == null ? 0 : request.getDim());
        entity.setApiKey(request.getApiKey() == null ? "" : request.getApiKey());
        entity.setIsDefault(request.getIsDefault() == null ? 0 : request.getIsDefault());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        if (entity.getIsDefault() == 1) {
            clearOtherDefaults(entity.getKind());
        }
        providerMapper.insert(entity);
        auditService.record(KnowledgeAuditService.ACTION_AI_PROVIDER_CREATE, "AI_PROVIDER",
                String.valueOf(entity.getId()), "创建 AI 服务商 " + entity.getCode());
        return toVO(entity);
    }

    @Override
    @Transactional
    @Idempotent(keyPrefix = "ai-provider-update")
    @RateLimit(keyPrefix = "ai-provider-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public AiProviderVO update(String id, AiProviderRequest request) {
        AiProvider entity = require(Long.valueOf(id));
        AiProviderKindEnum kind = requireKind(request.getKind());
        requireType(request.getType());
        requireDim(kind, request.getDim());
        entity.setCode(request.getCode());
        entity.setKind(request.getKind());
        entity.setType(request.getType());
        entity.setName(request.getName());
        entity.setBaseUrl(request.getBaseUrl() == null ? "" : request.getBaseUrl());
        entity.setModel(request.getModel());
        entity.setDim(request.getDim() == null ? 0 : request.getDim());
        // apiKey 空=不修改
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            entity.setApiKey(request.getApiKey());
        }
        if (request.getIsDefault() != null && request.getIsDefault() == 1) {
            clearOtherDefaults(entity.getKind());
            entity.setIsDefault(1);
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        providerMapper.updateById(entity);
        auditService.record(KnowledgeAuditService.ACTION_AI_PROVIDER_UPDATE, "AI_PROVIDER",
                String.valueOf(entity.getId()), "更新 AI 服务商 " + entity.getCode());
        return toVO(entity);
    }

    @Override
    @Transactional
    @Idempotent(keyPrefix = "ai-provider-delete")
    @RateLimit(keyPrefix = "ai-provider-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public void delete(String id) {
        AiProvider entity = require(Long.valueOf(id));
        if (isEffectiveDefault(entity)) {
            throw new BizException(BlogErrorCodeEnum.AI_PROVIDER_IN_USE,
                    BlogErrorCodeEnum.AI_PROVIDER_IN_USE.getDesc() + "：" + entity.getCode());
        }
        providerMapper.deleteById(entity.getId());
        auditService.record(KnowledgeAuditService.ACTION_AI_PROVIDER_DELETE, "AI_PROVIDER",
                String.valueOf(entity.getId()), "删除 AI 服务商 " + entity.getCode());
    }

    @Override
    @RateLimit(keyPrefix = "ai-provider-test", permits = 10, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public AiProviderTestResultVO test(String id) {
        AiProvider entity = require(Long.valueOf(id));
        AiProviderKindEnum kind = AiProviderKindEnum.fromCode(entity.getKind());
        boolean embedding = kind == AiProviderKindEnum.EMBEDDING;
        String baseUrl = entity.getBaseUrl() == null || entity.getBaseUrl().isBlank()
                ? ymlBaseUrl(entity.getCode()) : entity.getBaseUrl();
        String apiKey = entity.getApiKey() == null || entity.getApiKey().isBlank()
                ? ymlApiKey(entity.getCode()) : entity.getApiKey();
        try {
            long latencyMs = resolver.buildClient(baseUrl, apiKey, entity.getModel()).ping(embedding);
            auditService.record(KnowledgeAuditService.ACTION_AI_PROVIDER_TEST, "AI_PROVIDER",
                    String.valueOf(entity.getId()), "连通测试成功 " + entity.getCode() + "，耗时 " + latencyMs + "ms");
            AiProviderTestResultVO vo = new AiProviderTestResultVO();
            vo.setOk(true);
            vo.setLatencyMs(latencyMs);
            vo.setMessage("连通成功");
            return vo;
        } catch (BizException e) {
            auditService.record(KnowledgeAuditService.ACTION_AI_PROVIDER_TEST, "AI_PROVIDER",
                    String.valueOf(entity.getId()), "连通测试失败 " + entity.getCode() + "：" + e.getMessage());
            throw new BizException(BlogErrorCodeEnum.AI_PROVIDER_TEST_FAILED,
                    BlogErrorCodeEnum.AI_PROVIDER_TEST_FAILED.getDesc() + "：" + e.getMessage());
        }
    }

    private AiProvider require(Long id) {
        AiProvider entity = providerMapper.selectById(id);
        if (entity == null || Integer.valueOf(1).equals(entity.getDeleted())) {
            throw new BizException(BlogErrorCodeEnum.AI_PROVIDER_NOT_FOUND);
        }
        return entity;
    }

    private AiProviderKindEnum requireKind(Integer kind) {
        AiProviderKindEnum e = AiProviderKindEnum.fromCode(kind);
        if (e == null) {
            throw new BizException(BlogErrorCodeEnum.AI_PROVIDER_NOT_FOUND,
                    "kind 取值非法：" + kind);
        }
        return e;
    }

    private void requireType(String type) {
        if (type == null || !VALID_TYPES.contains(type)) {
            throw new BizException(BlogErrorCodeEnum.AI_PROVIDER_NOT_FOUND,
                    "type 取值非法：" + type + "（允许 " + VALID_TYPES + "）");
        }
    }

    /** dim 与 ES index dims 一致（203114）；kind=1 必填 >0 */
    private void requireDim(AiProviderKindEnum kind, Integer dim) {
        if (kind == AiProviderKindEnum.EMBEDDING) {
            if (dim == null || dim <= 0) {
                throw new BizException(BlogErrorCodeEnum.AI_PROVIDER_NOT_FOUND,
                        "Embedding 服务商 dim 必填且 >0");
            }
        }
        if (dim != null && dim > 0 && dim != KnowledgeElasticsearchProperties.INDEX_EMBEDDING_DIMS) {
            throw new BizException(BlogErrorCodeEnum.AI_PROVIDER_DIM_MISMATCH);
        }
    }

    private boolean isEffectiveDefault(AiProvider entity) {
        return entity.getIsDefault() != null && entity.getIsDefault() == 1
                && entity.getStatus() != null && entity.getStatus() == 1;
    }

    private void clearOtherDefaults(Integer kind) {
        providerMapper.update(null, new LambdaUpdateWrapper<AiProvider>()
                .set(AiProvider::getIsDefault, 0)
                .eq(AiProvider::getKind, kind)
                .eq(AiProvider::getIsDefault, 1));
    }

    private String ymlBaseUrl(String code) {
        return resolver.ymlProvider(code).getBaseUrl();
    }

    private String ymlApiKey(String code) {
        return resolver.ymlProvider(code).getApiKey();
    }

    private AiProviderVO toVO(AiProvider entity) {
        AiProviderVO vo = new AiProviderVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setCode(entity.getCode());
        vo.setKind(entity.getKind());
        vo.setType(entity.getType());
        vo.setName(entity.getName());
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setModel(entity.getModel());
        vo.setDim(entity.getDim());
        vo.setApiKeyMasked(maskApiKey(entity.getApiKey()));
        vo.setIsDefault(entity.getIsDefault());
        vo.setStatus(entity.getStatus());
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().format(TIME));
        }
        return vo;
    }

    /** 掩码：sk-****abcd（前 2 后 4）；空返回 ""；明文永不回显 */
    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        String head = apiKey.length() > 2 ? apiKey.substring(0, 2) : "";
        String tail = apiKey.length() > 6 ? apiKey.substring(apiKey.length() - 4) : "";
        return head + "****" + tail;
    }
}
