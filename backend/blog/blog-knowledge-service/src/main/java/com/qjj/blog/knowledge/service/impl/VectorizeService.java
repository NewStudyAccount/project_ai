package com.qjj.blog.knowledge.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.framework.concurrent.KnowledgeAiProperties;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.knowledge.api.ArticleContent;
import com.qjj.blog.knowledge.api.ArticleContentProvider;
import com.qjj.blog.knowledge.ai.AiProviderResolver;
import com.qjj.blog.knowledge.ai.EmbeddingProvider;
import com.qjj.blog.knowledge.entity.KnowledgeChunk;
import com.qjj.blog.knowledge.entity.KnowledgeDoc;
import com.qjj.blog.knowledge.entity.KnowledgeFile;
import com.qjj.blog.knowledge.enums.KnowledgeDocSourceEnum;
import com.qjj.blog.knowledge.enums.KnowledgeDocStatusEnum;
import com.qjj.blog.knowledge.enums.KnowledgeEmbedStatusEnum;
import com.qjj.blog.knowledge.es.KnowledgeChunkIndexService;
import com.qjj.blog.knowledge.mapper.KnowledgeChunkMapper;
import com.qjj.blog.knowledge.mapper.KnowledgeDocMapper;
import com.qjj.blog.knowledge.mapper.KnowledgeFileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 向量化任务执行器（blog-knowledge-base 提案 tasks 6.6）：
 * 逐 chunk 批量调 EmbeddingProvider.embed → index 进 ES（_id=chunk.id 幂等覆盖）→ embed_status 回写；
 * doc 状态机 PENDING→VECTORIZING→READY/FAILED。source=1 的 doc 向量化成功后增量刷新投影行。
 */
