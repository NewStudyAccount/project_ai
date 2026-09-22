package com.example.auth.user;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * 扫描用户中心 Feign 契约。
 */
@Configuration
@EnableFeignClients(basePackages = "com.example.auth.user.feign")
public class UserApiConfiguration {
}
