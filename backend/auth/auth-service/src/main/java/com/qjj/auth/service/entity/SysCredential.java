package com.qjj.auth.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("sys_credential")
public class SysCredential extends BaseEntity {
    private Long userId;
    private String credentialType;
    private String secretRef;
    private Integer verified;
    private Integer status;
    private LocalDateTime expiresAt;
    private LocalDateTime pwdUpdateTime;
}
