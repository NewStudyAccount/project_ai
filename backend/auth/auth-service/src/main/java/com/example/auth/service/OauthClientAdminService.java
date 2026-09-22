package com.example.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.auth.common.BizException;
import com.example.auth.common.ErrorCode;
import com.example.auth.common.PageQuery;
import com.example.auth.common.PageResult;
import com.example.auth.common.enums.AuditActionEnum;
import com.example.auth.common.enums.ClientTypeEnum;
import com.example.auth.dto.ClientCreateRequest;
import com.example.auth.dto.ClientUpdateRequest;
import com.example.auth.dto.OauthClientVo;
import com.example.auth.dto.SecretResetVo;
import com.example.auth.entity.OauthClient;
import com.example.auth.framework.IdService;
import com.example.auth.mapper.OauthClientMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth Client 运营；secret 仅创建/重置明文一次。
 */
@Service
public class OauthClientAdminService {

    private final OauthClientMapper clientMapper;
    private final IdService idService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public OauthClientAdminService(OauthClientMapper clientMapper, IdService idService, AuditService auditService) {
        this.clientMapper = clientMapper;
        this.idService = idService;
        this.auditService = auditService;
    }

    @Transactional
    public SecretResetVo create(ClientCreateRequest request, Long operatorId, String ip) {
        String clientId = request.clientId() == null || request.clientId().isBlank()
                ? "cli-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : request.clientId().trim();
        Long exists = clientMapper.selectCount(new LambdaQueryWrapper<OauthClient>()
                .eq(OauthClient::getClientId, clientId));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.CLIENT_ID_EXISTS);
        }
        OauthClient row = new OauthClient();
        row.setId(idService.nextId("oauth_client"));
        row.setClientId(clientId);
        row.setClientName(request.clientName() == null ? "" : request.clientName());
        row.setClientType(request.clientType() == null ? ClientTypeEnum.PUBLIC.getCode() : request.clientType());
        row.setClientAuthMethod(request.clientAuthMethod() == null ? "NONE" : request.clientAuthMethod());
        row.setGrantTypes(request.grantTypes() == null ? "authorization_code,refresh_token" : request.grantTypes());
        row.setRedirectUris(request.redirectUris() == null ? "" : request.redirectUris());
        row.setScopes(request.scopes() == null ? "openid,profile" : request.scopes());
        row.setRequirePkce(request.requirePkce() == null || request.requirePkce() ? 1 : 0);
        row.setRequireConsent(Boolean.TRUE.equals(request.requireConsent()) ? 1 : 0);
        row.setReuseRefreshTokens(0);
        row.setAccessTokenTtlSec(request.accessTokenTtlSec() == null ? 600 : request.accessTokenTtlSec());
        row.setRefreshTokenTtlSec(request.refreshTokenTtlSec() == null ? 604800 : request.refreshTokenTtlSec());
        row.setSystemCode(request.systemCode() == null ? "" : request.systemCode());
        row.setOwner(request.owner() == null ? "" : request.owner());
        row.setEnv(request.env() == null ? "dev" : request.env());
        row.setEnabled(1);
        row.setRemark(request.remark() == null ? "" : request.remark());

        String plainSecret = null;
        if (ClientTypeEnum.CONFIDENTIAL.getCode().equals(row.getClientType())) {
            plainSecret = generateSecret();
            row.setClientSecretHash(passwordEncoder.encode(plainSecret));
            row.setClientAuthMethod("client_secret_basic");
        } else {
            row.setClientSecretHash(null);
            row.setClientAuthMethod("NONE");
            row.setRequirePkce(1);
        }
        clientMapper.insert(row);
        auditService.record(AuditActionEnum.CLIENT_CREATE, operatorId, "oauth_client", clientId,
                "created", ip);
        return new SecretResetVo(toVo(row), plainSecret);
    }

    @Transactional
    public void update(String clientId, ClientUpdateRequest request, Long operatorId, String ip) {
        OauthClient row = requireByClientId(clientId);
        if (request.clientName() != null) {
            row.setClientName(request.clientName());
        }
        if (request.redirectUris() != null) {
            row.setRedirectUris(request.redirectUris());
        }
        if (request.scopes() != null) {
            row.setScopes(request.scopes());
        }
        if (request.grantTypes() != null) {
            row.setGrantTypes(request.grantTypes());
        }
        if (request.accessTokenTtlSec() != null) {
            row.setAccessTokenTtlSec(request.accessTokenTtlSec());
        }
        if (request.refreshTokenTtlSec() != null) {
            row.setRefreshTokenTtlSec(request.refreshTokenTtlSec());
        }
        if (request.requirePkce() != null) {
            row.setRequirePkce(request.requirePkce() ? 1 : 0);
        }
        if (request.owner() != null) {
            row.setOwner(request.owner());
        }
        if (request.remark() != null) {
            row.setRemark(request.remark());
        }
        if (request.systemCode() != null) {
            row.setSystemCode(request.systemCode());
        }
        clientMapper.updateById(row);
        auditService.record(AuditActionEnum.CLIENT_UPDATE, operatorId, "oauth_client", clientId,
                "updated", ip);
    }

    @Transactional
    public void setEnabled(String clientId, boolean enabled, Long operatorId, String ip) {
        OauthClient row = requireByClientId(clientId);
        row.setEnabled(enabled ? 1 : 0);
        clientMapper.updateById(row);
        auditService.record(AuditActionEnum.CLIENT_UPDATE, operatorId, "oauth_client", clientId,
                enabled ? "enabled" : "disabled", ip);
    }

    @Transactional
    public SecretResetVo resetSecret(String clientId, Long operatorId, String ip) {
        OauthClient row = requireByClientId(clientId);
        if (!ClientTypeEnum.CONFIDENTIAL.getCode().equals(row.getClientType())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "公开客户端无密钥");
        }
        String plainSecret = generateSecret();
        row.setClientSecretHash(passwordEncoder.encode(plainSecret));
        clientMapper.updateById(row);
        auditService.record(AuditActionEnum.CLIENT_SECRET_RESET, operatorId, "oauth_client", clientId,
                "secret reset", ip);
        return new SecretResetVo(toVo(row), plainSecret);
    }

    @Transactional(readOnly = true)
    public OauthClientVo getByClientId(String clientId) {
        return toVo(requireByClientId(clientId));
    }

    @Transactional(readOnly = true)
    public PageResult<OauthClientVo> page(String keyword, Integer enabled, PageQuery query) {
        Page<OauthClient> page = clientMapper.selectPage(new Page<>(query.current(), query.size()),
                new LambdaQueryWrapper<OauthClient>()
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(OauthClient::getClientName, keyword)
                                .or().like(OauthClient::getClientId, keyword)
                                .or().like(OauthClient::getSystemCode, keyword))
                        .eq(enabled != null, OauthClient::getEnabled, enabled)
                        .orderByDesc(OauthClient::getCreateTime));
        return PageResult.of(page.getRecords().stream().map(this::toVo).toList(),
                page.getTotal(), query.size(), query.current());
    }

    @Transactional(readOnly = true)
    public OauthClient requireEntityByClientId(String clientId) {
        return requireByClientId(clientId);
    }

    private OauthClient requireByClientId(String clientId) {
        OauthClient row = clientMapper.selectOne(new LambdaQueryWrapper<OauthClient>()
                .eq(OauthClient::getClientId, clientId)
                .last("LIMIT 1"));
        if (row == null) {
            throw new BizException(ErrorCode.CLIENT_NOT_FOUND);
        }
        return row;
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private OauthClientVo toVo(OauthClient row) {
        return new OauthClientVo(
                row.getId(),
                row.getClientId(),
                row.getClientName(),
                row.getClientType(),
                row.getClientAuthMethod(),
                row.getGrantTypes(),
                row.getRedirectUris(),
                row.getScopes(),
                row.getRequirePkce(),
                row.getRequireConsent(),
                row.getAccessTokenTtlSec(),
                row.getRefreshTokenTtlSec(),
                row.getSystemCode(),
                row.getOwner(),
                row.getEnv(),
                row.getEnabled(),
                row.getRemark(),
                row.getCreateTime()
        );
    }
}
