package com.qjj.blog.framework.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MinIO/S3 对象存储唯一封装（object-storage-design.md §3）。
 * 业务禁止直绑 SDK；失败抛业务/系统异常，不吞错。
 */
@Slf4j
@RequiredArgsConstructor
public class ObjectStorageClient {

    private final MinioClient minioClient;
    private final BlogMinioProperties properties;

    public void put(String objectKey, InputStream in, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(in, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            log.error("MinIO putObject failed bucket={} key={}", properties.getBucket(), objectKey, e);
            throw new IllegalStateException("文件上传失败", e);
        }
    }

    /** 预签名 GET，TTL 受配置约束 */
    public String presignGet(String objectKey, Duration ttl) {
        Duration effective = ttl == null ? properties.getPresignTtl() : ttl;
        if (effective.compareTo(properties.getPresignTtl()) > 0) {
            effective = properties.getPresignTtl();
        }
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .expiry((int) effective.toSeconds())
                    .build());
        } catch (Exception e) {
            log.error("MinIO presign failed bucket={} key={}", properties.getBucket(), objectKey, e);
            throw new IllegalStateException("预签名失败", e);
        }
    }

    public void remove(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("MinIO removeObject failed bucket={} key={}", properties.getBucket(), objectKey, e);
            throw new IllegalStateException("对象删除失败", e);
        }
    }

    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("MinIO stat failed bucket={} key={}", properties.getBucket(), objectKey);
            return false;
        }
    }

    public String bucket() {
        return properties.getBucket();
    }

    public long maxBytes() {
        return properties.getUploadMaxBytes();
    }

    public boolean isAllowedContentType(String contentType) {
        return contentType != null && properties.getAllowedContentTypes().contains(contentType);
    }
}
