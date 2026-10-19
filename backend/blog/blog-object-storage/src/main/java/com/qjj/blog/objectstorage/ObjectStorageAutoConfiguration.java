package com.qjj.blog.objectstorage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储自动装配（object-storage-design.md §3）。
 * 主密钥来源由 secret-crypto 配置（默认环境变量 OBJECT_STORAGE_SK_KEY）。
 * SecretKeyCipher 全局唯一，避免每个 provider 单独建。
 *
 * <p>主密钥懒加载：启动时不强校验环境变量，仅在首次加解密 DB 服务商密文 SK 时解析。
 * 本地 yml 引导（明文 SK）无需设环境变量即可启动。
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecretKeyCipher.class)
    public SecretKeyCipher secretKeyCipher(ObjectStorageProperties props) {
        return new SecretKeyCipher(props.getSecretCrypto());
    }
}
