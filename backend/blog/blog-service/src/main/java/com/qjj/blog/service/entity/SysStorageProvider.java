package com.qjj.blog.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 对象存储服务商配置（sys_storage_provider；object-storage-design.md §5.2）。
 * 拥有表：blog-service。object-storage 模块不持此 Entity。
 */
@Getter
@Setter
@TableName("sys_storage_provider")
public class SysStorageProvider extends BaseEntity {

    /** 服务商编码，系统内唯一 */
    private String code;

    private String name;

    /** MINIO/AWS_S3/ALIYUN_OSS/TENCENT_COS/HUAWEI_OBS/CUSTOM_S3 */
    private String providerType;

    private String endpoint;

    private String region;

    private String bucket;

    private String accessKey;

    /** SK 密文（AES-GCM） */
    private String secretKey;

    /** 1=path-style / 0=virtual-hosted-style */
    private Integer pathStyle;

    private String publicBaseUrl;

    /** 0 停用 / 1 启用 */
    private Integer status;

    /** 1=默认服务商（同库仅一条） */
    private Integer isDefault;

    private String description;
}