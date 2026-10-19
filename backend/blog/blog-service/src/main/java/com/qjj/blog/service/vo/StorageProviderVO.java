package com.qjj.blog.service.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "存储服务商出参（SK 脱敏）")
public class StorageProviderVO {

    private String id;

    private String code;

    private String name;

    private String providerType;

    private String endpoint;

    private String region;

    private String bucket;

    private String accessKey;

    @Schema(description = "SK 掩码（如 ****，禁止明文回显）")
    private String secretKeyMasked;

    private Integer pathStyle;

    private String publicBaseUrl;

    private Integer status;

    private Integer isDefault;

    private String description;

    private String createTime;

    private String updateTime;
}