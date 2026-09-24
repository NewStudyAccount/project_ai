package com.qjj.auth.service.dto;

import com.qjj.auth.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AuditQuery extends PageQuery {
    private String action;
    private Long actorUserId;
    private String targetId;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}
