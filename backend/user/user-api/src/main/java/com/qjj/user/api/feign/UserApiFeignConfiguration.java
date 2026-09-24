package com.qjj.user.api.feign;

import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * user-api 统一 Feign 超时约束，禁止各调用方各写各的超时。
 */
@Configuration
public class UserApiFeignConfiguration {

    @Bean
    public Request.Options userQueryRequestOptions() {
        return new Request.Options(2, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
    }
}
