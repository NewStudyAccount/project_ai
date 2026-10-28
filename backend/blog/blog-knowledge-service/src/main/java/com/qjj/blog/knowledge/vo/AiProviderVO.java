package com.qjj.blog.knowledge.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * AI 服务商出参（api-design.md §3）：apiKeyMasked 永不回显明文。
 */
@Getter
@Setter
public class AiProviderVO {

    private String id;

    private String code;

    private Integer kind;

    private String type;

    private String name;

    private String baseUrl;

    private String model;

    private Integer dim;

    /** 掩码形如 sk-****abcd；空返回 "" */
    private String apiKeyMasked;

    private Integer isDefault;

    private Integer status;

    private String updateTime;
}
