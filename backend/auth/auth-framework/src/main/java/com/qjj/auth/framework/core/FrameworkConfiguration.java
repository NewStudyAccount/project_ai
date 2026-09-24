package com.qjj.auth.framework.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FrameworkConfiguration {
    @Bean
    public AuthContextFilter authContextFilter() {
        return new AuthContextFilter();
    }
}
