package com.qjj.auth.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("auth_audit_log")
public class AuthAuditLog extends BaseEntity {
    private String action;
    private Long actorUserId;
    private String targetType;
    private String targetId;
    private String detail;
    private String ip;
}
