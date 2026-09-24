package com.qjj.auth.service.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GrantVO {
    private String id;
    private String userId;
    private String clientId;
    private String scopes;
    private Integer status;
    private LocalDateTime revokedAt;
    private String revokeReason;
}
