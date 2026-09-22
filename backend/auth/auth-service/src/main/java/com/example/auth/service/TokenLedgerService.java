package com.example.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.auth.common.BizException;
import com.example.auth.common.ErrorCode;
import com.example.auth.common.PageQuery;
import com.example.auth.common.PageResult;
import com.example.auth.common.enums.GrantStatusEnum;
import com.example.auth.common.enums.RefreshTokenStatusEnum;
import com.example.auth.entity.AuthGrant;
import com.example.auth.entity.AuthRefreshToken;
import com.example.auth.framework.IdService;
import com.example.auth.mapper.AuthGrantMapper;
import com.example.auth.mapper.AuthRefreshTokenMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 授权台账 + RT 轮转/吊销；重用检测吊销全链。
 */
@Service
public class TokenLedgerService {

    private final AuthGrantMapper grantMapper;
    private final AuthRefreshTokenMapper refreshTokenMapper;
    private final IdService idService;
    private final AuditService auditService;

    public TokenLedgerService(AuthGrantMapper grantMapper, AuthRefreshTokenMapper refreshTokenMapper,
                              IdService idService, AuditService auditService) {
        this.grantMapper = grantMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.idService = idService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthGrant createGrant(Long userId, String clientId, Long sessionId, String scopes) {
        AuthGrant grant = new AuthGrant();
        grant.setId(idService.nextId("auth_grant"));
        grant.setUserId(userId);
        grant.setClientId(clientId);
        grant.setSessionId(sessionId);
        grant.setScopes(scopes == null ? "" : scopes);
        grant.setStatus(GrantStatusEnum.ACTIVE.getCode());
        grantMapper.insert(grant);
        return grant;
    }

    @Transactional
    public String issueRefreshToken(Long grantId, Long userId, String clientId, int ttlSec) {
        String raw = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        return storeRefreshToken(grantId, userId, clientId, raw, null, ttlSec);
    }

    @Transactional
    public String rotateRefreshToken(String tokenHash, int ttlSec) {
        AuthRefreshToken current = findByHash(tokenHash);
        if (current == null) {
            throw new BizException(ErrorCode.TOKEN_REVOKED);
        }
        if (current.getStatus() == null || current.getStatus() != RefreshTokenStatusEnum.ACTIVE.getCode()) {
            revokeGrantChain(current.getGrantId(), "refresh_token_reuse");
            throw new BizException(ErrorCode.TOKEN_REVOKED);
        }
        if (current.getExpiresAt() != null && current.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.TOKEN_REVOKED);
        }
        current.setStatus(RefreshTokenStatusEnum.ROTATED.getCode());
        current.setRotatedAt(LocalDateTime.now());
        refreshTokenMapper.updateById(current);

        String raw = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        return storeRefreshToken(current.getGrantId(), current.getUserId(), current.getClientId(),
                raw, current.getId(), ttlSec);
    }

    @Transactional
    public void revokeByTokenHash(String tokenHash, String reason) {
        AuthRefreshToken row = findByHash(tokenHash);
        if (row == null) {
            return;
        }
        revokeGrantChain(row.getGrantId(), reason == null ? "revoked" : reason);
    }

    @Transactional
    public int kickByUserId(Long userId, String reason) {
        List<AuthGrant> grants = grantMapper.selectList(new LambdaQueryWrapper<AuthGrant>()
                .eq(AuthGrant::getUserId, userId)
                .eq(AuthGrant::getStatus, GrantStatusEnum.ACTIVE.getCode()));
        for (AuthGrant grant : grants) {
            revokeGrantChain(grant.getId(), reason == null ? "kick_offline" : reason);
        }
        return grants.size();
    }

    @Transactional
    public int kickByClientId(String clientId, String reason) {
        List<AuthGrant> grants = grantMapper.selectList(new LambdaQueryWrapper<AuthGrant>()
                .eq(AuthGrant::getClientId, clientId)
                .eq(AuthGrant::getStatus, GrantStatusEnum.ACTIVE.getCode()));
        for (AuthGrant grant : grants) {
            revokeGrantChain(grant.getId(), reason == null ? "kick_offline" : reason);
        }
        return grants.size();
    }

    @Transactional(readOnly = true)
    public PageResult<AuthGrant> pageGrants(Long userId, String clientId, PageQuery query) {
        Page<AuthGrant> page = grantMapper.selectPage(new Page<>(query.current(), query.size()),
                new LambdaQueryWrapper<AuthGrant>()
                        .eq(userId != null, AuthGrant::getUserId, userId)
                        .eq(clientId != null && !clientId.isBlank(), AuthGrant::getClientId, clientId)
                        .orderByDesc(AuthGrant::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), query.size(), query.current());
    }

    public static String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("token hash failed", ex);
        }
    }

    private String storeRefreshToken(Long grantId, Long userId, String clientId, String raw,
                                     Long parentId, int ttlSec) {
        AuthRefreshToken row = new AuthRefreshToken();
        row.setId(idService.nextId("auth_refresh_token"));
        row.setGrantId(grantId);
        row.setUserId(userId);
        row.setClientId(clientId);
        row.setTokenHash(hashToken(raw));
        row.setParentId(parentId);
        row.setStatus(RefreshTokenStatusEnum.ACTIVE.getCode());
        row.setExpiresAt(LocalDateTime.now().plusSeconds(ttlSec));
        refreshTokenMapper.insert(row);
        return raw;
    }

    private AuthRefreshToken findByHash(String tokenHash) {
        return refreshTokenMapper.selectOne(new LambdaQueryWrapper<AuthRefreshToken>()
                .eq(AuthRefreshToken::getTokenHash, tokenHash)
                .last("LIMIT 1"));
    }

    private void revokeGrantChain(Long grantId, String reason) {
        UpdateWrapper<AuthGrant> grantUpdate = new UpdateWrapper<>();
        grantUpdate.eq("id", grantId)
                .set("status", GrantStatusEnum.REVOKED.getCode())
                .set("revoked_at", LocalDateTime.now())
                .set("revoke_reason", reason);
        grantMapper.update(null, grantUpdate);
        UpdateWrapper<AuthRefreshToken> rtUpdate = new UpdateWrapper<>();
        rtUpdate.eq("grant_id", grantId)
                .eq("status", RefreshTokenStatusEnum.ACTIVE.getCode())
                .set("status", RefreshTokenStatusEnum.REVOKED.getCode())
                .set("revoked_at", LocalDateTime.now());
        refreshTokenMapper.update(null, rtUpdate);
    }
}
