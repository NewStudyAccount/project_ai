package com.qjj.auth.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.auth.common.enums.AuthErrorCodeEnum;
import com.qjj.auth.common.exception.BizException;
import com.qjj.auth.common.result.PageResult;
import com.qjj.auth.framework.core.IdGenerator;
import com.qjj.auth.framework.core.MybatisSupportConfiguration;
import com.qjj.auth.service.dto.ClientRequest;
import com.qjj.auth.service.entity.OAuthClient;
import com.qjj.auth.service.mapper.OAuthClientMapper;
import com.qjj.auth.service.vo.ClientSecretVO;
import com.qjj.auth.service.vo.ClientVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {
    private final OAuthClientMapper clientMapper;
    private final IdGenerator idGenerator;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public PageResult<ClientVO> page(long current, long size, String clientId, String systemCode, Integer enabled) {
        LambdaQueryWrapper<OAuthClient> wrapper = new LambdaQueryWrapper<OAuthClient>()
                .eq(clientId != null && !clientId.isBlank(), OAuthClient::getClientId, clientId)
                .eq(systemCode != null && !systemCode.isBlank(), OAuthClient::getSystemCode, systemCode)
                .eq(enabled != null, OAuthClient::getEnabled, enabled)
                .orderByDesc(OAuthClient::getCreateTime);
        Page<OAuthClient> page = clientMapper.selectPage(MybatisSupportConfiguration.toPage(current, size), wrapper);
        return MybatisSupportConfiguration.toPageResult(page, this::toVO);
    }

    @Override
    @Transactional
    public ClientSecretVO create(ClientRequest request) {
        validate(request);
        Long exists = clientMapper.selectCount(new LambdaQueryWrapper<OAuthClient>().eq(OAuthClient::getClientId, request.getClientId()));
        if (exists != null && exists > 0) throw new BizException(AuthErrorCodeEnum.CLIENT_ID_EXISTS);
        OAuthClient client = apply(new OAuthClient(), request);
        client.setId(idGenerator.nextId());
        String plainSecret = null;
        if ("CONFIDENTIAL".equals(request.getClientType())) {
            plainSecret = request.getClientSecret() == null || request.getClientSecret().isBlank()
                    ? UUID.randomUUID().toString().replace("-", "") : request.getClientSecret();
            client.setClientSecretHash(passwordEncoder.encode(plainSecret));
        }
        clientMapper.insert(client);
        auditService.record("CLIENT_UPDATE", "oauth_client", String.valueOf(client.getId()), "创建客户端");
        ClientSecretVO vo = new ClientSecretVO();
        vo.setId(String.valueOf(client.getId()));
        vo.setClientId(client.getClientId());
        vo.setClientSecret(plainSecret == null ? "" : plainSecret);
        return vo;
    }

    @Override
    @Transactional
    public ClientVO update(String id, ClientRequest request) {
        validate(request);
        OAuthClient client = require(id);
        apply(client, request);
        clientMapper.updateById(client);
        auditService.record("CLIENT_UPDATE", "oauth_client", id, "更新客户端");
        return toVO(client);
    }

    @Override
    @Transactional
    public ClientVO updateStatus(String id, Integer enabled) {
        OAuthClient client = require(id);
        client.setEnabled(enabled);
        clientMapper.updateById(client);
        auditService.record("CLIENT_UPDATE", "oauth_client", id, "更新客户端状态");
        return toVO(client);
    }

    @Override
    @Transactional
    public ClientSecretVO resetSecret(String id) {
        OAuthClient client = require(id);
        String secret = UUID.randomUUID().toString().replace("-", "");
        client.setClientSecretHash(passwordEncoder.encode(secret));
        clientMapper.updateById(client);
        auditService.record("CLIENT_SECRET_RESET", "oauth_client", id, "重置客户端密钥");
        ClientSecretVO vo = new ClientSecretVO();
        vo.setId(id);
        vo.setClientId(client.getClientId());
        vo.setClientSecret(secret);
        return vo;
    }

    @Override
    @Transactional
    public void delete(String id) {
        clientMapper.deleteById(Long.parseLong(id));
        auditService.record("CLIENT_UPDATE", "oauth_client", id, "删除客户端");
    }

    private void validate(ClientRequest request) {
        if (request.getReuseRefreshTokens() != null && request.getReuseRefreshTokens() != 0) {
            throw new BizException(com.qjj.auth.common.enums.SysErrorCodeEnum.VALIDATION_FAILED);
        }
        if (request.getAccessTokenTtlSec() < 300 || request.getAccessTokenTtlSec() > 900) {
            throw new BizException(com.qjj.auth.common.enums.SysErrorCodeEnum.VALIDATION_FAILED);
        }
    }

    private OAuthClient apply(OAuthClient client, ClientRequest request) {
        client.setClientId(request.getClientId());
        client.setClientName(request.getClientName());
        client.setClientType(request.getClientType());
        client.setClientAuthMethod(request.getClientAuthMethod());
        client.setGrantTypes(request.getGrantTypes());
        client.setRedirectUris(request.getRedirectUris());
        client.setScopes(request.getScopes());
        client.setRequirePkce(request.getRequirePkce());
        client.setRequireConsent(request.getRequireConsent());
        client.setReuseRefreshTokens(0);
        client.setAccessTokenTtlSec(request.getAccessTokenTtlSec());
        client.setRefreshTokenTtlSec(request.getRefreshTokenTtlSec());
        client.setSystemCode(request.getSystemCode());
        client.setOwner(request.getOwner());
        client.setEnv(request.getEnv());
        client.setEnabled(request.getEnabled());
        client.setRemark(request.getRemark());
        return client;
    }

    private OAuthClient require(String id) {
        OAuthClient client = clientMapper.selectById(Long.parseLong(id));
        if (client == null) throw new BizException(AuthErrorCodeEnum.CLIENT_NOT_FOUND);
        return client;
    }

    private ClientVO toVO(OAuthClient client) {
        ClientVO vo = new ClientVO();
        vo.setId(String.valueOf(client.getId()));
        vo.setClientId(client.getClientId());
        vo.setClientName(client.getClientName());
        vo.setClientType(client.getClientType());
        vo.setClientAuthMethod(client.getClientAuthMethod());
        vo.setGrantTypes(client.getGrantTypes());
        vo.setRedirectUris(client.getRedirectUris());
        vo.setScopes(client.getScopes());
        vo.setRequirePkce(client.getRequirePkce());
        vo.setRequireConsent(client.getRequireConsent());
        vo.setReuseRefreshTokens(client.getReuseRefreshTokens());
        vo.setAccessTokenTtlSec(client.getAccessTokenTtlSec());
        vo.setRefreshTokenTtlSec(client.getRefreshTokenTtlSec());
        vo.setSystemCode(client.getSystemCode());
        vo.setOwner(client.getOwner());
        vo.setEnv(client.getEnv());
        vo.setEnabled(client.getEnabled());
        vo.setRemark(client.getRemark());
        return vo;
    }
}
