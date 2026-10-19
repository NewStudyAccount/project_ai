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

    private long uploadMaxBytes = 10L * 1024 * 1024;

    private List<String> allowedContentTypes = new ArrayList<>(List.of(
            "image/png", "image/jpeg", "image/gif", "application/pdf"));

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(30);

    private SecretCrypto secretCrypto = new SecretCrypto();

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

    @Getter
    @Setter
    public static class SecretCrypto {
        /** 主密钥来源：env / nacos */
        private String keySource = "env";
        /** 环境变量名（默认 OBJECT_STORAGE_SK_KEY） */
        private String keyEnv = "OBJECT_STORAGE_SK_KEY";
    }
}