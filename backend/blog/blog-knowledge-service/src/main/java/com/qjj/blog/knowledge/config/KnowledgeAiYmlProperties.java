package com.qjj.blog.knowledge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 服务商 yml 冷启动引导（blog.ai.providers[]；DB 优先——ai_provider 库表生效后 yml 仅补 base_url/api_key 空值）。
 * api_key 禁止明文入仓：经管理端录入或环境注入。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "blog.ai")
public class KnowledgeAiYmlProperties {

    private List<Provider> providers = new ArrayList<>();

    @Getter
    @Setter
    public static class Provider {
        private String code;
        private String baseUrl;
        private String apiKey;
    }
}
