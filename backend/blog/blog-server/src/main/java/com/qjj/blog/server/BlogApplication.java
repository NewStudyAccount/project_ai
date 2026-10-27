package com.qjj.blog.server;

import com.qjj.user.api.feign.UserQueryClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * blog 启动入口（启动模块 blog-server）。
 * 组件扫描 com.qjj.blog 装配 common/framework/service/api；业务代码在 blog-service。
 * MapperScan 只扫业务 mapper 包（blog-service 内），禁止扫到 Service/Feign 接口。
 * 服务名保持 blog-service（Nacos 注册名 + 网关 lb://blog-service 路由契约，模块名 ≠ 服务名）。
 */
@SpringBootApplication(scanBasePackages = "com.qjj.blog")
@EnableFeignClients(clients = UserQueryClient.class)
@MapperScan(basePackages = "com.qjj.blog.service.mapper")
public class BlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
