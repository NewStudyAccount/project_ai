package com.qjj.blog.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.idempotent.Idempotent;
import com.qjj.blog.framework.idempotent.RateLimit;
import com.qjj.blog.objectstorage.ObjectStorageException;
import com.qjj.blog.objectstorage.ObjectStorageClient;
import com.qjj.blog.objectstorage.ProviderConfig;
import com.qjj.blog.objectstorage.ProviderConfigResolver;
import com.qjj.blog.service.dto.FilePageQuery;
import com.qjj.blog.service.entity.SysFile;
import com.qjj.blog.service.enums.AuditActionEnum;
import com.qjj.blog.service.enums.BizFileTypeEnum;
import com.qjj.blog.service.mapper.SysFileMapper;
import com.qjj.blog.service.service.AuditService;
import com.qjj.blog.service.service.FileReferenceChecker;
import com.qjj.blog.service.service.FileService;
import com.qjj.blog.service.vo.FileVO;
import com.qjj.blog.service.vo.FileUrlVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文件生命周期（object-storage-design.md §4）：上传/查询/URL/逻辑删除。
 * 多服务商：上传解析目标服务商并登记 storage_code；URL/删除按文件 storage_code 回源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final Set<String> ORDER_FIELDS = Set.of("create_time", "id");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/gif", "application/pdf");

    private final SysFileMapper fileMapper;
    private final IdGenerator idGenerator;
    private final ObjectStorageClient storageClient;
    private final ProviderConfigResolver resolver;
    private final AuditService auditService;
    private final List<FileReferenceChecker> referenceCheckers;

    @Override
    @Transactional
    @RateLimit(keyPrefix = "file-upload", permits = 10, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public FileVO upload(MultipartFile file) {
        return upload(file, null, null, null);
    }

    @Override
    @Transactional
    @RateLimit(keyPrefix = "file-upload", permits = 10, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public FileVO upload(MultipartFile file, String bizType, Long bizId) {
        return upload(file, bizType, bizId, null);
    }

    @Override
    @Transactional
    @RateLimit(keyPrefix = "file-upload", permits = 10, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public FileVO upload(MultipartFile file, String bizType, Long bizId, String storageCode) {
        if (file == null || file.isEmpty()) {
            throw new BizException(BlogErrorCodeEnum.FILE_UPLOAD_FAILED);
        }
        // 解析目标服务商（显式 code 须存在且启用；否则走默认链路）
        ProviderConfig provider;
        try {
            provider = resolver.resolveForUpload(storageCode);
        } catch (ObjectStorageException e) {
            throw translateStorageException(e);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BizException(BlogErrorCodeEnum.FILE_TYPE_NOT_ALLOWED);
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot);
        }
        String objectKey = UUID.randomUUID() + ext;
        try {
            storageClient.put(provider.getCode(), objectKey, file.getInputStream(),
                    file.getSize(), contentType, original);
        } catch (ObjectStorageException e) {
            throw translateStorageException(e);
        } catch (Exception e) {
            log.error("upload io failed provider={} key={}", provider.getCode(), objectKey, e);
            throw new BizException(BlogErrorCodeEnum.FILE_UPLOAD_FAILED);
        }
        SysFile meta = new SysFile();
        meta.setId(idGenerator.nextId());
        meta.setStorageCode(provider.getCode());
        meta.setBucket(provider.getBucket());
        meta.setObjectKey(objectKey);
        meta.setOriginalName(original);
        meta.setContentType(contentType);
        meta.setSizeBytes(file.getSize());
        meta.setBizType(BizFileTypeEnum.fromCode(bizType).getCode());
        meta.setBizId(bizId == null ? 0L : bizId);
        fileMapper.insert(meta);
        auditService.record(AuditActionEnum.FILE_UPLOAD, "FILE", String.valueOf(meta.getId()),
                "上传 " + original + " 至 " + provider.getCode());
        return toVO(meta);
    }

    @Override
    @RateLimit(keyPrefix = "file-list", permits = 60, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public PageResult<FileVO> page(FilePageQuery query) {
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = query.getSize() == null || query.getSize() < 1 ? 10 : Math.min(query.getSize(), 200);
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .like(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        SysFile::getOriginalName, query.getKeyword())
                .eq(query.getContentType() != null && !query.getContentType().isBlank(),
                        SysFile::getContentType, query.getContentType())
                .eq(query.getBizType() != null && !query.getBizType().isBlank(),
                        SysFile::getBizType, query.getBizType())
                .eq(query.getStorageCode() != null && !query.getStorageCode().isBlank(),
                        SysFile::getStorageCode, query.getStorageCode());
        String orderBy = query.getOrderBy() == null ? "create_time" : query.getOrderBy().toLowerCase();
        if (!ORDER_FIELDS.contains(orderBy)) {
            orderBy = "create_time";
        }
        boolean asc = "asc".equalsIgnoreCase(query.getOrder());
        wrapper.orderBy(true, asc, "create_time".equals(orderBy) ? SysFile::getCreateTime : SysFile::getId);
        Page<SysFile> page = fileMapper.selectPage(new Page<>(current, size), wrapper);
        List<FileVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), size, current);
    }

    @Override
    public FileVO get(String id) {
        return toVO(require(id));
    }

    @Override
    @RateLimit(keyPrefix = "file-url", permits = 60, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public FileUrlVO getFileUrl(String id) {
        SysFile file = require(id);
        FileUrlVO vo = new FileUrlVO();
        vo.setUrl(publicUrlOf(file));
        return vo;
    }

    @Override
    public String publicImageUrl(String id) {
        SysFile file = require(id);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!contentType.startsWith("image/")) {
            throw new BizException(BlogErrorCodeEnum.FILE_TYPE_NOT_ALLOWED);
        }
        return publicUrlOf(file);
    }

    @Override
    @Transactional
    @Idempotent(keyPrefix = "file-delete")
    public void logicalDelete(String id) {
        SysFile file = require(id);
        List<String> refs = new java.util.ArrayList<>();
        for (FileReferenceChecker checker : referenceCheckers) {
            FileReferenceChecker.FileReferenceResult result = checker.check(file.getId());
            if (result.isReferenced()) {
                refs.addAll(result.getRefs());
            }
        }
        if (!refs.isEmpty()) {
            throw new BizException(BlogErrorCodeEnum.FILE_IN_USE,
                    BlogErrorCodeEnum.FILE_IN_USE.getDesc() + "：" + String.join("；", refs));
        }
        file.setDeleted(1);
        fileMapper.updateById(file);
        auditService.record(AuditActionEnum.FILE_DELETE, "FILE", String.valueOf(file.getId()),
                "删除 " + file.getOriginalName());
    }

    /** 按文件 storage_code 回源服务商拼稳定公开 URL（停用服务商仍可读） */
    private String publicUrlOf(SysFile file) {
        try {
            return storageClient.publicUrl(file.getStorageCode(), file.getObjectKey());
        } catch (ObjectStorageException e) {
            throw translateStorageException(e);
        }
    }

    /** 能力层异常翻译为业务错误码（blog 系统号段 203080+） */
    private BizException translateStorageException(ObjectStorageException e) {
        BlogErrorCodeEnum code = switch (e.getReason()) {
            case PROVIDER_NOT_FOUND -> BlogErrorCodeEnum.PROVIDER_NOT_FOUND;
            case PROVIDER_DISABLED -> BlogErrorCodeEnum.PROVIDER_DISABLED;
            case NO_PROVIDER_AVAILABLE -> BlogErrorCodeEnum.NO_STORAGE_PROVIDER_AVAILABLE;
            case CONFIG_INVALID -> BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID;
            case PUT_FAILED -> BlogErrorCodeEnum.FILE_UPLOAD_FAILED;
            case GET_FAILED -> BlogErrorCodeEnum.FILE_NOT_FOUND;
            case REMOVE_FAILED -> BlogErrorCodeEnum.FILE_NOT_FOUND;
            case TEST_FAILED -> BlogErrorCodeEnum.PROVIDER_TEST_FAILED;
            case PUBLIC_URL_CONFIG_MISSING -> BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID;
            case SECRET_DECRYPT_FAILED -> BlogErrorCodeEnum.PROVIDER_SECRET_DECRYPT_FAILED;
            case MASTER_KEY_MISSING -> BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID;
        };
        String msg = code.getDesc();
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            msg = msg + "：" + e.getMessage();
        }
        return new BizException(code, msg);
    }

    private SysFile require(String id) {
        Long fileId;
        try {
            fileId = Long.valueOf(id);
        } catch (Exception e) {
            throw new BizException(BlogErrorCodeEnum.FILE_NOT_FOUND);
        }
        SysFile file = fileMapper.selectById(fileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw new BizException(BlogErrorCodeEnum.FILE_NOT_FOUND);
        }
        return file;
    }

    private FileVO toVO(SysFile entity) {
        FileVO vo = new FileVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setStorageCode(entity.getStorageCode());
        vo.setBucket(entity.getBucket());
        vo.setObjectKey(entity.getObjectKey());
        vo.setUrl(publicUrlOf(entity));
        vo.setOriginalName(entity.getOriginalName());
        vo.setContentType(entity.getContentType());
        vo.setSizeBytes(entity.getSizeBytes());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setDeleted(entity.getDeleted());
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().format(TIME));
        }
        return vo;
    }
}