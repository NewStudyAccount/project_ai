package com.qjj.blog.framework.core;

import com.qjj.blog.framework.markdown.MarkdownRenderer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * framework 统一装配入口：需配置组件只在此落地（CLAUDE.md 2.1 / 5.4）；
 * 各支撑配置类经组件扫描（应用侧 scanBasePackages=com.qjj.user）注册，业务模块禁止自建横切实现。
 */
@Configuration
public class FrameworkConfiguration {

    @Bean
    public UserContextFilter userContextFilter() {
        return new UserContextFilter();
    }

    /**
     * 服务端 Markdown→HTML 渲染器（business-design §E.5 唯一转换点；
     * escapeHtml/sanitizeUrls 固化于实现，业务禁止另配）。
     */
    @Bean
    public MarkdownRenderer markdownRenderer() {
        return new MarkdownRenderer();
    }
}
