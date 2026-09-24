package com.qjj.user.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.user.common.result.PageResult;
import com.qjj.user.framework.core.IdGenerator;
import com.qjj.user.framework.core.MybatisSupportConfiguration;
import com.qjj.user.framework.core.UserContext;
import com.qjj.user.service.dto.AuditPageQuery;
import com.qjj.user.service.entity.UserAuditLog;
import com.qjj.user.service.enums.AuditActionEnum;
import com.qjj.user.service.mapper.UserAuditLogMapper;
import com.qjj.user.service.vo.AuditVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final UserAuditLogMapper auditLogMapper;

    private final IdGenerator idGenerator;

    @Override
    public void record(AuditActionEnum action, Long targetUserId, String detail) {
        UserAuditLog log = new UserAuditLog();
        log.setId(idGenerator.nextId());
        log.setAction(action.getCode());
        log.setActorUserId(UserContext.userIdOrSystem());
        log.setTargetUserId(targetUserId);
        log.setDetail(detail == null ? "" : detail.substring(0, Math.min(detail.length(), 512)));
        log.setIp(resolveIp());
        auditLogMapper.insert(log);
    }

    @Override
    public PageResult<AuditVO> page(AuditPageQuery query) {
        LambdaQueryWrapper<UserAuditLog> wrapper = new LambdaQueryWrapper<UserAuditLog>()
                .eq(query.getAction() != null && !query.getAction().isBlank(), UserAuditLog::getAction, query.getAction())
                .eq(query.getActorUserId() != null, UserAuditLog::getActorUserId, query.getActorUserId())
                .eq(query.getTargetUserId() != null, UserAuditLog::getTargetUserId, query.getTargetUserId())
                .ge(query.getBeginTime() != null, UserAuditLog::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, UserAuditLog::getCreateTime, query.getEndTime())
                .orderByDesc(UserAuditLog::getCreateTime);
        Page<UserAuditLog> page = auditLogMapper.selectPage(
                MybatisSupportConfiguration.toPage(query.getCurrent(), query.getSize()), wrapper);
        return MybatisSupportConfiguration.toPageResult(page, this::toVO);
    }

    private AuditVO toVO(UserAuditLog log) {
        AuditVO vo = new AuditVO();
        vo.setId(String.valueOf(log.getId()));
        vo.setAction(log.getAction());
        vo.setActorUserId(log.getActorUserId() == null ? "" : String.valueOf(log.getActorUserId()));
        vo.setTargetUserId(log.getTargetUserId() == null ? "" : String.valueOf(log.getTargetUserId()));
        vo.setDetail(log.getDetail());
        vo.setIp(log.getIp());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private String resolveIp() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
            String forwarded = servletAttrs.getRequest().getHeader("X-Forwarded-For");
            return forwarded == null || forwarded.isBlank()
                    ? servletAttrs.getRequest().getRemoteAddr()
                    : forwarded.split(",")[0].trim();
        }
        return "";
    }
}
