package com.qjj.auth.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClientRequest {
    @NotBlank
    @Size(max = 64)
    private String clientId;
    @Size(max = 100)
    private String clientSecret;
    @NotBlank
    @Size(max = 64)
    private String clientName;
    private String clientType = "PUBLIC";
    private String clientAuthMethod = "NONE";
    @NotBlank
    private String grantTypes = "authorization_code,refresh_token";
    @NotBlank
    private String redirectUris;
    private String scopes = "openid,profile";
    private Integer requirePkce = 1;
    private Integer requireConsent = 0;
    private Integer reuseRefreshTokens = 0;
    private Integer accessTokenTtlSec = 600;
    private Integer refreshTokenTtlSec = 604800;
    private String systemCode = "";
    private String owner = "";
    private String env = "";
    private Integer enabled = 1;
    private String remark = "";
}
