package com.qjj.auth.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.auth.common.enums.AuthErrorCodeEnum;
import com.qjj.auth.common.exception.BizException;
import com.qjj.auth.common.result.PageResult;
import com.qjj.auth.framework.core.MybatisSupportConfiguration;
import com.qjj.auth.service.dto.GrantQuery;
import com.qjj.auth.service.entity.AuthGrant;
import com.qjj.auth.service.mapper.AuthGrantMapper;
import com.qjj.auth.service.security.SsoSessionService;
import com.qjj.auth.service.vo.GrantVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GrantServiceImpl implements GrantService {
    private final AuthGrantMapper grantMapper;
    private final AuditService auditService;
    private final TokenManagementService tokenManagementService;
    private final SsoSessionService ssoSessionService;

    @Override
    public PageResult<GrantVO> page(GrantQuery query) {
        LambdaQueryWrapper<AuthGrant> wrapper = new LambdaQueryWrapper<AuthGrant>()
                .eq(query.getUserId() != null, AuthGrant::getUserId, query.getUserId())
                .eq(query.getClientId() != null && !query.getClientId().isBlank(), AuthGrant::getClientId, query.getClientId())
                .eq(query.getStatus() != null, AuthGrant::getStatus, query.getStatus())
                .orderByDesc(AuthGrant::getCreateTime);
        Page<AuthGrant> page = grantMapper.selectPage(MybatisSupportConfiguration.toPage(query.getCurrent(), query.getSize()), wrapper);
        return MybatisSupportConfiguration.toPageResult(page, this::toVO);
    }

    @Override
    @Transactional
    public void revoke(String id, String reason) {
        AuthGrant grant = grantMapper.selectById(Long.parseLong(id));
        if (grant == null) throw new BizException(AuthErrorCodeEnum.GRANT_NOT_FOUND);
        grant.setStatus(0);
        grant.setRevokedAt(LocalDateTime.now());
        grant.setRevokeReason(reason == null ? "" : reason);
        grantMapper.updateById(grant);
        tokenManagementService.revokeGrant(id, String.valueOf(grant.getUserId()));
        auditService.record("REVOKE", "auth_grant", id, "吊销授权");
    }

    @Override
    @Transactional
    public void revokeByUser(String userId, String reason) {
        AuthGrant update = new AuthGrant();
        update.setStatus(0);
        update.setRevokedAt(LocalDateTime.now());
        update.setRevokeReason(reason == null ? "" : reason);
        grantMapper.update(update, new LambdaQueryWrapper<AuthGrant>().eq(AuthGrant::getUserId, Long.parseLong(userId)).eq(AuthGrant::getStatus, 1));
        grantMapper.selectList(new LambdaQueryWrapper<AuthGrant>().eq(AuthGrant::getUserId, Long.parseLong(userId)))
                .forEach(grant -> tokenManagementService.revokeGrant(String.valueOf(grant.getId()), userId));
        ssoSessionService.revoke(Long.valueOf(userId), null);
        auditService.record("REVOKE", "user", userId, "按用户吊销授权");
    }

    private GrantVO toVO(AuthGrant grant) {
        GrantVO vo = new GrantVO();
        vo.setId(String.valueOf(grant.getId()));
        vo.setUserId(String.valueOf(grant.getUserId()));
        vo.setClientId(grant.getClientId());
        vo.setScopes(grant.getScopes());
        vo.setStatus(grant.getStatus());
        vo.setRevokedAt(grant.getRevokedAt());
        vo.setRevokeReason(grant.getRevokeReason());
        return vo;
    }
}
