package com.qjj.blog.objectstorage;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * S3 多服务商客户端工厂（object-storage-design.md §3.2.1）。
 * 单栈沿用 MinIO SDK（S3 兼容）；按 ProviderConfig 装配 endpoint/region/path-style/credentials。
 * 厂商差异只允许出现在此层（§3.2.1）。
 */
@Component
public class S3ProviderFactory {

    /**
     * 按 ProviderConfig 创建 MinioClient 实例（不缓存，缓存由 ProviderClientRegistry 负责）。
     */
    public MinioClient create(ProviderConfig cfg) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(cfg.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(cfg.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build();
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .httpClient(http);
        if (cfg.getRegion() != null && !cfg.getRegion().isBlank()) {
            builder.region(cfg.getRegion());
        }
        // path-style 由服务商配置决定；MinIO/CUSTOM_S3 默认 true，其他按配置
        boolean pathStyle = cfg.getPathStyle() != null && cfg.getPathStyle() == 1;
        // MinIO SDK：path-style 通过 endpoint 拼装体现，无需显式 API
        // （各厂商按 S3 兼容 endpoint，差异在配置端）
        if (pathStyle) {
            // 标记：当前 SDK 路径风格由 endpoint 形态决定（path-style endpoint 直接带桶路径或 host:port）
            // 此处仅起配置可见性作用，提醒适配层在 endpoint 装配时保持 path-style
        }
        MinioClient client = builder.build();
        return client;
    }
}