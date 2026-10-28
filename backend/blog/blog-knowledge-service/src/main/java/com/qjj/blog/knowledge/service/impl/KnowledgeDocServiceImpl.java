package com.qjj.blog.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.enums.SysErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.idempotent.Idempotent;
import com.qjj.blog.framework.idempotent.RateLimit;
import com.qjj.blog.knowledge.api.ArticleContent;
import com.qjj.blog.knowledge.api.ArticleContentProvider;
import com.qjj.blog.knowledge.dto.DocPageQuery;
import com.qjj.blog.knowledge.dto.FromArticlesRequest;
import com.qjj.blog.knowledge.entity.KnowledgeArticleIndex;
import com.qjj.blog.knowledge.entity.KnowledgeChunk;
import com.qjj.blog.knowledge.entity.KnowledgeDoc;
import com.qjj.blog.knowledge.entity.KnowledgeFile;
import com.qjj.blog.knowledge.enums.KnowledgeDocSourceEnum;
import com.qjj.blog.knowledge.enums.KnowledgeDocStatusEnum;
import com.qjj.blog.knowledge.enums.KnowledgeEmbedStatusEnum;
import com.qjj.blog.knowledge.enums.KnowledgeTaskTypeEnum;
import com.qjj.blog.knowledge.mapper.KnowledgeArticleIndexMapper;
import com.qjj.blog.knowledge.mapper.KnowledgeChunkMapper;
import com.qjj.blog.knowledge.mapper.KnowledgeDocMapper;
import com.qjj.blog.knowledge.mapper.KnowledgeFileMapper;
import com.qjj.blog.knowledge.service.KnowledgeDocService;
import com.qjj.blog.knowledge.service.KnowledgeTaskService;
import com.qjj.blog.knowledge.vo.FromArticlesResultVO;
import com.qjj.blog.knowledge.vo.ImportDocsResultVO;
import com.qjj.blog.knowledge.vo.KnowledgeDocVO;
import com.qjj.blog.objectstorage.ObjectStorageClient;
import com.qjj.blog.objectstorage.ProviderConfig;
import com.qjj.blog.objectstorage.ProviderConfigResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 知识文档服务实现（api-design.md §1.4 / §2.1–§2.6）。
 * 入库语义：受理即返回（knowledge_task(INGEST) 立即投递，向量化异步）；
 * 唯一键语义：source=1 复用 doc 行 UPDATE；source=2 一文件一 doc（source_id=NULL）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocServiceImpl implements KnowledgeDocService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_FILES = 20;
    private static final String MD_SUFFIX = ".md";

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeFileMapper fileMapper;
    private final KnowledgeArticleIndexMapper articleIndexMapper;
    private final ArticleContentProvider articleContentProvider;
    private final KnowledgeTaskService taskService;
    private final IdGenerator idGenerator;
    private final ObjectStorageClient storageClient;
    private final ProviderConfigResolver storageResolver;

    // ---------- §1.4 从文章入库 ----------

    @Override
    @Transactional
    @Idempotent(keyPrefix = "knowledge-from-articles")
    @RateLimit(keyPrefix = "knowledge-ingest", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public FromArticlesResultVO fromArticles(FromArticlesRequest request) {
        FromArticlesResultVO result = new FromArticlesResultVO();
        for (String idStr : request.getArticleIds()) {
            Long articleId;
            try {
                articleId = Long.valueOf(idStr);
            } catch (NumberFormatException e) {
                reject(result, idStr, "NOT_FOUND", BlogErrorCodeEnum.ARTICLE_NOT_FOUND.getCode());
                continue;
            }
            KnowledgeArticleIndex projection = articleIndexMapper.selectOne(
                    new LambdaQueryWrapper<KnowledgeArticleIndex>()
                            .eq(KnowledgeArticleIndex::getArticleId, articleId)
                            .last("LIMIT 1"));
            if (projection == null) {
                // 投影表仅保留已发布行：无行 = 不存在或未发布（再经契约区分 NOT_FOUND）
                if (articleContentProvider.fetchArticle(articleId) == null) {
                    reject(result, idStr, "NOT_FOUND", BlogErrorCodeEnum.ARTICLE_NOT_FOUND.getCode());
                } else {
                    reject(result, idStr, "NOT_PUBLISHED", BlogErrorCodeEnum.KNOWLEDGE_ARTICLE_NOT_INGESTABLE.getCode());
                }
                continue;
            }
            long liveFileId = projection.getLiveFileId() == null ? 0L : projection.getLiveFileId();
            if (liveFileId <= 0) {
                reject(result, idStr, "NO_CONTENT", BlogErrorCodeEnum.KNOWLEDGE_ARTICLE_NOT_INGESTABLE.getCode());
                continue;
            }
            KnowledgeDoc existing = docMapper.selectOne(new LambdaQueryWrapper<KnowledgeDoc>()
                    .eq(KnowledgeDoc::getSource, KnowledgeDocSourceEnum.ARTICLE.getCode())
                    .eq(KnowledgeDoc::getSourceId, articleId)
                    .last("LIMIT 1"));
            if (existing != null && !Integer.valueOf(1).equals(existing.getDeleted())
                    && KnowledgeDocStatusEnum.READY.getCode().equals(existing.getStatus())
                    && existing.getContentVersion() != null
                    && existing.getContentVersion().equals(String.valueOf(liveFileId))) {
                FromArticlesResultVO.Skipped skip = new FromArticlesResultVO.Skipped();
                skip.setArticleId(idStr);
                skip.setReason("ALREADY_INGESTED");
                result.getSkippedItems().add(skip);
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            // 复用既有行语义：UPDATE 重置，不重复 INSERT
            boolean reused = existing != null;
            KnowledgeDoc doc = reused ? existing : new KnowledgeDoc();
            doc.setSource(KnowledgeDocSourceEnum.ARTICLE.getCode());
            doc.setSourceId(articleId);
            doc.setTitle(projection.getTitle());
            doc.setContentVersion(String.valueOf(liveFileId));
            doc.setFileId(0L);
            doc.setTags("");
            doc.setStatus(KnowledgeDocStatusEnum.PENDING.getCode());
            doc.setChunkCount(0);
            doc.setFailReason("");
            if (!reused) {
                doc.setId(idGenerator.nextId());
                docMapper.insert(doc);
            } else {
                docMapper.updateById(doc);
            }
            Long taskId = taskService.enqueue(KnowledgeTaskTypeEnum.INGEST.getCode(), doc.getId(),
                    "{\"source\":1,\"sourceId\":" + articleId + ",\"contentVersion\":\"" + liveFileId + "\"}");
            FromArticlesResultVO.Accepted accepted = new FromArticlesResultVO.Accepted();
            accepted.setArticleId(idStr);
            accepted.setDocId(String.valueOf(doc.getId()));
            accepted.setTaskId(taskId == null ? "" : String.valueOf(taskId));
            accepted.setReused(reused);
            result.getAccepted().add(accepted);
            result.setSubmitted(result.getSubmitted() + 1);
        }
        return result;
    }

    // ---------- §2.2 手动导入 ----------

    @Override
    @Transactional
    @Idempotent(keyPrefix = "knowledge-import")
    @RateLimit(keyPrefix = "knowledge-write", permits = 10, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public ImportDocsResultVO importDocs(MultipartFile[] files, String tags) {
        ImportDocsResultVO result = new ImportDocsResultVO();
        if (files == null || files.length == 0) {
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_ARTICLE_NOT_INGESTABLE, "请至少上传一个 .md 文件");
        }
        if (files.length > MAX_FILES) {
            throw new BizException(SysErrorCodeEnum.VALIDATION_FAILED, "单次最多上传 20 个文件");
        }
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            if (!name.toLowerCase().endsWith(MD_SUFFIX)) {
                rejectImport(result, name, "TYPE_NOT_ALLOWED", 203031);
                continue;
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                rejectImport(result, name, "TOO_LARGE", 203032);
                continue;
            }
            String content;
            try {
                content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                rejectImport(result, name, "EMPTY_CONTENT", 203064);
                continue;
            }
            if (content.isBlank()) {
                rejectImport(result, name, "EMPTY_CONTENT", 203064);
                continue;
            }
            // doc(source=2, source_id=NULL) 先建行 → object_key 带 docId
            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setSource(KnowledgeDocSourceEnum.MANUAL.getCode());
            doc.setSourceId(null);
            doc.setTitle(defaultTitle(name));
            doc.setContentVersion("");
            doc.setFileId(0L);
            doc.setTags(tags == null ? "" : tags.trim());
            doc.setStatus(KnowledgeDocStatusEnum.PENDING.getCode());
            doc.setChunkCount(0);
            doc.setFailReason("");
            doc.setId(idGenerator.nextId());
            docMapper.insert(doc);

            Long fileId = putKnowledgeObject(doc.getId(), name, content);
            doc.setFileId(fileId);
            doc.setContentVersion(String.valueOf(fileId));
            docMapper.updateById(doc);

            Long taskId = taskService.enqueue(KnowledgeTaskTypeEnum.INGEST.getCode(), doc.getId(),
                    "{\"source\":2}");
            ImportDocsResultVO.Accepted accepted = new ImportDocsResultVO.Accepted();
            accepted.setOriginalName(name);
            accepted.setDocId(String.valueOf(doc.getId()));
            accepted.setTaskId(taskId == null ? "" : String.valueOf(taskId));
            result.getAccepted().add(accepted);
            result.setSubmitted(result.getSubmitted() + 1);
        }
        return result;
    }

    // ---------- §2.3–§2.6 编辑/删除/重建 ----------

    @Override
    @Transactional
    @Idempotent(keyPrefix = "knowledge-doc-update")
    @RateLimit(keyPrefix = "knowledge-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public Map<String, Object> updateDoc(String id, MultipartFile file, String tags) {
        KnowledgeDoc doc = requireDoc(id);
        if (!KnowledgeDocSourceEnum.MANUAL.getCode().equals(doc.getSource())) {
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_DOC_SOURCE_NOT_MANUAL);
        }
        Map<String, Object> out = new HashMap<>();
        if (tags != null) {
            doc.setTags(tags.trim());
        }
        if (file != null && !file.isEmpty()) {
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            if (!name.toLowerCase().endsWith(MD_SUFFIX)) {
                throw new BizException(BlogErrorCodeEnum.FILE_TYPE_NOT_ALLOWED, "仅允许 .md 文件");
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                throw new BizException(BlogErrorCodeEnum.FILE_TOO_LARGE, "单文件不能超过 10MB");
            }
            String content;
            try {
                content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new BizException(BlogErrorCodeEnum.CONTENT_FORMAT_INVALID, "源文读取失败");
            }
            if (content.isBlank()) {
                throw new BizException(BlogErrorCodeEnum.CONTENT_FORMAT_INVALID, "源文内容为空");
            }
            // 重传：新对象登记 → content_version 变 → 重置 PENDING + REVECTORIZE（先删旧 ES chunk）
            Long fileId = putKnowledgeObject(doc.getId(), name, content);
            doc.setFileId(fileId);
            doc.setContentVersion(String.valueOf(fileId));
            doc.setStatus(KnowledgeDocStatusEnum.PENDING.getCode());
            doc.setChunkCount(0);
            doc.setFailReason("");
            docMapper.updateById(doc);
            Long taskId = taskService.enqueue(KnowledgeTaskTypeEnum.REVECTORIZE.getCode(), doc.getId(),
                    "{\"action\":\"REUPLOAD\"}");
            out.put("taskId", taskId == null ? "" : String.valueOf(taskId));
        } else {
            docMapper.updateById(doc);
        }
        out.put("id", String.valueOf(doc.getId()));
        out.put("contentVersion", doc.getContentVersion());
        out.put("status", doc.getStatus());
        return out;
    }

    @Override
    @Transactional
    @Idempotent(keyPrefix = "knowledge-doc-delete")
    @RateLimit(keyPrefix = "knowledge-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public Map<String, Object> deleteDoc(String id) {
        KnowledgeDoc doc = requireDoc(id);
        if (!KnowledgeDocSourceEnum.MANUAL.getCode().equals(doc.getSource())) {
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_DOC_SOURCE_NOT_MANUAL);
        }
        docMapper.deleteById(doc.getId());
        // knowledge_file 一并逻辑删（biz_id → doc.id）
        List<KnowledgeFile> files = fileMapper.selectList(new LambdaQueryWrapper<KnowledgeFile>()
                .eq(KnowledgeFile::getBizType, "KNOWLEDGE_SOURCE")
                .eq(KnowledgeFile::getBizId, doc.getId()));
        for (KnowledgeFile f : files) {
            fileMapper.deleteById(f.getId());
        }
        Long taskId = taskService.enqueue(KnowledgeTaskTypeEnum.INVALIDATE.getCode(), doc.getId(),
                "{\"action\":\"DELETE\"}");
        Map<String, Object> out = new HashMap<>();
        out.put("id", String.valueOf(doc.getId()));
        out.put("taskId", taskId == null ? "" : String.valueOf(taskId));
        return out;
    }

    @Override
    @Idempotent(keyPrefix = "knowledge-revectorize")
    @RateLimit(keyPrefix = "knowledge-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public Map<String, Object> revectorize(String id) {
        KnowledgeDoc doc = requireDoc(id);
        if (KnowledgeDocStatusEnum.INVALID.getCode().equals(doc.getStatus())) {
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_DOC_INVALID);
        }
        doc.setStatus(KnowledgeDocStatusEnum.PENDING.getCode());
        doc.setFailReason("");
        docMapper.updateById(doc);
        Long taskId = taskService.enqueue(KnowledgeTaskTypeEnum.REVECTORIZE.getCode(), doc.getId(),
                "{\"action\":\"REVECTORIZE\"}");
        Map<String, Object> out = new HashMap<>();
        out.put("docId", String.valueOf(doc.getId()));
        out.put("taskId", taskId == null ? "" : String.valueOf(taskId));
        return out;
    }

    @Override
    @Idempotent(keyPrefix = "knowledge-reindex")
    @RateLimit(keyPrefix = "knowledge-write", permits = 5, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public Map<String, Object> reindex() {
        long docTotal = docMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getDeleted, 0));
        Long taskId = taskService.enqueue(KnowledgeTaskTypeEnum.FULL_REINDEX.getCode(), 0L, "{\"action\":\"FULL_REINDEX\"}");
        Map<String, Object> out = new HashMap<>();
        out.put("taskId", taskId == null ? "" : String.valueOf(taskId));
        out.put("docTotal", docTotal);
        return out;
    }

    // ---------- §2.1 分页 ----------

    @Override
    public PageResult<KnowledgeDocVO> pageDocs(DocPageQuery query) {
        long current = query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = query.getSize() < 1 ? 10 : Math.min(query.getSize(), 200);
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(KnowledgeDoc::getTitle, query.getKeyword());
        }
        if (query.getSource() != null) {
            wrapper.eq(KnowledgeDoc::getSource, query.getSource());
        }
        if (query.getStatus() != null) {
            wrapper.eq(KnowledgeDoc::getStatus, query.getStatus());
        }
        boolean byChunkCount = "chunk_count".equals(query.getOrderBy());
        if (byChunkCount) {
            wrapper.orderByDesc(KnowledgeDoc::getChunkCount).orderByDesc(KnowledgeDoc::getId);
        } else {
            wrapper.orderByDesc(KnowledgeDoc::getUpdateTime).orderByDesc(KnowledgeDoc::getId);
        }
        Page<KnowledgeDoc> page = docMapper.selectPage(new Page<>(current, size), wrapper);
        List<KnowledgeDocVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), size, current);
    }

    // ---------- 私有 ----------

    private KnowledgeDoc requireDoc(String id) {
        KnowledgeDoc doc;
        try {
            doc = docMapper.selectById(Long.valueOf(id));
        } catch (NumberFormatException e) {
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_DOC_NOT_FOUND);
        }
        if (doc == null) {
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_DOC_NOT_FOUND);
        }
        return doc;
    }

    /** put MinIO knowledge/{docId}/{uuid}.md → 登记 knowledge_file → file.id */
    private Long putKnowledgeObject(Long docId, String originalName, String content) {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ProviderConfig provider;
        try {
            provider = storageResolver.resolveForUpload(null);
        } catch (Exception e) {
            throw new BizException(BlogErrorCodeEnum.FILE_UPLOAD_FAILED, "存储服务商不可用");
        }
        String objectKey = "knowledge/" + docId + "/" + UUID.randomUUID() + MD_SUFFIX;
        try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            storageClient.put(provider.getCode(), objectKey, in, bytes.length, "text/markdown", originalName);
        } catch (Exception e) {
            log.error("knowledge object put failed docId={}", docId, e);
            throw new BizException(BlogErrorCodeEnum.FILE_UPLOAD_FAILED);
        }
        KnowledgeFile f = new KnowledgeFile();
        f.setId(idGenerator.nextId());
        f.setBucket(provider.getBucket());
        f.setObjectKey(objectKey);
        f.setOriginalName(originalName);
        f.setMimeType("text/markdown");
        f.setFileSize((long) bytes.length);
        f.setBizType("KNOWLEDGE_SOURCE");
        f.setBizId(docId);
        f.setStorageCode(provider.getCode());
        fileMapper.insert(f);
        return f.getId();
    }

    private String defaultTitle(String fileName) {
        String name = fileName.endsWith(MD_SUFFIX) ? fileName.substring(0, fileName.length() - MD_SUFFIX.length()) : fileName;
        return name.isBlank() ? "未命名知识" : name;
    }

    private void reject(FromArticlesResultVO result, String articleId, String reason, int code) {
        FromArticlesResultVO.Rejected r = new FromArticlesResultVO.Rejected();
        r.setArticleId(articleId);
        r.setReason(reason);
        r.setCode(code);
        result.getRejectedItems().add(r);
        result.setRejected(result.getRejected() + 1);
    }

    private void rejectImport(ImportDocsResultVO result, String name, String reason, int code) {
        ImportDocsResultVO.Rejected r = new ImportDocsResultVO.Rejected();
        r.setOriginalName(name);
        r.setReason(reason);
        r.setCode(code);
        result.getRejected().add(r);
    }

    private KnowledgeDocVO toVO(KnowledgeDoc doc) {
        KnowledgeDocVO vo = new KnowledgeDocVO();
        vo.setId(String.valueOf(doc.getId()));
        vo.setSource(doc.getSource());
        vo.setSourceId(doc.getSourceId() == null ? null : String.valueOf(doc.getSourceId()));
        vo.setTitle(doc.getTitle());
        vo.setTags(doc.getTags());
        vo.setStatus(doc.getStatus());
        vo.setChunkCount(doc.getChunkCount());
        Long ready = chunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocId, doc.getId())
                .eq(KnowledgeChunk::getEmbedStatus, KnowledgeEmbedStatusEnum.READY.getCode()));
        vo.setReadyChunkCount(ready == null ? 0 : ready.intValue());
        vo.setFailReason(doc.getFailReason());
        vo.setEmbedModel(doc.getEmbedModel());
        vo.setEmbedDim(doc.getEmbedDim());
        vo.setContentVersion(doc.getContentVersion());
        vo.setDocUrl(resolveDocUrl(doc));
        vo.setUpdateTime(doc.getUpdateTime() == null ? null : doc.getUpdateTime().format(TIME));
        return vo;
    }

    /** 文章 doc = /articles/{slug}（经只读契约回拉 slug；手动导入为 ""） */
    private String resolveDocUrl(KnowledgeDoc doc) {
        if (!KnowledgeDocSourceEnum.ARTICLE.getCode().equals(doc.getSource())) {
            return "";
        }
        try {
            ArticleContent content = articleContentProvider.fetchArticle(doc.getSourceId());
            return content == null || content.getSlug() == null || content.getSlug().isBlank()
                    ? "" : "/articles/" + content.getSlug();
        } catch (Exception e) {
            log.warn("resolveDocUrl failed docId={}", doc.getId(), e);
            return "";
        }
    }
}
