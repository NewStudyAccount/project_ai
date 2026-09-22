package com.example.auth.dto;

import java.time.LocalDateTime;

public record OauthClientVo(
        Long id,
        String clientId,
        String clientName,
        String clientType,
        String clientAuthMethod,
        String grantTypes,
        String redirectUris,
        String scopes,
        Integer requirePkce,
        Integer requireConsent,
        Integer accessTokenTtlSec,
        Integer refreshTokenTtlSec,
        String systemCode,
        String owner,
        String env,
        Integer enabled,
        String remark,
        LocalDateTime createTime
) {
}
