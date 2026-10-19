package com.qjj.blog.service.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

/**
 * 服务商类型枚举（object-storage-design.md §3.2.2）。
 * 库里存 code、界面显示 desc、接口只传 code（CLAUDE.md 6.13）。
 */
@Getter
public enum StorageProviderTypeEnum implements BaseEnum<String> {

    MINIO("MINIO", "MinIO 自建"),
    AWS_S3("AWS_S3", "AWS S3"),
    ALIYUN_OSS("ALIYUN_OSS", "阿里云 OSS（S3 兼容）"),
    TENCENT_COS("TENCENT_COS", "腾讯云 COS（S3 兼容）"),
    HUAWEI_OBS("HUAWEI_OBS", "华为云 OBS（S3 兼容）"),
    CUSTOM_S3("CUSTOM_S3", "其它 S3 兼容实现");

    private final String code;

    private final String desc;

    StorageProviderTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static StorageProviderTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (StorageProviderTypeEnum t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        return null;
    }

    /** 该类型是否强制 region */
    public boolean isRegionRequired() {
        return this != MINIO && this != CUSTOM_S3;
    }
}