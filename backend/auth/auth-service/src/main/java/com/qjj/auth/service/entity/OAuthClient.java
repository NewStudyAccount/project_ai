package com.qjj.auth.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("oauth_client")
public class OAuthClient extends BaseEntity {
    private String clientId;
    private String clientSecretHash;
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
