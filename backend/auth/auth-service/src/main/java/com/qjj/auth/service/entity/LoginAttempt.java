package com.qjj.auth.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("login_attempt")
public class LoginAttempt extends BaseEntity {
    private String username;
    private Long userId;
    private Integer success;
    private String failReason;
    private String ip;
    private String userAgent;
    private String clientId;
}
