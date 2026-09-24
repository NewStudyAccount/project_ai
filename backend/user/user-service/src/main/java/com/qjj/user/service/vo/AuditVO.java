package com.qjj.user.service.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditVO {

    private String id;

    private String action;

    private String actorUserId;

    private String targetUserId;

    private String detail;

    private String ip;

    private LocalDateTime createTime;
}
