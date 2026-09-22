package com.example.auth.dto;

public record ClientCreateRequest(
        String clientId,
        String clientName,
        String clientType,
        String clientAuthMethod,
        String grantTypes,
        String redirectUris,
        String scopes,
        Boolean requirePkce,
        Boolean requireConsent,
        Integer accessTokenTtlSec,
        Integer refreshTokenTtlSec,
        String systemCode,
        String owner,
        String env,
        String remark
) {
}
