package com.qjj.auth.service.dto;

import com.qjj.auth.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LoginAttemptQuery extends PageQuery {
    private String username;
    private Long userId;
    private String clientId;
    private String ip;
    private Integer success;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}
