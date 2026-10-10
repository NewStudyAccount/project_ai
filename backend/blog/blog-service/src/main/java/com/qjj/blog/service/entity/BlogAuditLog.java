package com.qjj.blog.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("blog_audit_log")
public class BlogAuditLog extends BaseEntity {

    private String action;

    private Long actorUserId;

    private String targetType;

    private String targetId;

    private String detail;

    private String ip;
}
