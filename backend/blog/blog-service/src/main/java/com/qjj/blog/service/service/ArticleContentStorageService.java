package com.qjj.blog.service.service;

import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.storage.ObjectStorageClient;
import com.qjj.blog.service.entity.SysFile;
import com.qjj.blog.service.enums.BizFileTypeEnum;
import com.qjj.blog.service.enums.ContentFormatEnum;
import com.qjj.blog.service.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 文章正文 OSS 读写：put 对象 → INSERT sys_file → 返回 file_id。
 * 业务禁止直绑 MinIO SDK；禁止业务表裸存 object_key。
 */
@Service
@RequiredArgsConstructor
public class ArticleContentStorageService {

    /** 单篇正文上限 500KB */
    public static final long MAX_CONTENT_BYTES = 500L * 1024L;

    private final ObjectStorageClient storageClient;
    private final SysFileMapper fileMapper;
    private final IdGenerator idGenerator;

    /**
     * 写入正文对象并登记 sys_file。
     *
     * @param track live|draft
     * @return 新 sys_file.id
     */
    public Long putContent(Long articleId, boolean draftTrack, String content, String format) {
        if (content == null || content.isBlank()) {
            throw new BizException(BlogErrorCodeEnum.CONTENT_REQUIRED);
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONTENT_BYTES) {
            throw new BizException(BlogErrorCodeEnum.CONTENT_TOO_LARGE);
        }
        String fmt = format == null || format.isBlank() ? ContentFormatEnum.MARKDOWN.getCode() : format;
        if (!ContentFormatEnum.supports(fmt)) {
            throw new BizException(BlogErrorCodeEnum.CONTENT_FORMAT_INVALID);
        }
        String ext = ContentFormatEnum.HTML.getCode().equals(fmt) ? ".html" : ".md";
        String track = draftTrack ? "draft" : "live";
        String objectKey = "blog-content/" + articleId + "/" + track + "/" + UUID.randomUUID() + ext;
        String contentType = ContentFormatEnum.HTML.getCode().equals(fmt) ? "text/html" : "text/markdown";
        try {
            storageClient.put(objectKey, new java.io.ByteArrayInputStream(bytes), bytes.length, contentType);
        } catch (Exception e) {
            throw new BizException(BlogErrorCodeEnum.FILE_UPLOAD_FAILED);
        }
        SysFile meta = new SysFile();
        meta.setId(idGenerator.nextId());
        meta.setBucket(storageClient.bucket());
        meta.setObjectKey(objectKey);
        meta.setOriginalName(articleId + ext);
        meta.setContentType(contentType);
        meta.setSizeBytes((long) bytes.length);
        meta.setBizType(draftTrack ? BizFileTypeEnum.ARTICLE_DRAFT.getCode() : BizFileTypeEnum.ARTICLE_LIVE.getCode());
        meta.setBizId(articleId);
        fileMapper.insert(meta);
        return meta.getId();
    }

    /** 按 file_id 读正文；不存在返回 null */
    public String readContent(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return null;
        }
        SysFile file = fileMapper.selectById(fileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            return null;
        }
        try {
            return new String(storageClient.getBytes(file.getObjectKey()), StandardCharsets.UTF_8);
        } catch (Exception e) {
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

    public String presignGet(Long fileId) {
        SysFile file = getFile(fileId);
        if (file == null) {
            throw new BizException(BlogErrorCodeEnum.FILE_NOT_FOUND);
        }
        return storageClient.presignGet(file.getObjectKey(), java.time.Duration.ofMinutes(10));
    }

    public String bucket() {
        return storageClient.bucket();
    }
}
