package com.qjj.auth.service.vo;

import lombok.Data;

@Data
public class ClientVO {
    private String id;
    private String clientId;
    private String clientName;
    private String clientType;
    private String clientAuthMethod;
    private String grantTypes;
    private String redirectUris;
    private String scopes;
    private Integer requirePkce;
    private Integer requireConsent;
    private Integer reuseRefreshTokens;
    private Integer accessTokenTtlSec;
    private Integer refreshTokenTtlSec;
    private String systemCode;
    private String owner;
    private String env;
    private Integer enabled;
    private String remark;
}
