-- V2__unified_auth_center.sql
-- 统一认证中心 auth_db（与用户中心拆分；用户主数据不在本库）
-- 约束：无物理外键；id BIGINT 禁自增（14 位应用发号）；utf8mb4
-- 变更：add-unified-auth-center / docs/template/unified-auth-center-database.md

-- ----------------------------
-- 1. sys_sequence 发号（若 V1 已建可忽略）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_sequence` (
  `id`          BIGINT       NOT NULL            COMMENT '主键',
  `seq_name`    VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '序列名',
  `seq_date`    CHAR(8)      NOT NULL            COMMENT '业务日 yyyyMMdd',
  `current_val` BIGINT       NOT NULL DEFAULT 0  COMMENT '当日已发序号',
  `create_time` DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_sequence_name_date` (`seq_name`, `seq_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ID 发号表（yyMMdd+8 位序号）';

-- ----------------------------
-- 2. sys_credential 登录凭证（user_id → 用户中心 sys_user.id，逻辑关联）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_credential` (
  `id`               BIGINT       NOT NULL            COMMENT '主键 14 位，禁自增',
  `user_id`          BIGINT       NOT NULL            COMMENT '用户中心 sys_user.id',
  `credential_type`  VARCHAR(32)  NOT NULL            COMMENT 'PASSWORD/SMS/EMAIL/TOTP',
  `secret_ref`       VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'PASSWORD: BCrypt/Argon2 哈希，禁止明文',
  `verified`         TINYINT      NOT NULL DEFAULT 0  COMMENT '1 已验证',
  `status`           TINYINT      NOT NULL DEFAULT 1  COMMENT '1 启用 / 0 停用',
  `expires_at`       DATETIME     NULL                COMMENT '过期时间',
  `pwd_update_time`  DATETIME     NULL                COMMENT '最近改密',
  `create_time`      DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`        BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`        BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`          TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_credential_user_type` (`user_id`, `credential_type`),
  KEY `idx_sys_credential_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录凭证（仅认证库）';

-- ----------------------------
-- 3. oauth_client 客户端注册与运营
-- ----------------------------
CREATE TABLE IF NOT EXISTS `oauth_client` (
  `id`                     BIGINT        NOT NULL            COMMENT '主键 14 位',
  `client_id`              VARCHAR(64)   NOT NULL            COMMENT 'OAuth client_id',
  `client_secret_hash`     VARCHAR(100)  NULL                COMMENT '机密客户端；公开可空',
  `client_name`            VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '显示名',
  `client_type`            VARCHAR(32)   NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/CONFIDENTIAL',
  `client_auth_method`     VARCHAR(32)   NOT NULL DEFAULT 'NONE' COMMENT '认证方式',
  `grant_types`            VARCHAR(255)  NOT NULL            COMMENT '如 authorization_code,refresh_token',
  `redirect_uris`          VARCHAR(1024) NOT NULL            COMMENT '精确白名单，逗号分隔',
  `scopes`                 VARCHAR(255)  NOT NULL DEFAULT 'openid,profile' COMMENT 'scope',
  `require_pkce`           TINYINT       NOT NULL DEFAULT 1  COMMENT '强制 PKCE',
  `require_consent`        TINYINT       NOT NULL DEFAULT 0  COMMENT '授权确认',
  `reuse_refresh_tokens`   TINYINT       NOT NULL DEFAULT 0  COMMENT '必须 0（轮转）',
  `access_token_ttl_sec`   INT           NOT NULL DEFAULT 600 COMMENT 'AT TTL',
  `refresh_token_ttl_sec`  INT           NOT NULL DEFAULT 604800 COMMENT 'RT TTL',
  `system_code`            VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '接入系统编码',
  `owner`                  VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '负责人',
  `env`                    VARCHAR(16)   NOT NULL DEFAULT 'dev' COMMENT '环境',
  `enabled`                TINYINT       NOT NULL DEFAULT 1  COMMENT '1 启用 / 0 停用',
  `remark`                 VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '备注',
  `create_time`            DATETIME      NOT NULL            COMMENT '创建时间',
  `update_time`            DATETIME      NOT NULL            COMMENT '更新时间',
  `create_by`              BIGINT        NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`              BIGINT        NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`                TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oauth_client_client_id` (`client_id`),
  KEY `idx_oauth_client_enabled` (`enabled`),
  KEY `idx_oauth_client_system_code` (`system_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth Client 注册与运营';

-- ----------------------------
-- 4. auth_session SSO 会话
-- ----------------------------
CREATE TABLE IF NOT EXISTS `auth_session` (
  `id`                 BIGINT       NOT NULL            COMMENT '主键 14 位',
  `session_token_hash` VARCHAR(64)  NOT NULL            COMMENT '会话令牌 SHA-256',
  `user_id`            BIGINT       NOT NULL            COMMENT '用户中心 id',
  `auth_time`          DATETIME     NOT NULL            COMMENT '认证时间',
  `expires_at`         DATETIME     NOT NULL            COMMENT '过期时间',
  `revoked_at`         DATETIME     NULL                COMMENT '吊销时间',
  `user_agent`         VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'UA',
  `ip`                 VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '客户端 IP',
  `create_time`        DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time`        DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`          BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`          BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`            TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_session_token_hash` (`session_token_hash`),
  KEY `idx_auth_session_user_id` (`user_id`),
  KEY `idx_auth_session_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO 会话';

-- ----------------------------
-- 5. auth_grant 授权/令牌链台账
-- ----------------------------
CREATE TABLE IF NOT EXISTS `auth_grant` (
  `id`            BIGINT       NOT NULL            COMMENT '主键 14 位',
  `user_id`       BIGINT       NOT NULL            COMMENT '用户中心 id',
  `client_id`     VARCHAR(64)  NOT NULL            COMMENT 'OAuth client_id',
  `session_id`    BIGINT       NULL                COMMENT 'auth_session.id',
  `scopes`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '授权 scope',
  `status`        TINYINT      NOT NULL DEFAULT 1  COMMENT '1 活跃 / 0 吊销',
  `revoked_at`    DATETIME     NULL                COMMENT '吊销时间',
  `revoke_reason` VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '吊销原因',
  `create_time`   DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`     BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`     BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  KEY `idx_auth_grant_user_id` (`user_id`),
  KEY `idx_auth_grant_client_id` (`client_id`),
  KEY `idx_auth_grant_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授权台账';

-- ----------------------------
-- 6. auth_refresh_token RT 哈希与轮转链
-- ----------------------------
CREATE TABLE IF NOT EXISTS `auth_refresh_token` (
  `id`          BIGINT       NOT NULL            COMMENT '主键 14 位',
  `grant_id`    BIGINT       NOT NULL            COMMENT 'auth_grant.id',
  `user_id`     BIGINT       NOT NULL            COMMENT '用户中心 id',
  `client_id`   VARCHAR(64)  NOT NULL            COMMENT 'OAuth client_id',
  `token_hash`  VARCHAR(64)  NOT NULL            COMMENT 'RT SHA-256，不存明文',
  `parent_id`   BIGINT       NULL                COMMENT '轮转来源 id',
  `status`      TINYINT      NOT NULL DEFAULT 1  COMMENT '1 有效 / 0 轮转作废 / 2 吊销',
  `expires_at`  DATETIME     NOT NULL            COMMENT '过期时间',
  `rotated_at`  DATETIME     NULL                COMMENT '轮转时间',
  `revoked_at`  DATETIME     NULL                COMMENT '吊销时间',
  `create_time` DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_refresh_token_hash` (`token_hash`),
  KEY `idx_auth_refresh_token_grant_id` (`grant_id`),
  KEY `idx_auth_refresh_token_user_id` (`user_id`),
  KEY `idx_auth_refresh_token_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Refresh Token';

-- ----------------------------
-- 7. login_attempt 登录风控
-- ----------------------------
CREATE TABLE IF NOT EXISTS `login_attempt` (
  `id`          BIGINT       NOT NULL            COMMENT '主键 14 位',
  `username`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '尝试登录名',
  `user_id`     BIGINT       NULL                COMMENT '用户中心 id',
  `success`     TINYINT      NOT NULL DEFAULT 0  COMMENT '1 成功 / 0 失败',
  `fail_reason` VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '失败原因',
  `ip`          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '客户端 IP',
  `user_agent`  VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'UA',
  `client_id`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT 'OAuth client_id',
  `create_time` DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  KEY `idx_login_attempt_username` (`username`),
  KEY `idx_login_attempt_ip` (`ip`),
  KEY `idx_login_attempt_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录风控';

-- ----------------------------
-- 8. auth_audit_log 安全审计
-- ----------------------------
CREATE TABLE IF NOT EXISTS `auth_audit_log` (
  `id`            BIGINT       NOT NULL            COMMENT '主键 14 位',
  `action`        VARCHAR(64)  NOT NULL            COMMENT 'LOGIN/TOKEN_ISSUE/REFRESH/REVOKE/CLIENT_UPDATE…',
  `actor_user_id` BIGINT       NULL                COMMENT '操作者',
  `target_type`   VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '目标类型',
  `target_id`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '目标 id',
  `detail`        VARCHAR(512) NOT NULL DEFAULT '' COMMENT '详情，禁止密码/完整 token',
  `ip`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '客户端 IP',
  `create_time`   DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`     BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`     BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  KEY `idx_auth_audit_log_action` (`action`),
  KEY `idx_auth_audit_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全审计';

-- ----------------------------
-- 9. oauth2_authorization SAS 协议存储（授权码/AT/RT 协议状态）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `oauth2_authorization` (
  `id`                            VARCHAR(100)  NOT NULL COMMENT 'SAS authorization id',
  `registered_client_id`          VARCHAR(100)  NOT NULL COMMENT 'client_id',
  `principal_name`                VARCHAR(200)  NOT NULL COMMENT 'principal',
  `authorization_grant_type`      VARCHAR(100)  NOT NULL COMMENT 'grant type',
  `authorized_scopes`             VARCHAR(1000) NULL     COMMENT 'scopes',
  `attributes`                    BLOB          NULL     COMMENT 'attributes',
  `state`                         VARCHAR(500)  NULL     COMMENT 'state',
  `authorization_code_value`      BLOB          NULL     COMMENT 'code',
  `authorization_code_issued_at`  DATETIME      NULL     COMMENT '',
  `authorization_code_expires_at` DATETIME      NULL     COMMENT '',
  `authorization_code_metadata`   BLOB          NULL     COMMENT '',
  `access_token_value`            BLOB          NULL     COMMENT 'AT',
  `access_token_issued_at`        DATETIME      NULL     COMMENT '',
  `access_token_expires_at`       DATETIME      NULL     COMMENT '',
  `access_token_metadata`         BLOB          NULL     COMMENT '',
  `access_token_type`             VARCHAR(100)  NULL     COMMENT '',
  `refresh_token_value`           BLOB          NULL     COMMENT 'RT',
  `refresh_token_issued_at`       DATETIME      NULL     COMMENT '',
  `refresh_token_expires_at`      DATETIME      NULL     COMMENT '',
  `refresh_token_metadata`        BLOB          NULL     COMMENT '',
  `oidc_id_token_value`           BLOB          NULL     COMMENT 'ID Token',
  `oidc_id_token_issued_at`       DATETIME      NULL     COMMENT '',
  `oidc_id_token_expires_at`      DATETIME      NULL     COMMENT '',
  `oidc_id_token_metadata`        BLOB          NULL     COMMENT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SAS 协议存储';
