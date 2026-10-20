package com.qjj.blog.objectstorage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 对象存储配置（object-storage-design.md §6.2；键名 kebab-case，禁止散落 @Value）。
 * yml 仅作冷启动引导；运行时以 sys_storage_provider 库表为准（DB 优先）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "blog.object-storage")
public class ObjectStorageProperties {

    /** 引导默认服务商 code（冷启动用；运行时被 DB 默认覆盖） */
    private String defaultProvider;

    private List<Provider> providers = new ArrayList<>();

    private List<String> allowedContentTypes = new ArrayList<>(List.of(
            "image/png", "image/jpeg", "image/gif", "application/pdf"));

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(30);

    @Getter
    @Setter
    public static class Provider {
        private String code;
        private String providerType;
        private String endpoint;
        private String region;
        private String bucket;
        private String accessKey;
        private String secretKey;
        private Integer pathStyle = 1;
        private String publicBaseUrl;
    }
}