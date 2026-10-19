package com.qjj.blog.objectstorage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 上传目标服务商解析器（object-storage-design.md §3.3 / §3.6）。
 * 解析顺序：显式 storageCode（须存在且启用，否则业务错误）→ 启用中的默认服务商 → yml 引导默认。
 * 读/删存量文件按文件 storage_code 解析（走 {@link #resolveForRead}，停用允许）。
 */
@Slf4j
@Component
public class ProviderConfigResolver {

    private final List<StorageProviderConfigSupplier> suppliers;

    @Autowired
    public ProviderConfigResolver(List<StorageProviderConfigSupplier> suppliers) {
        // 优先级ASC（DB 实现 getOrder 更小排前），yml 引导兜底
        this.suppliers = new ArrayList<>(suppliers);
        this.suppliers.sort(Comparator.comparingInt(StorageProviderConfigSupplier::getOrder));
    }

    /**
     * 解析新建上传目标服务商：必须是启用中的有效服务商。
     *
     * @param storageCode 请求显式 code；空则取默认
     * @return 已启用服务商配置
     * @throws ObjectStorageException 显式 code 不存在/已停用，或无可用配置
     */
    public ProviderConfig resolveForUpload(String storageCode) {
        if (storageCode != null && !storageCode.isBlank()) {
            ProviderConfig cfg = resolveInternal(storageCode);
            if (cfg == null) {
                log.warn("upload refused: provider not found code={}", storageCode);
                throw new ObjectStorageException(ObjectStorageException.Reason.PROVIDER_NOT_FOUND,
                        "storage provider not found: " + storageCode);
            }
            if (!isEnabled(cfg)) {
                log.warn("upload refused: provider disabled code={}", storageCode);
                throw new ObjectStorageException(ObjectStorageException.Reason.PROVIDER_DISABLED,
                        "storage provider disabled: " + storageCode);
            }
            return cfg;
        }
        // 取默认：运行时 DB 优先
        for (StorageProviderConfigSupplier s : suppliers) {
            if (!s.isRuntime()) {
                continue;
            }
            ProviderConfig cfg = resolveDefaultFrom(s);
            if (cfg != null && isEnabled(cfg)) {
                return cfg;
            }
        }
        // yml 引导默认（冷启动/无库）
        for (StorageProviderConfigSupplier s : suppliers) {
            ProviderConfig cfg = resolveDefaultFrom(s);
            if (cfg != null && isEnabled(cfg)) {
                return cfg;
            }
        }
        log.error("upload refused: no storage provider available");
        throw new ObjectStorageException(ObjectStorageException.Reason.NO_PROVIDER_AVAILABLE,
                "no storage provider available");
    }

    /**
     * 读/删存量文件按其 storage_code 回源（停用仍允许读与逻辑删）。
     */
    public ProviderConfig resolveForRead(String storageCode) {
        if (storageCode == null || storageCode.isBlank()) {
            return null;
        }
        return resolveInternal(storageCode);
    }

    private ProviderConfig resolveInternal(String code) {
        for (StorageProviderConfigSupplier s : suppliers) {
            ProviderConfig cfg = s.resolve(code);
            if (cfg != null) {
                return cfg;
            }
        }
        return null;
    }

    private ProviderConfig resolveDefaultFrom(StorageProviderConfigSupplier s) {
        String defaultCode = s.defaultProviderCode();
        if (defaultCode == null || defaultCode.isBlank()) {
            return null;
        }
        return resolveInternal(defaultCode);
    }

    private boolean isEnabled(ProviderConfig cfg) {
        return cfg.getStatus() != null && cfg.getStatus() == 1;
    }

    /**
     * 默认服务商 code（运行时 DB 优先 → yml 引导）。
     */
    public String defaultProviderCode() {
        for (StorageProviderConfigSupplier s : suppliers) {
            String code = s.defaultProviderCode();
            if (code != null && !code.isBlank()) {
                return code;
            }
        }
        return null;
    }
}