@Slf4j
@Component
public class VectorizeService implements KnowledgeTaskServiceImpl.TaskHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeFileMapper fileMapper;
    private final IdGenerator idGenerator;
    private final ArticleContentProvider articleContentProvider;
    private final AiProviderResolver providerResolver;
    private final KnowledgeChunkIndexService chunkIndexService;
    private final KnowledgeChunker chunker;
    private final KnowledgeAiProperties aiProps;
    private final com.qjj.blog.objectstorage.ObjectStorageClient storageClient;
    private final com.qjj.blog.objectstorage.ProviderConfigResolver storageResolver;

    public VectorizeService(KnowledgeDocMapper docMapper,
                            KnowledgeChunkMapper chunkMapper,
                            KnowledgeFileMapper fileMapper,
                            IdGenerator idGenerator,
                            ArticleContentProvider articleContentProvider,
                            AiProviderResolver providerResolver,
                            KnowledgeChunkIndexService chunkIndexService,
                            KnowledgeChunker chunker,
                            KnowledgeAiProperties aiProps,
                            com.qjj.blog.objectstorage.ObjectStorageClient storageClient,
                            com.qjj.blog.objectstorage.ProviderConfigResolver storageResolver) {
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.fileMapper = fileMapper;
        this.idGenerator = idGenerator;
        this.articleContentProvider = articleContentProvider;
        this.providerResolver = providerResolver;
        this.chunkIndexService = chunkIndexService;
        this.chunker = chunker;
        this.aiProps = aiProps;
        this.storageClient = storageClient;
        this.storageResolver = storageResolver;
    }

    @Override
    public void execute(com.qjj.blog.knowledge.entity.KnowledgeTask task) {
        switch (task.getTaskType()) {
            case "INGEST", "REVECTORIZE" -> ingestDoc(task.getDocId());
            case "INVALIDATE" -> invalidate(task);
            case "FULL_REINDEX" -> fullReindex(task);
            default -> throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_TASK_NOT_FOUND,
                    "未知任务类型：" + task.getTaskType());
        }
    }

    /** 单文档入库/重建：切块 → 批量向量化 → ES index → 状态回写 */
    public void ingestDoc(Long docId) {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_DOC_NOT_FOUND);
        }
        doc.setStatus(KnowledgeDocStatusEnum.VECTORIZING.getCode());
        doc.setFailReason("");
        docMapper.updateById(doc);

        LoadedDoc loaded = loadDoc(doc);
        EmbeddingProvider embedding = providerResolver.resolveEmbedding();
        var embedRow = providerResolver.activeRow(com.qjj.blog.knowledge.enums.AiProviderKindEnum.EMBEDDING);

        // 旧 chunk 清理：MySQL 逻辑删 + ES 删该 doc 全部 chunk 文档
        List<KnowledgeChunk> oldChunks = chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocId, docId));
        for (KnowledgeChunk old : oldChunks) {
            old.setDeleted(1);
            chunkMapper.updateById(old);
        }
        chunkIndexService.ensureIndex();
        chunkIndexService.deleteByDoc(docId);

        // 切块
        List<KnowledgeChunker.Chunk> pieces = chunker.chunk(
                loaded.title(), loaded.content(), loaded.category(), loaded.tags());
        if (pieces.isEmpty()) {
            doc.setStatus(KnowledgeDocStatusEnum.FAILED.getCode());
            doc.setFailReason("正文切块后为空");
            docMapper.updateById(doc);
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_ARTICLE_NOT_INGESTABLE, "正文切块后为空");
        }
        List<KnowledgeChunk> chunks = new ArrayList<>(pieces.size());
        for (KnowledgeChunker.Chunk piece : pieces) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setId(idGenerator.nextId());
            chunk.setDocId(docId);
            chunk.setSeq(piece.seq());
            chunk.setHeading(truncate(piece.heading(), 200));
            chunk.setContent(piece.content());
            chunk.setTokenCount(piece.tokenCount());
            chunk.setEmbedStatus(KnowledgeEmbedStatusEnum.PENDING.getCode());
            chunk.setFailReason("");
            chunkMapper.insert(chunk);
            chunks.add(chunk);
        }

        // 批量向量化 + ES index（_id=chunk.id 幂等覆盖）
        int batchSize = Math.max(1, aiProps.getEmbedBatchSize());
        try {
            for (int from = 0; from < chunks.size(); from += batchSize) {
                List<KnowledgeChunk> batch = chunks.subList(from, Math.min(chunks.size(), from + batchSize));
                List<String> texts = batch.stream().map(KnowledgeChunk::getContent).toList();
                List<float[]> vectors = embedding.embed(texts);
                List<KnowledgeChunkIndexService.EsChunkDoc> docs = new ArrayList<>(batch.size());
                for (int i = 0; i < batch.size(); i++) {
                    KnowledgeChunk chunk = batch.get(i);
                    float[] v = vectors.get(i);
                    List<Float> vec = new ArrayList<>(v.length);
                    for (float f : v) {
                        vec.add(f);
                    }
                    docs.add(new KnowledgeChunkIndexService.EsChunkDoc(
                            String.valueOf(chunk.getId()), docId,
                            chunk.getHeading(), chunk.getContent(), vec,
                            doc.getSource(), splitTags(doc.getTags()),
                            KnowledgeDocStatusEnum.READY.getCode(),
                            loaded.title(), loaded.docUrl()));
                    chunk.setEmbedStatus(KnowledgeEmbedStatusEnum.READY.getCode());
                    chunk.setFailReason("");
                }
                chunkIndexService.bulkIndex(docs);
            }
        } catch (Exception e) {
            // 批次失败：失败块及后续 PENDING 块标 FAILED，doc FAILED；任务重试将整篇重跑（幂等覆盖）
            for (KnowledgeChunk chunk : chunks) {
                if (!KnowledgeEmbedStatusEnum.READY.getCode().equals(chunk.getEmbedStatus())) {
                    chunk.setEmbedStatus(KnowledgeEmbedStatusEnum.FAILED.getCode());
                    chunk.setFailReason(truncate(e.getMessage(), 500));
                    chunkMapper.updateById(chunk);
                }
            }
            doc.setStatus(KnowledgeDocStatusEnum.FAILED.getCode());
            doc.setFailReason(truncate(e.getMessage(), 500));
            docMapper.updateById(doc);
            throw e instanceof BizException be ? be : new BizException(BlogErrorCodeEnum.KNOWLEDGE_EMBED_FAILED,
                    "向量化失败：" + e.getMessage());
        }

        // 成功：doc READY + 元数据回写 + 投影增量刷新
        doc.setStatus(KnowledgeDocStatusEnum.READY.getCode());
        doc.setChunkCount(chunks.size());
        doc.setEmbedModel(embedRow == null ? "" : embedRow.getModel());
        doc.setEmbedDim(embedRow == null ? 0 : (embedRow.getDim() == null ? 0 : embedRow.getDim()));
        doc.setFailReason("");
        docMapper.updateById(doc);
        refreshProjection(doc, loaded);
        log.info("doc 向量化完成 docId={} chunks={}", docId, chunks.size());
    }

    /** 失效/删除同步：ES doc_status→4 或 delete_by_query（table-design.md 裁决 13） */
    private void invalidate(com.qjj.blog.knowledge.entity.KnowledgeTask task) {
        KnowledgeDoc doc = docMapper.selectById(task.getDocId());
        String action = extractPayloadField(task.getPayload(), "action");
        if ("DELETE".equals(action)) {
            chunkIndexService.deleteByDoc(task.getDocId());
            log.info("doc ES 文档已清除（DELETE）docId={}", task.getDocId());
            return;
        }
        // INVALID：MySQL doc 置 4 + ES doc_status=4（保留内容，候选页可重新入库恢复）
        if (doc != null) {
            doc.setStatus(KnowledgeDocStatusEnum.INVALID.getCode());
            docMapper.updateById(doc);
        }
        chunkIndexService.invalidateDoc(task.getDocId());
        log.info("doc 已失效（INVALID）docId={}", task.getDocId());
    }

    /** 全量重建：删 ES index 重建 → MySQL chunk/doc 状态重置 → 逐 doc 重切重向量（api-design.md §2.6） */
    private void fullReindex(com.qjj.blog.knowledge.entity.KnowledgeTask task) {
        chunkIndexService.recreateIndex();
        List<KnowledgeDoc> docs = docMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getDeleted, 0));
        int failed = 0;
        StringBuilder failedIds = new StringBuilder();
        for (KnowledgeDoc doc : docs) {
            try {
                ingestDoc(doc.getId());
            } catch (Exception e) {
                failed++;
                failedIds.append(doc.getId()).append(' ');
                log.warn("FULL_REINDEX doc 失败 docId={}: {}", doc.getId(), e.getMessage());
            }
        }
        String summary = failed == 0 ? "全量重建完成，共 " + docs.size() + " 篇"
                : "全量重建完成，共 " + docs.size() + " 篇，失败 " + failed + " 篇：" + failedIds;
        // 成功但留痕失败明细（failReason 兼作完成摘要）
        task.setFailReason(summary);
        if (failed > 0) {
            log.warn(summary);
        }
    }

    private LoadedDoc loadDoc(KnowledgeDoc doc) {
        if (KnowledgeDocSourceEnum.ARTICLE.getCode().equals(doc.getSource())) {
            ArticleContent content = articleContentProvider.fetchArticle(doc.getSourceId());
            if (content == null || content.getContent() == null || content.getContent().isBlank()) {
                doc.setStatus(KnowledgeDocStatusEnum.FAILED.getCode());
                doc.setFailReason("文章正文缺失或已下架");
                docMapper.updateById(doc);
                throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_ARTICLE_NOT_INGESTABLE,
                        "文章正文缺失或已下架 articleId=" + doc.getSourceId());
            }
            String docUrl = content.getSlug() == null || content.getSlug().isBlank()
                    ? "" : "/articles/" + content.getSlug();
            return new LoadedDoc(content.getTitle(), content.getContent(),
                    content.getCategoryName(), content.getTags(), docUrl, content);
        }
        // source=2 手动导入：读 knowledge_file 对象
        KnowledgeFile file = doc.getFileId() == null ? null : fileMapper.selectById(doc.getFileId());
        if (file == null) {
            doc.setStatus(KnowledgeDocStatusEnum.FAILED.getCode());
            doc.setFailReason("源文对象登记缺失");
            docMapper.updateById(doc);
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_DOC_NOT_FOUND, "源文对象登记缺失 docId=" + doc.getId());
        }
        try {
            byte[] bytes = storageClient.getBytes(file.getStorageCode(), file.getObjectKey());
            String content = new String(bytes, StandardCharsets.UTF_8);
            return new LoadedDoc(doc.getTitle(), content, "", splitTags(doc.getTags()), "", null);
        } catch (Exception e) {
            doc.setStatus(KnowledgeDocStatusEnum.FAILED.getCode());
            doc.setFailReason("源文对象读取失败：" + e.getMessage());
            docMapper.updateById(doc);
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_DOC_NOT_FOUND, "源文对象读取失败：" + e.getMessage());
        }
    }

    /** 入库成功增量刷新投影行（table-design.md §2.7 触发点 2；刷新器由 ArticleIndexService 提供） */
    private void refreshProjection(KnowledgeDoc doc, LoadedDoc loaded) {
        if (!KnowledgeDocSourceEnum.ARTICLE.getCode().equals(doc.getSource()) || loaded.articleContent() == null) {
            return;
        }
        if (projectionRefresher != null) {
            projectionRefresher.refresh(doc.getSourceId(), loaded.articleContent().getArticleUpdateTime());
        }
    }

    private String extractPayloadField(String payload, String field) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(payload);
            return node.path(field).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private record LoadedDoc(String title, String content, String category, List<String> tags,
                             String docUrl, ArticleContent articleContent) {
    }

    /** 投影刷新委托（ArticleIndexService 实现注入，避免 mapper 直连循环依赖） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProjectionRefresher projectionRefresher;

    public interface ProjectionRefresher {
        void refresh(Long articleId, LocalDateTime articleUpdateTime);
    }
}
