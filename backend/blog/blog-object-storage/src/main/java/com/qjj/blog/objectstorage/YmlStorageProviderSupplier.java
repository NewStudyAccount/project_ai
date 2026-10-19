package com.qjj.blog.objectstorage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * yml 引导服务商供给方（冷启动兜底）。
 * getOrder 大于 DB 实现，确保运行时 DB 优先。
 */
@Component
public class YmlStorageProviderSupplier implements StorageProviderConfigSupplier {

    private final ObjectStorageProperties props;

    @Autowired
    public YmlStorageProviderSupplier(ObjectStorageProperties props) {
        this.props = props;
    }

    @Override
    public ProviderConfig resolve(String providerCode) {
        if (providerCode == null || props == null || props.getProviders() == null) {
            return null;
        }
        return props.getProviders().stream()
                .filter(p -> providerCode.equals(p.getCode()))
                .findFirst()
                .map(this::toConfig)
                .orElse(null);
    }

    @Override
    public String defaultProviderCode() {
        return props.getDefaultProvider();
    }

    @Override
    public int getOrder() {
        // yml 引导优先级最低（DB 实现应返回更小值）
        return 1000;
    }

    @Override
    public boolean isRuntime() {
        return false;
    }

    private ProviderConfig toConfig(ObjectStorageProperties.Provider p) {
        ProviderConfig cfg = new ProviderConfig();
        cfg.setCode(p.getCode());
        cfg.setProviderType(StorageProviderType.fromCode(p.getProviderType()));
        cfg.setEndpoint(p.getEndpoint());
        cfg.setRegion(p.getRegion());
        cfg.setBucket(p.getBucket());
        cfg.setAccessKey(p.getAccessKey());
        cfg.setSecretKey(p.getSecretKey());
        cfg.setPathStyle(p.getPathStyle());
        cfg.setPublicBaseUrl(p.getPublicBaseUrl());
        cfg.setStatus(1);
        cfg.setIsDefault(p.getCode().equals(props.getDefaultProvider()) ? 1 : 0);
        cfg.setConnectTimeout(props.getConnectTimeout());
        cfg.setReadTimeout(props.getReadTimeout());
        return cfg;
    }
}