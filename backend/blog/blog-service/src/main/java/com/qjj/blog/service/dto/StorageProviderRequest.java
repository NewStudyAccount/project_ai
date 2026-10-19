package com.qjj.blog.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "存储服务商创建/更新入参")
public class StorageProviderRequest {

    @Schema(description = "服务商编码，系统内唯一", example = "minio-local")
    @NotBlank(message = "code 不能为空")
    @Size(max = 64, message = "code 长度不能超过 64")
    private String code;

    @Schema(description = "展示名")
    @Size(max = 128, message = "name 长度不能超过 128")
    private String name;

    @Schema(description = "MINIO/AWS_S3/ALIYUN_OSS/TENCENT_COS/HUAWEI_OBS/CUSTOM_S3", example = "MINIO")
    @NotBlank(message = "providerType 不能为空")
    private String providerType;

    @Schema(description = "S3 endpoint（含协议与端口）")
    @NotBlank(message = "endpoint 不能为空")
    @Size(max = 255, message = "endpoint 长度不能超过 255")
    private String endpoint;

    @Schema(description = "区域；AWS/OSS/COS/OBS 必填，MinIO 可空")
    @Size(max = 64, message = "region 长度不能超过 64")
    private String region;

    @Schema(description = "默认桶")
    @NotBlank(message = "bucket 不能为空")
    @Size(max = 64, message = "bucket 长度不能超过 64")
    private String bucket;

    @Schema(description = "AccessKey")
    @NotBlank(message = "accessKey 不能为空")
    @Size(max = 128, message = "accessKey 长度不能超过 128")
    private String accessKey;

    @Schema(description = "SecretKey：创建必填；更新传空表示不修改，非空则加密存储；出参永远脱敏")
    @Size(max = 255, message = "secretKey 长度不能超过 255")
    private String secretKey;

    @Schema(description = "1=path-style / 0=virtual-hosted-style；默认 1")
    private Integer pathStyle = 1;

    @Schema(description = "公开读基址（无尾斜杠）")
    @NotBlank(message = "publicBaseUrl 不能为空")
    @Size(max = 255, message = "publicBaseUrl 长度不能超过 255")
    private String publicBaseUrl;

    @Schema(description = "备注")
    @Size(max = 255, message = "description 长度不能超过 255")
    private String description;

    @Schema(description = "0 停用 / 1 启用；默认 1")
    private Integer status = 1;

    @Schema(description = "是否设为默认；1=默认服务商")
    private Integer isDefault = 0;
}