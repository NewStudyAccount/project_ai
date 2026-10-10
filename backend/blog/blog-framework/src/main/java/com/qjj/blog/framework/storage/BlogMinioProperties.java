package com.qjj.blog.framework.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 对象存储配置（object-storage-design.md §6；键名 kebab-case，禁止散落 @Value）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "blog.minio")
public class BlogMinioProperties {

    private String endpoint = "";
    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "blog";
    private long uploadMaxBytes = 10L * 1024 * 1024;
    private List<String> allowedContentTypes = new ArrayList<>(List.of(
            "image/png", "image/jpeg", "image/gif", "application/pdf"));
    private Duration presignTtl = Duration.ofMinutes(10);
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);
}
