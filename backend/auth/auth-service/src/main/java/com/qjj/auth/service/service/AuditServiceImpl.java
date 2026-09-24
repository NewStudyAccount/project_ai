package com.qjj.auth.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.auth.common.result.PageResult;
import com.qjj.auth.framework.core.AuthContext;
import com.qjj.auth.framework.core.IdGenerator;
import com.qjj.auth.framework.core.MybatisSupportConfiguration;
import com.qjj.auth.service.dto.AuditQuery;
import com.qjj.auth.service.dto.LoginAttemptQuery;
import com.qjj.auth.service.entity.AuthAuditLog;
import com.qjj.auth.service.entity.LoginAttempt;
import com.qjj.auth.service.mapper.AuthAuditLogMapper;
import com.qjj.auth.service.mapper.LoginAttemptMapper;
import com.qjj.auth.service.vo.AuditVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {
    private final AuthAuditLogMapper auditLogMapper;
    private final LoginAttemptMapper loginAttemptMapper;
    private final IdGenerator idGenerator;

    @Override
    public void record(String action, String targetType, String targetId, String detail) {
        AuthAuditLog log = new AuthAuditLog();
        log.setId(idGenerator.nextId());
        log.setAction(action);
        log.setActorUserId(AuthContext.userIdOrSystem());
        log.setTargetType(targetType == null ? "" : targetType);
        log.setTargetId(targetId == null ? "" : targetId);
        log.setDetail(detail == null ? "" : detail.substring(0, Math.min(detail.length(), 512)));
        log.setIp(resolveIp());
        auditLogMapper.insert(log);
    }

    @Override
    public PageResult<AuditVO> page(AuditQuery query) {
        LambdaQueryWrapper<AuthAuditLog> wrapper = new LambdaQueryWrapper<AuthAuditLog>()
                .eq(query.getAction() != null && !query.getAction().isBlank(), AuthAuditLog::getAction, query.getAction())
                .eq(query.getActorUserId() != null, AuthAuditLog::getActorUserId, query.getActorUserId())
                .eq(query.getTargetId() != null && !query.getTargetId().isBlank(), AuthAuditLog::getTargetId, query.getTargetId())
                .ge(query.getBeginTime() != null, AuthAuditLog::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, AuthAuditLog::getCreateTime, query.getEndTime())
                .orderByDesc(AuthAuditLog::getCreateTime);
        Page<AuthAuditLog> page = auditLogMapper.selectPage(MybatisSupportConfiguration.toPage(query.getCurrent(), query.getSize()), wrapper);
        return MybatisSupportConfiguration.toPageResult(page, this::toVO);
    }

    @Override
    public PageResult<AuditVO> loginAttempts(LoginAttemptQuery query) {
        LambdaQueryWrapper<LoginAttempt> wrapper = new LambdaQueryWrapper<LoginAttempt>()
                .eq(query.getUsername() != null && !query.getUsername().isBlank(), LoginAttempt::getUsername, query.getUsername())
                .eq(query.getUserId() != null, LoginAttempt::getUserId, query.getUserId())
                .eq(query.getClientId() != null && !query.getClientId().isBlank(), LoginAttempt::getClientId, query.getClientId())
                .eq(query.getIp() != null && !query.getIp().isBlank(), LoginAttempt::getIp, query.getIp())
                .eq(query.getSuccess() != null, LoginAttempt::getSuccess, query.getSuccess())
                .ge(query.getBeginTime() != null, LoginAttempt::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, LoginAttempt::getCreateTime, query.getEndTime())
                .orderByDesc(LoginAttempt::getCreateTime);
        Page<LoginAttempt> page = loginAttemptMapper.selectPage(MybatisSupportConfiguration.toPage(query.getCurrent(), query.getSize()), wrapper);
        return MybatisSupportConfiguration.toPageResult(page, this::toAttemptVO);
    }

    private AuditVO toVO(AuthAuditLog log) {
        AuditVO vo = new AuditVO();
        vo.setId(String.valueOf(log.getId()));
        vo.setAction(log.getAction());
        vo.setActorUserId(log.getActorUserId() == null ? "" : String.valueOf(log.getActorUserId()));
        vo.setTargetType(log.getTargetType());
        vo.setTargetId(log.getTargetId());
        vo.setDetail(log.getDetail());
        vo.setIp(log.getIp());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private AuditVO toAttemptVO(LoginAttempt attempt) {
        AuditVO vo = new AuditVO();
        vo.setId(String.valueOf(attempt.getId()));
        vo.setAction(attempt.getSuccess() == 1 ? "LOGIN_SUCCESS" : "LOGIN_FAILED");
        vo.setActorUserId(attempt.getUserId() == null ? "" : String.valueOf(attempt.getUserId()));
        vo.setTargetType("login_attempt");
        vo.setTargetId(attempt.getUsername());
        vo.setDetail(attempt.getFailReason());
        vo.setIp(attempt.getIp());
        vo.setCreateTime(attempt.getCreateTime());
        return vo;
    }

    private String resolveIp() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest().getRemoteAddr();
        }
        return "";
    }
}
