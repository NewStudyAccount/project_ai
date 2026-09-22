package com.example.blog.content;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** blog 业务进程入口。 */
@SpringBootApplication(scanBasePackages = {"com.example.blog.content", "com.example.blog.common"})
@MapperScan({"com.example.blog.content.mapper", "com.example.blog.common.audit"})
public class BlogContentApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogContentApplication.class, args);
    }
}
