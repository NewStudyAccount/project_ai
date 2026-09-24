package com.qjj.auth.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("auth_grant")
public class AuthGrant extends BaseEntity {
    private Long userId;
    private String clientId;
    private String scopes;
    private Integer status;
    private LocalDateTime revokedAt;
    private String revokeReason;
}
