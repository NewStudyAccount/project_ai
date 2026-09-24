package com.qjj.user.framework.core;

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
}
