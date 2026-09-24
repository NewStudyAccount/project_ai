package com.qjj.user.service;

import com.qjj.user.api.feign.UserQueryClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户中心可运行入口。组件扫描 com.qjj.user 装配 common/framework/service/api；
 * MapperScan 只扫 mapper 包，禁止扫到 Service/Feign 接口。
 */
@SpringBootApplication(scanBasePackages = "com.qjj.user")
@EnableFeignClients(clients = UserQueryClient.class)
@MapperScan(basePackages = "com.qjj.user.service.mapper")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
