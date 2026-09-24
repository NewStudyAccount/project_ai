package com.qjj.user.service.dto;

import com.qjj.user.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AuditPageQuery extends PageQuery {

    private String action;

    private Long actorUserId;

    private Long targetUserId;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;
}
