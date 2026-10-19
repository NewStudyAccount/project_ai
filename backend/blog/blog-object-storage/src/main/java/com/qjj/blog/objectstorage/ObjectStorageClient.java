package com.qjj.blog.objectstorage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 对象存储统一封装（object-storage-design.md §3.3）。
 * 业务禁止直绑 SDK；失败抛能力层异常，blog-service 翻译为业务错误码。
 * 读走公开永久 URL（§4.2），无预签名。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObjectStorageClient {

    private final ProviderClientRegistry registry;
    private final ProviderConfigResolver resolver;

    /**
     * 上传对象并写 Content-Type + Content-Disposition（image/pdf=inline，其它=attachment）。
     */
    public void put(String storageCode, String objectKey, InputStream in, long size,
                    String contentType, String originalName) {
        ProviderConfig cfg = resolver.resolveForRead(storageCode);
        if (cfg == null) {
            throw new ObjectStorageException(ObjectStorageException.Reason.PROVIDER_NOT_FOUND,
                    "storage provider not found: " + storageCode);
        }
        String ct = contentType == null ? "application/octet-stream" : contentType;
        try {
            registry.client(storageCode).putObject(PutObjectArgs.builder()
                    .bucket(cfg.getBucket())
                    .object(objectKey)
                    .stream(in, size, -1)
                    .contentType(ct)
                    .headers(Map.of("Content-Disposition", contentDisposition(ct, originalName)))
                    .build());
        } catch (Exception e) {
            log.error("putObject failed provider={} bucket={} key={}", storageCode, cfg.getBucket(), objectKey, e);
            throw new ObjectStorageException(ObjectStorageException.Reason.PUT_FAILED,
                    "put object failed", e);
        }
    }

    /**
     * 稳定公开 URL：publicBaseUrl + '/' + objectKey（无签名、无过期）。
     */
    public String publicUrl(String storageCode, String objectKey) {
        ProviderConfig cfg = resolver.resolveForRead(storageCode);
        if (cfg == null) {
            throw new ObjectStorageException(ObjectStorageException.Reason.PROVIDER_NOT_FOUND,
                    "storage provider not found: " + storageCode);
        }
        String base = cfg.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            throw new ObjectStorageException(ObjectStorageException.Reason.PUBLIC_URL_CONFIG_MISSING,
                    "public-base-url not configured for provider: " + storageCode);
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String key = objectKey != null && objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        return trimmed + "/" + key;
    }

    /** 读取对象内容为字节（业务正文等小对象） */
    public byte[] getBytes(String storageCode, String objectKey) {
        ProviderConfig cfg = resolver.resolveForRead(storageCode);
        if (cfg == null) {
            throw new ObjectStorageException(ObjectStorageException.Reason.PROVIDER_NOT_FOUND,
                    "storage provider not found: " + storageCode);
        }
        try (InputStream in = registry.client(storageCode).getObject(GetObjectArgs.builder()
                .bucket(cfg.getBucket())
                .object(objectKey)
                .build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            log.error("getObject failed provider={} bucket={} key={}", storageCode, cfg.getBucket(), objectKey, e);
            throw new ObjectStorageException(ObjectStorageException.Reason.GET_FAILED,
                    "get object failed", e);
        }
    }

    public void remove(String storageCode, String objectKey) {
        ProviderConfig cfg = resolver.resolveForRead(storageCode);
        if (cfg == null) {
            throw new ObjectStorageException(ObjectStorageException.Reason.PROVIDER_NOT_FOUND,
                    "storage provider not found: " + storageCode);
        }
        try {
            registry.client(storageCode).removeObject(RemoveObjectArgs.builder()
                    .bucket(cfg.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("removeObject failed provider={} bucket={} key={}", storageCode, cfg.getBucket(), objectKey, e);
            throw new ObjectStorageException(ObjectStorageException.Reason.REMOVE_FAILED,
                    "remove object failed", e);
        }
    }

    public boolean exists(String storageCode, String objectKey) {
        ProviderConfig cfg = resolver.resolveForRead(storageCode);
        if (cfg == null) {
            return false;
        }
        try {
            registry.client(storageCode).statObject(StatObjectArgs.builder()
                    .bucket(cfg.getBucket())
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            log.warn("stat failed provider={} bucket={} key={}", storageCode, cfg.getBucket(), objectKey);
            return false;
        }
    }

    /**
     * 连通性测试（headBucket 等价：bucketExists 探测）。
     * 返回成功；失败抛异常携带可读原因；详情禁止出现 SK/带凭证 endpoint。
     */
    public void testConnection(String storageCode) {
        ProviderConfig cfg = resolver.resolveForRead(storageCode);
        if (cfg == null) {
            throw new ObjectStorageException(ObjectStorageException.Reason.PROVIDER_NOT_FOUND,
                    "storage provider not found: " + storageCode);
        }
        MinioClient client = registry.client(storageCode);
        try {
            // MinIO SDK 提供 bucketExists 直接做 headBucket 等价探测
            if (!client.bucketExists(io.minio.BucketExistsArgs.builder()
                    .bucket(cfg.getBucket())
                    .build())) {
                throw new ObjectStorageException(ObjectStorageException.Reason.TEST_FAILED,
                        "bucket not exists: " + cfg.getBucket());
            }
        } catch (ObjectStorageException e) {
            throw e;
        } catch (Exception e) {
            log.warn("testConnection failed provider={} bucket={}", storageCode, cfg.getBucket(), e);
            throw new ObjectStorageException(ObjectStorageException.Reason.TEST_FAILED,
                    "connectivity test failed: " + e.getMessage(), e);
        }
    }

    static String contentDisposition(String contentType, String originalName) {
        boolean inline = contentType != null
                && (contentType.startsWith("image/") || "application/pdf".equalsIgnoreCase(contentType));
        if (inline) {
            return "inline";
        }
        String name = (originalName == null || originalName.isBlank()) ? "download" : originalName;
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + name.replace("\"", "'") + "\"; filename*=UTF-8''" + encoded;
    }
}