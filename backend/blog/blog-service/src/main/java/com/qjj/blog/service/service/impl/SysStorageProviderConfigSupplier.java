package com.qjj.blog.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qjj.blog.objectstorage.ProviderConfig;
import com.qjj.blog.objectstorage.StorageProviderConfigSupplier;
import com.qjj.blog.objectstorage.StorageProviderType;
import com.qjj.blog.service.entity.SysStorageProvider;
import com.qjj.blog.service.mapper.SysStorageProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 服务商配置供给方·DB 实现（运行时真相源，object-storage-design.md §6.1）。
 * 优先级高于 yml 引导（getOrder=10 < 1000）。
 *
 * <p>注：SK 加密层已移除（变更 blog-object-storage-remove-sk-encryption）；
 * dev/test 阶段 SK 明文落库，读取时直接取实体 secretKey，不经过解密。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysStorageProviderConfigSupplier implements StorageProviderConfigSupplier {

    private final SysStorageProviderMapper mapper;

    @Override
    public ProviderConfig resolve(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return null;
        }
        SysStorageProvider entity = mapper.selectOne(new LambdaQueryWrapper<SysStorageProvider>()
                .eq(SysStorageProvider::getCode, providerCode));
        if (entity == null) {
            return null;
        }
        return toConfig(entity);
    }

    @Override
    public String defaultProviderCode() {
        // 启用中的默认（is_default=1 且 status=1）
        SysStorageProvider entity = mapper.selectOne(new LambdaQueryWrapper<SysStorageProvider>()
                .eq(SysStorageProvider::getIsDefault, 1)
                .eq(SysStorageProvider::getStatus, 1)
                .last("LIMIT 1"));
        if (entity != null) {
            return entity.getCode();
        }
        // 退化为任意启用中的服务商（便于无显式默认时仍可解析）
        SysStorageProvider any = mapper.selectOne(new LambdaQueryWrapper<SysStorageProvider>()
                .eq(SysStorageProvider::getStatus, 1)
                .orderByAsc(SysStorageProvider::getId)
                .last("LIMIT 1"));
        return any != null ? any.getCode() : null;
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public boolean isRuntime() {
        return true;
    }

    private ProviderConfig toConfig(SysStorageProvider entity) {
        ProviderConfig cfg = new ProviderConfig();
        cfg.setCode(entity.getCode());
        cfg.setName(entity.getName());
        cfg.setProviderType(StorageProviderType.fromCode(entity.getProviderType()));
        cfg.setEndpoint(entity.getEndpoint());
        cfg.setRegion(entity.getRegion());
        cfg.setBucket(entity.getBucket());
        cfg.setAccessKey(entity.getAccessKey());
        // dev/test 阶段 SK 明文落库，直接取实体值；生产重引入加密后改回 cipher.decrypt
        cfg.setSecretKey(entity.getSecretKey());
        cfg.setPathStyle(entity.getPathStyle() != null ? entity.getPathStyle() : 1);
        cfg.setPublicBaseUrl(entity.getPublicBaseUrl());
        cfg.setStatus(entity.getStatus());
        cfg.setIsDefault(entity.getIsDefault());
        cfg.setConnectTimeout(Duration.ofSeconds(5));
        cfg.setReadTimeout(Duration.ofSeconds(30));
        return cfg;
    }
}