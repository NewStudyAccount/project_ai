package com.qjj.blog.framework.storage;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * MinIO 装配（需连接中间件，只进 framework；object-storage-design.md §3.1）。
 */
@Configuration
@EnableConfigurationProperties(BlogMinioProperties.class)
@ConditionalOnProperty(prefix = "blog.minio", name = "endpoint")
public class ObjectStorageConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient(BlogMinioProperties props) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(props.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(props.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build();
        return MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .httpClient(http)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStorageClient.class)
    public ObjectStorageClient objectStorageClient(MinioClient minioClient, BlogMinioProperties props) {
        return new ObjectStorageClient(minioClient, props);
    }
}
