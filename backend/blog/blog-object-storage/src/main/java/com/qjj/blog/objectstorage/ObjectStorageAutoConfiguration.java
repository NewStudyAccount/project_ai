package com.qjj.blog.objectstorage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储自动装配（object-storage-design.md §3）。
 *
 * <p>注：SK 加密层（{@code SecretKeyCipher} / {@code secret-crypto} 配置）已于变更
 * {@code blog-object-storage-remove-sk-encryption} 移除；dev/test 阶段 SK 明文落库。
 * 生产部署前须另开独立加密变更重引入加密层。
 *
 * <p>本类仅保留 {@link EnableConfigurationProperties} 以将 {@link ObjectStorageProperties}
 * 注册为 Spring bean（{@code YmlStorageProviderSupplier} 构造器注入依赖此）。
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageAutoConfiguration {
}
