package com.qjj.blog.objectstorage;

import lombok.Getter;

/**
 * S3 多服务商类型（object-storage-design.md §3.2.2）。
 * 只决定适配层推荐默认参数（path-style、region 是否必填等）；厂商差异只允许出现在适配层。
 */
@Getter
public enum StorageProviderType {

    MINIO("MINIO", true, false, "自建/私有部署 MinIO"),
    AWS_S3("AWS_S3", false, true, "AWS S3"),
    ALIYUN_OSS("ALIYUN_OSS", false, true, "阿里云 OSS（S3 兼容）"),
    TENCENT_COS("TENCENT_COS", false, true, "腾讯云 COS（S3 兼容）"),
    HUAWEI_OBS("HUAWEI_OBS", false, true, "华为云 OBS（S3 兼容）"),
    CUSTOM_S3("CUSTOM_S3", true, false, "其它 S3 兼容实现");

    private final String code;
    /** 推荐默认 path-style */
    private final boolean pathStyleDefault;
    /** 类型是否强制 region */
    private final boolean regionRequired;
    private final String desc;

    StorageProviderType(String code, boolean pathStyleDefault, boolean regionRequired, String desc) {
        this.code = code;
        this.pathStyleDefault = pathStyleDefault;
        this.regionRequired = regionRequired;
        this.desc = desc;
    }

    public static StorageProviderType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (StorageProviderType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        return null;
    }
}