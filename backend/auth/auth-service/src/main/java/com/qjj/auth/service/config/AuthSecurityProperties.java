package com.qjj.auth.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.security")
public class AuthSecurityProperties {
    private List<String> allowedOrigins = new ArrayList<>();
    private int loginMaxFailures = 5;
    private long loginLockSeconds = 900;
    private long ssoSessionTtlSeconds = 43200;
}
