package com.qjj.blog.service.service;

import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.objectstorage.ObjectStorageException;
import com.qjj.blog.objectstorage.ObjectStorageClient;
import com.qjj.blog.objectstorage.ProviderConfig;
import com.qjj.blog.objectstorage.ProviderConfigResolver;
import com.qjj.blog.service.entity.SysFile;
import com.qjj.blog.service.enums.BizFileTypeEnum;
import com.qjj.blog.service.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 文章正文对象读写：put 对象 → INSERT sys_file → 返回 file_id。
 * 正文唯一 Markdown（article-content-markdown-only）；走默认服务商；业务禁止直绑 SDK。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleContentStorageService {

    /** 单篇正文上限 500KB */
    public static final long MAX_CONTENT_BYTES = 500L * 1024L;

    private final ObjectStorageClient storageClient;
    private final ProviderConfigResolver resolver;
    private final SysFileMapper fileMapper;
    private final IdGenerator idGenerator;

    /**
     * 写入 Markdown 正文对象并登记 sys_file（走默认服务商）。
     *
     * @param draftTrack true=草稿轨，false=线上轨
     * @return 新 sys_file.id
     */
    public Long putContent(Long articleId, boolean draftTrack, String content) {
        if (content == null || content.isBlank()) {
            throw new BizException(BlogErrorCodeEnum.CONTENT_REQUIRED);
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONTENT_BYTES) {
            throw new BizException(BlogErrorCodeEnum.CONTENT_TOO_LARGE);
        }
        ProviderConfig provider;
        try {
            provider = resolver.resolveForUpload(null);
        } catch (ObjectStorageException e) {
            throw new BizException(BlogErrorCodeEnum.FILE_UPLOAD_FAILED,
                    BlogErrorCodeEnum.FILE_UPLOAD_FAILED.getDesc() + "：存储服务商不可用");
        }
        String track = draftTrack ? "draft" : "live";
        String objectKey = "blog-content/" + articleId + "/" + track + "/" + UUID.randomUUID() + ".md";
        String contentType = "text/markdown";
        try {
            storageClient.put(provider.getCode(), objectKey,
                    new ByteArrayInputStream(bytes), bytes.length, contentType, "content.md");
        } catch (ObjectStorageException e) {
            log.error("putContent failed article={} provider={}", articleId, provider.getCode(), e);
            throw new BizException(BlogErrorCodeEnum.FILE_UPLOAD_FAILED);
        }
        SysFile meta = new SysFile();
        meta.setId(idGenerator.nextId());
        meta.setStorageCode(provider.getCode());
        meta.setBucket(provider.getBucket());
        meta.setObjectKey(objectKey);
        meta.setOriginalName(articleId + ".md");
        meta.setContentType(contentType);
        meta.setSizeBytes((long) bytes.length);
        meta.setBizType(draftTrack ? BizFileTypeEnum.ARTICLE_DRAFT.getCode() : BizFileTypeEnum.ARTICLE_LIVE.getCode());
        meta.setBizId(articleId);
        fileMapper.insert(meta);
        return meta.getId();
    }

    /** 按 file_id 读正文（Markdown 源文）；不存在返回 null */
    public String readContent(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return null;
        }
        SysFile file = fileMapper.selectById(fileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            return null;
        }
        try {
            return new String(storageClient.getBytes(file.getStorageCode(), file.getObjectKey()),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("readContent failed fileId={} provider={}", fileId, file.getStorageCode(), e);
            return null;
        }
    }

    public SysFile getFile(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return null;
        }
        return fileMapper.selectById(fileId);
    }

    /** 逻辑删旧登记行 */
    public void logicalDeleteFile(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return;
        }
        SysFile file = fileMapper.selectById(fileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            return;
        }
        file.setDeleted(1);
        fileMapper.updateById(file);
    }

    /** 稳定公开 URL（无预签名/无过期）；按文件 storage_code 回源 */
    public String publicUrl(Long fileId) {
        SysFile file = getFile(fileId);
        if (file == null) {
            throw new BizException(BlogErrorCodeEnum.FILE_NOT_FOUND);
        }
        try {
            return storageClient.publicUrl(file.getStorageCode(), file.getObjectKey());
        } catch (ObjectStorageException e) {
            throw new BizException(BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID,
                    BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID.getDesc()
                            + "：无法解析文件 storage_code=" + file.getStorageCode());
        }
    }
}