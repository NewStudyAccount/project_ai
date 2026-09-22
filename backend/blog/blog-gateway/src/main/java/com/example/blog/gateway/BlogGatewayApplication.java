package com.example.blog.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/** blog 网关：仅 JWT AuthN + 反代，不需要数据库。 */
@SpringBootApplication(
        scanBasePackages = "com.example.blog.gateway",
        exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
public class BlogGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogGatewayApplication.class, args);
    }
}
