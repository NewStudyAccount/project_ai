package com.qjj.user.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("user_audit_log")
public class UserAuditLog extends BaseEntity {

    private String action;

    private Long actorUserId;

    private Long targetUserId;

    private String detail;

    private String ip;
}
