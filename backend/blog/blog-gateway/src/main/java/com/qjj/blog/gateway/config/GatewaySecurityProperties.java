package com.qjj.blog.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "blog-gateway.security")
public class GatewaySecurityProperties {

    private boolean localPassThrough = true;

    private String jwkSetUri = "";

    private List<String> publicPaths = List.of("/actuator/health", "/internal/**");

    private List<String> trustedOrigins = List.of("http://localhost:5100", "http://127.0.0.1:5100");
}

