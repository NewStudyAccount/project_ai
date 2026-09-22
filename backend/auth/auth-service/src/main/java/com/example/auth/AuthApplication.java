package com.example.auth;

import com.example.auth.user.UserApiConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * 统一认证中心入口。
 */
@SpringBootApplication
@MapperScan("com.example.auth.mapper")
@EnableFeignClients(basePackages = "com.example.auth.user.feign")
@Import(UserApiConfiguration.class)
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
