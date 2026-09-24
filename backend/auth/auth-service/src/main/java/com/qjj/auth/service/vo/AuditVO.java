package com.qjj.auth.service.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditVO {
    private String id;
    private String action;
    private String actorUserId;
    private String targetType;
    private String targetId;
    private String detail;
    private String ip;
    private LocalDateTime createTime;
}
