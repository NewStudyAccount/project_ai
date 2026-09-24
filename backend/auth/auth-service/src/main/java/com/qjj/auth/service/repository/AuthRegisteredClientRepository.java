package com.qjj.auth.service.repository;

import com.qjj.auth.service.entity.OAuthClient;
import com.qjj.auth.service.mapper.OAuthClientMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class AuthRegisteredClientRepository implements RegisteredClientRepository {
    private final OAuthClientMapper mapper;

    public AuthRegisteredClientRepository(OAuthClientMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
    }

    @Override
    public RegisteredClient findById(String id) {
        return map(mapper.selectById(Long.parseLong(id)));
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return map(mapper.selectOne(new LambdaQueryWrapper<OAuthClient>().eq(OAuthClient::getClientId, clientId)));
    }

    private RegisteredClient map(OAuthClient client) {
        if (client == null) return null;
        RegisteredClient.Builder builder = RegisteredClient.withId(String.valueOf(client.getId()))
                .clientId(client.getClientId())
                .clientAuthenticationMethod("NONE".equalsIgnoreCase(client.getClientAuthMethod())
                        ? ClientAuthenticationMethod.NONE : ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientSecret(client.getClientSecretHash())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        Arrays.stream(client.getRedirectUris().split(",")).filter(s -> !s.isBlank())
                .forEach(builder::redirectUri);
        Arrays.stream(client.getScopes().split(",")).filter(s -> !s.isBlank()).forEach(builder::scope);
        builder.clientSettings(ClientSettings.builder()
                .requireProofKey(client.getRequirePkce() == 1)
                .requireAuthorizationConsent(client.getRequireConsent() == 1)
                .build());
        builder.tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofSeconds(client.getAccessTokenTtlSec()))
                .refreshTokenTimeToLive(Duration.ofSeconds(client.getRefreshTokenTtlSec()))
                .reuseRefreshTokens(false)
                .build());
        return builder.build();
    }
}
