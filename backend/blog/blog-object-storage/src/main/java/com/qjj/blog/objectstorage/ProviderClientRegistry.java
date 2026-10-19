package com.qjj.blog.objectstorage;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 多服务商客户端注册表（object-storage-design.md §3.3）。
 * 按 providerCode 缓存 MinioClient；配置变更后通过 {@link #invalidate} 失效缓存。
 * 管理 API 是唯一变更通道；直接改库不保证失效（运维约束）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderClientRegistry {

    private final S3ProviderFactory factory;
    private final ProviderConfigResolver resolver;

    private final ConcurrentHashMap<String, MinioClient> cache = new ConcurrentHashMap<>();

    /**
     * 获取（或按需创建并缓存）服务商客户端。
     */
    public MinioClient client(String providerCode) {
        return cache.computeIfAbsent(providerCode, code -> {
            ProviderConfig cfg = resolver.resolveForRead(code);
            if (cfg == null) {
                throw new ObjectStorageException(ObjectStorageException.Reason.PROVIDER_NOT_FOUND,
                        "storage provider not found: " + code);
            }
            log.info("create minio client for provider code={} type={}", code, cfg.getProviderType());
            return factory.create(cfg);
        });
    }

    /**
     * 失效指定服务商缓存（配置变更后调用）。
     */
    public void invalidate(String providerCode) {
        cache.remove(providerCode);
    }

    /**
     * 失效全部缓存。
     */
    public void invalidateAll() {
        cache.clear();
    }
}