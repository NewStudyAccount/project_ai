package com.example.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.file.common.BizException;
import com.example.file.config.FileProperties;
import com.example.file.entity.FileObject;
import com.example.file.mapper.FileObjectMapper;
import com.example.file.security.CurrentUser;
import com.example.file.storage.ObjectStorage;
import com.example.file.util.ObjectKeys;
import com.example.file.vo.FileObjectVo;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对象生命周期：校验 → 先写库元数据 → 再写对象存储（失败可同 key 重试）。
 */
@Service
public class FileObjectService {

    private final FileObjectMapper fileObjectMapper;
    private final IdService idService;
    private final ObjectStorage objectStorage;
    private final FileProperties properties;

    public FileObjectService(
            FileObjectMapper fileObjectMapper,
            IdService idService,
            ObjectStorage objectStorage,
            FileProperties properties) {
        this.fileObjectMapper = fileObjectMapper;
        this.idService = idService;
        this.objectStorage = objectStorage;
        this.properties = properties;
    }

    /**
     * 上传；objectKey 非空则稳定覆盖同 key。
     */
    @Transactional
    public FileObjectVo upload(
            MultipartFile file,
            String bizType,
            String bizId,
            String scene,
            String objectKey) {
        if (file == null || file.isEmpty()) {
            throw BizException.badParam("文件不能为空");
        }
        long maxSize = properties.getUpload().getMaxSizeBytes();
        if (file.getSize() > maxSize) {
            throw BizException.tooLarge();
        }
        String contentType = ObjectKeys.normalizeContentType(file.getContentType());
        Set<String> allowed = properties.getUpload().getAllowedContentTypes()
                .stream()
                .map(ObjectKeys::normalizeContentType)
                .collect(Collectors.toSet());
        if (contentType.isEmpty()) {
            contentType = guessContentType(file.getOriginalFilename());
        }
        ObjectKeys.requireAllowedContentType(contentType, allowed);

        String key;
        if (objectKey != null && !objectKey.isBlank()) {
            key = objectKey.trim();
            ObjectKeys.requireSafeKey(key);
        } else {
            key = ObjectKeys.generateKey(bizType, bizId, scene, file.getOriginalFilename());
        }

        long ownerId = CurrentUser.idOrNull() == null ? 0L : CurrentUser.idOrNull();
        FileObject row = fileObjectMapper.selectByObjectKeyAny(key);
        boolean overwrite = row != null;
        if (!overwrite) {
            row = new FileObject();
            row.setId(idService.nextId("file_object"));
            row.setObjectKey(key);
            row.setCreateTime(java.time.LocalDateTime.now());
            row.setCreateBy(ownerId);
        }
        row.setBucket(objectStorage.bucket());
        row.setUrl(objectStorage.publicUrl(key));
        row.setContentType(contentType);
        row.setSize(file.getSize());
        row.setOwnerId(ownerId);
        row.setBizType(bizType == null ? "" : bizType);
        row.setBizId(bizId == null ? "" : String.valueOf(bizId));
        row.setScene(scene == null ? "" : scene);
        row.setUpdateTime(java.time.LocalDateTime.now());
        row.setUpdateBy(ownerId);
        row.setDeleted(0);

        if (overwrite) {
            fileObjectMapper.updateMetaRevive(row);
        } else {
            fileObjectMapper.insert(row);
        }

        try (InputStream in = file.getInputStream()) {
            objectStorage.put(key, in, file.getSize(), contentType);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.storage("对象写入失败，请同 key 重试");
        }
        return toVo(row);
    }

    @Transactional
    public void deleteById(Long id) {
        FileObject row = fileObjectMapper.selectById(id);
        if (row == null) {
            throw BizException.notFound();
        }
        doDelete(row);
    }

    @Transactional
    public void deleteByKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw BizException.badParam("objectKey 不能为空");
        }
        ObjectKeys.requireSafeKey(objectKey);
        FileObject row = fileObjectMapper.selectByObjectKeyAny(objectKey);
        if (row == null || (row.getDeleted() != null && row.getDeleted() == 1)) {
            throw BizException.notFound();
        }
        doDelete(row);
    }

    public List<FileObjectVo> listByBiz(String bizType, String bizId) {
        if (bizType == null || bizType.isBlank()) {
            throw BizException.badParam("bizType 不能为空");
        }
        LambdaQueryWrapper<FileObject> qw = new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getBizType, bizType)
                .eq(bizId != null && !bizId.isBlank(), FileObject::getBizId, bizId)
                .orderByDesc(FileObject::getUpdateTime);
        return fileObjectMapper.selectList(qw).stream().map(this::toVo).collect(Collectors.toList());
    }

    public FileObjectVo getById(Long id) {
        FileObject row = fileObjectMapper.selectById(id);
        if (row == null) {
            throw BizException.notFound();
        }
        return toVo(row);
    }

    public FileObjectVo getByKey(String objectKey) {
        FileObject row = fileObjectMapper.selectByObjectKeyAny(objectKey);
        if (row == null || (row.getDeleted() != null && row.getDeleted() == 1)) {
            throw BizException.notFound();
        }
        return toVo(row);
    }

    private void doDelete(FileObject row) {
        long updateBy = CurrentUser.idOrNull() == null ? 0L : CurrentUser.idOrNull();
        fileObjectMapper.logicDeleteById(row.getId(), java.time.LocalDateTime.now(), updateBy);
        // 物理删对象；失败不阻断元数据逻辑删（可对账清理）
        try {
            objectStorage.remove(row.getObjectKey());
        } catch (Exception ignored) {
            // 对象可能已不存在
        }
    }

    private String guessContentType(String filename) {
        String ext = ObjectKeys.extension(filename);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "md", "markdown" -> "text/markdown";
            case "html", "htm" -> "text/html";
            default -> "";
        };
    }

    private FileObjectVo toVo(FileObject row) {
        return new FileObjectVo(
                row.getId(),
                row.getObjectKey(),
                row.getBucket(),
                row.getUrl(),
                row.getContentType(),
                row.getSize(),
                row.getOwnerId(),
                row.getBizType(),
                row.getBizId(),
                row.getScene(),
                row.getCreateTime(),
                row.getUpdateTime());
    }
}
