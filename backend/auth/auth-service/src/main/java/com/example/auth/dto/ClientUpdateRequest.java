package com.example.auth.dto;

public record ClientUpdateRequest(
        String clientName,
        String grantTypes,
        String redirectUris,
        String scopes,
        Boolean requirePkce,
        Integer accessTokenTtlSec,
        Integer refreshTokenTtlSec,
        String systemCode,
        String owner,
        String remark
) {
}
