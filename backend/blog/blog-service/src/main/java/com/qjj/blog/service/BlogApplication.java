package com.qjj.blog.service;

import com.qjj.user.api.feign.UserQueryClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * blog 可运行入口。组件扫描 com.qjj.blog 装配 common/framework/service/api。
 * MapperScan 只扫 mapper 包，禁止扫到 Service/Feign 接口。
 */
@SpringBootApplication(scanBasePackages = "com.qjj.blog")
@EnableFeignClients(clients = UserQueryClient.class)
@MapperScan(basePackages = "com.qjj.blog.service.mapper")
public class BlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
