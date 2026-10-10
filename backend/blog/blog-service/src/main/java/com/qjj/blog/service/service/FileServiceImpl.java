package com.qjj.blog.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.idempotent.Idempotent;
import com.qjj.blog.framework.idempotent.RateLimit;
import com.qjj.blog.framework.storage.ObjectStorageClient;
import com.qjj.blog.service.dto.FilePageQuery;
import com.qjj.blog.service.entity.SysFile;
import com.qjj.blog.service.enums.AuditActionEnum;
import com.qjj.blog.service.mapper.SysFileMapper;
import com.qjj.blog.service.vo.FileVO;
import com.qjj.blog.service.vo.PresignVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文件生命周期（object-storage-design.md §4）：上传/查询/预签名/逻辑删除。
 */
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final Set<String> ORDER_FIELDS = Set.of("create_time", "id");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysFileMapper fileMapper;
    private final IdGenerator idGenerator;
    private final ObjectStorageClient storageClient;
    private final AuditService auditService;

    @Override
    @Transactional
    @RateLimit(keyPrefix = "file-upload", permits = 10, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public FileVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(BlogErrorCodeEnum.FILE_UPLOAD_FAILED);
        }
        if (file.getSize() > storageClient.maxBytes()) {
            throw new BizException(BlogErrorCodeEnum.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (!storageClient.isAllowedContentType(contentType)) {
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
            storageClient.put(objectKey, file.getInputStream(), file.getSize(), contentType);
        } catch (Exception e) {
            throw new BizException(BlogErrorCodeEnum.FILE_UPLOAD_FAILED);
        }
        SysFile meta = new SysFile();
        meta.setId(idGenerator.nextId());
        meta.setBucket(storageClient.bucket());
        meta.setObjectKey(objectKey);
        meta.setOriginalName(original);
        meta.setContentType(contentType == null ? "" : contentType);
        meta.setSizeBytes(file.getSize());
        fileMapper.insert(meta);
        auditService.record(AuditActionEnum.FILE_UPLOAD, "FILE", String.valueOf(meta.getId()),
                "上传 " + original);
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
                        SysFile::getContentType, query.getContentType());
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
    @RateLimit(keyPrefix = "file-presign", permits = 60, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public PresignVO presign(String id) {
        SysFile file = require(id);
        String url = storageClient.presignGet(file.getObjectKey(), Duration.ofMinutes(10));
        PresignVO vo = new PresignVO();
        vo.setUrl(url);
        vo.setExpireAt(LocalDateTime.now().plusMinutes(10).format(TIME));
        return vo;
    }

    @Override
    @Transactional
    @Idempotent(keyPrefix = "file-delete")
    public void logicalDelete(String id) {
        SysFile file = require(id);
        file.setDeleted(1);
        fileMapper.updateById(file);
        auditService.record(AuditActionEnum.FILE_DELETE, "FILE", String.valueOf(file.getId()),
                "删除 " + file.getOriginalName());
    }

    private SysFile require(String id) {
        Long fileId = parseId(id);
        SysFile file = fileMapper.selectById(fileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw new BizException(BlogErrorCodeEnum.FILE_NOT_FOUND);
        }
        return file;
    }

    private Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (Exception e) {
            throw new BizException(BlogErrorCodeEnum.FILE_NOT_FOUND);
        }
    }

    private FileVO toVO(SysFile entity) {
        FileVO vo = new FileVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setBucket(entity.getBucket());
        vo.setObjectKey(entity.getObjectKey());
        vo.setOriginalName(entity.getOriginalName());
        vo.setContentType(entity.getContentType());
        vo.setSizeBytes(entity.getSizeBytes());
        vo.setDeleted(entity.getDeleted());
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().format(TIME));
        }
        return vo;
    }
}
