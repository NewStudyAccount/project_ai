package com.qjj.blog.objectstorage;

import lombok.Data;

import java.time.Duration;

/**
 * 服务商连接参数（运行时配置模型）。
 * 非业务 Entity——不绑 MyBatis；供给方（DB SPI 实现 / yml 引导）按需注入。
 * secretKey 在此处保留**明文**（供适配层建客户端），缓存与日志禁止记录其值。
 */
@Data
public class ProviderConfig {

    /** 服务商编码（系统内唯一） */
    private String code;

    /** 展示名 */
    private String name;

    private StorageProviderType providerType;

    /** S3 endpoint（含协议与端口） */
    private String endpoint;

    /** 区域；类型要求必填时校验 */
    private String region;

    /** 默认桶 */
    private String bucket;

    private String accessKey;

    private String secretKey;

    /** 1=path-style / 0=virtual-hosted-style */
    private Integer pathStyle = 1;

    /** 公开读基址（无尾斜杠）；拼稳定 url 用 */
    private String publicBaseUrl;

    /** 0 停用 / 1 启用（控制新上传；停用仍可读/删存量） */
    private Integer status = 1;

    /** 1=默认服务商（同库仅一条） */
    private Integer isDefault = 0;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(30);
}