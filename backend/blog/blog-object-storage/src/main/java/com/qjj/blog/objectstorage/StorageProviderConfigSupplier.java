package com.qjj.blog.objectstorage;

/**
 * 服务商配置供给 SPI（object-storage-design.md §3.6）。
 * 业务侧（拥有 sys_storage_provider 表的 domain-service）提供实现，
 * 按 storage_code 解析 ProviderConfig（含已停用：enabled=false 仍返回，供存量回源）。
 *
 * 默认存在 yml 引导实现（冷启动兜底），DB 实现优先级更高（@Order 更高）。
 */
public interface StorageProviderConfigSupplier {

    /**
     * 按 code 解析服务商配置（含已停用）；不存在返回 null。
     */
    ProviderConfig resolve(String providerCode);

    /**
     * 当前启用中的默认服务商 code（is_default=1 且 status=1）。
     */
    String defaultProviderCode();

    /**
     * 该供给方的优先级：值小优先；DB 实现应返回比 yml 引导更小的值。
     */
    int getOrder();

    /**
     * 标记该供给方是否处理配置变更（DB 实现返回 true，yml 引导返回 false）。
     * 用于解析器判断是否需要查询启用列表。
     */
    boolean isRuntime();
}