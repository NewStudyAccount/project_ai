package com.qjj.blog.api.feign;

import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class BlogApiFeignConfiguration {

    @Bean
    public Request.Options feignOptions() {
        // 连接 1s / 读取 3s（设计稿 §3 示例值，可配）
        return new Request.Options(1, TimeUnit.SECONDS, 3, TimeUnit.SECONDS, true);
    }
}
