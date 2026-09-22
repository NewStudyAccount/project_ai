package com.example.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.auth.common.enums.AuditActionEnum;
import com.example.auth.entity.AuthAuditLog;
import com.example.auth.framework.IdService;
import com.example.auth.mapper.AuthAuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 安全审计；detail 禁止写入密码/完整 token。
 */
@Service
public class AuditService {

    private final AuthAuditLogMapper auditLogMapper;
    private final IdService idService;

    public AuditService(AuthAuditLogMapper auditLogMapper, IdService idService) {
        this.auditLogMapper = auditLogMapper;
        this.idService = idService;
    }

    @Transactional
    public void record(AuditActionEnum action, Long actorUserId, String targetType, String targetId,
                       String detail, String ip) {
        AuthAuditLog row = new AuthAuditLog();
        row.setId(idService.nextId("auth_audit_log"));
        row.setAction(action.getCode());
        row.setActorUserId(actorUserId);
        row.setTargetType(targetType == null ? "" : targetType);
        row.setTargetId(targetId == null ? "" : targetId);
        row.setDetail(sanitize(detail));
        row.setIp(ip == null ? "" : ip);
        auditLogMapper.insert(row);
    }

    private String sanitize(String detail) {
        if (detail == null) {
            return "";
        }
        String value = detail;
        if (value.length() > 500) {
            value = value.substring(0, 500);
        }
        return value.replaceAll("(?i)(password|secret|token)=\\S+", "$1=***");
    }
}
