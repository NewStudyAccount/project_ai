package com.qjj.blog.knowledge.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * AI 服务商连通测试出参（api-design.md §3）
 */
@Getter
@Setter
public class AiProviderTestResultVO {

    private Boolean ok;

    private Long latencyMs;

    private String message;
}
