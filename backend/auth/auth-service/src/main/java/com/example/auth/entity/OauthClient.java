package com.example.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** OAuth Client 注册与运营；驱动 RegisteredClient。 */
@TableName("oauth_client")
public class OauthClient {

    @TableId(type = IdType.INPUT)
    private Long id;

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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecretHash() { return clientSecretHash; }
    public void setClientSecretHash(String clientSecretHash) { this.clientSecretHash = clientSecretHash; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getClientType() { return clientType; }
    public void setClientType(String clientType) { this.clientType = clientType; }
    public String getClientAuthMethod() { return clientAuthMethod; }
    public void setClientAuthMethod(String clientAuthMethod) { this.clientAuthMethod = clientAuthMethod; }
    public String getGrantTypes() { return grantTypes; }
    public void setGrantTypes(String grantTypes) { this.grantTypes = grantTypes; }
    public String getRedirectUris() { return redirectUris; }
    public void setRedirectUris(String redirectUris) { this.redirectUris = redirectUris; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public Integer getRequirePkce() { return requirePkce; }
    public void setRequirePkce(Integer requirePkce) { this.requirePkce = requirePkce; }
    public Integer getRequireConsent() { return requireConsent; }
    public void setRequireConsent(Integer requireConsent) { this.requireConsent = requireConsent; }
    public Integer getReuseRefreshTokens() { return reuseRefreshTokens; }
    public void setReuseRefreshTokens(Integer reuseRefreshTokens) { this.reuseRefreshTokens = reuseRefreshTokens; }
    public Integer getAccessTokenTtlSec() { return accessTokenTtlSec; }
    public void setAccessTokenTtlSec(Integer accessTokenTtlSec) { this.accessTokenTtlSec = accessTokenTtlSec; }
    public Integer getRefreshTokenTtlSec() { return refreshTokenTtlSec; }
    public void setRefreshTokenTtlSec(Integer refreshTokenTtlSec) { this.refreshTokenTtlSec = refreshTokenTtlSec; }
    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
