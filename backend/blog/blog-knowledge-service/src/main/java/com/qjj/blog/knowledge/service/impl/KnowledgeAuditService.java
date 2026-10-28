package com.qjj.blog.knowledge.service.impl;

import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.core.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 知识库域审计落库（blog_audit_log，与 blog-service AuditServiceImpl 同表同口径）。
 * knowledge 模块不依赖 blog-service（只读契约边界），经 JdbcTemplate 直写同库审计表；
 * action 取知识域动作码（与 AuditActionEnum 段同风格，见 knowledge.audit.KnowledgeAuditAction）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeAuditService {

    private static final String INSERT_SQL = "INSERT INTO blog_audit_log "
            + "(id, action, actor_user_id, target_type, target_id, detail, ip, create_time, update_time, create_by, update_by, deleted) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?, 0)";

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    /** 知识域审计动作码（复用 blog_audit_log.action 列，VARCHAR(64)） */
    public static final String ACTION_KNOWLEDGE_SYNC = "KNOWLEDGE_SYNC";
    public static final String ACTION_KNOWLEDGE_INGEST = "KNOWLEDGE_INGEST";
    public static final String ACTION_KNOWLEDGE_IMPORT = "KNOWLEDGE_IMPORT";
    public static final String ACTION_KNOWLEDGE_UPDATE = "KNOWLEDGE_UPDATE";
    public static final String ACTION_KNOWLEDGE_DELETE = "KNOWLEDGE_DELETE";
    public static final String ACTION_KNOWLEDGE_REVECTORIZE = "KNOWLEDGE_REVECTORIZE";
    public static final String ACTION_KNOWLEDGE_REINDEX = "KNOWLEDGE_REINDEX";
    public static final String ACTION_KNOWLEDGE_TASK_RERUN = "KNOWLEDGE_TASK_RERUN";
    public static final String ACTION_KNOWLEDGE_ASK = "KNOWLEDGE_ASK";
    public static final String ACTION_AI_PROVIDER_CREATE = "AI_PROVIDER_CREATE";
    public static final String ACTION_AI_PROVIDER_UPDATE = "AI_PROVIDER_UPDATE";
    public static final String ACTION_AI_PROVIDER_DELETE = "AI_PROVIDER_DELETE";
    public static final String ACTION_AI_PROVIDER_TEST = "AI_PROVIDER_TEST";

    public void record(String action, String targetType, String targetId, String detail) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    idGenerator.nextId(),
                    action,
                    UserContext.userIdOrSystem(),
                    targetType == null ? "" : targetType,
                    targetId == null ? "" : targetId,
                    detail == null ? "" : detail.substring(0, Math.min(detail.length(), 512)),
                    resolveIp(),
                    UserContext.userIdOrSystem(),
                    UserContext.userIdOrSystem());
        } catch (Exception e) {
            // 审计失败不阻断业务（与既有口径一致：禁止吞异常——此处记 error 日志告警）
            log.error("knowledge audit write failed action={} target={}", action, targetId, e);
        }
    }

    private String resolveIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            String forwarded = servletAttrs.getRequest().getHeader("X-Forwarded-For");
            return forwarded == null || forwarded.isBlank()
                    ? servletAttrs.getRequest().getRemoteAddr()
                    : forwarded.split(",")[0].trim();
        }
        return "";
    }
}
