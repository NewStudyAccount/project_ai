package com.qjj.blog.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.core.MybatisSupportConfiguration;
import com.qjj.blog.framework.core.UserContext;
import com.qjj.blog.service.dto.AuditPageQuery;
import com.qjj.blog.service.entity.BlogAuditLog;
import com.qjj.blog.service.enums.AuditActionEnum;
import com.qjj.blog.service.mapper.BlogAuditLogMapper;
import com.qjj.blog.service.vo.AuditVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final BlogAuditLogMapper auditLogMapper;

    private final IdGenerator idGenerator;

    @Override
    public void record(AuditActionEnum action, String targetType, String targetId, String detail) {
        BlogAuditLog log = new BlogAuditLog();
        log.setId(idGenerator.nextId());
        log.setAction(action.getCode());
        log.setActorUserId(UserContext.userIdOrSystem());
        log.setTargetType(targetType == null ? "" : targetType);
        log.setTargetId(targetId == null ? "" : targetId);
        log.setDetail(detail == null ? "" : detail.substring(0, Math.min(detail.length(), 512)));
        log.setIp(resolveIp());
        auditLogMapper.insert(log);
    }

    @Override
    public PageResult<AuditVO> page(AuditPageQuery query) {
        LambdaQueryWrapper<BlogAuditLog> wrapper = new LambdaQueryWrapper<BlogAuditLog>()
                .eq(query.getAction() != null && !query.getAction().isBlank(), BlogAuditLog::getAction, query.getAction())
                .eq(query.getActorUserId() != null, BlogAuditLog::getActorUserId, query.getActorUserId())
                .ge(query.getBeginTime() != null, BlogAuditLog::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, BlogAuditLog::getCreateTime, query.getEndTime())
                .orderByDesc(BlogAuditLog::getCreateTime);
        Page<BlogAuditLog> page = auditLogMapper.selectPage(
                MybatisSupportConfiguration.toPage(query.getCurrent(), query.getSize()), wrapper);
        return MybatisSupportConfiguration.toPageResult(page, this::toVO);
    }

    private AuditVO toVO(BlogAuditLog log) {
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
