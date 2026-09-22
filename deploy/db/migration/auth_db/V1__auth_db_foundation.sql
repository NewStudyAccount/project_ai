-- V1__auth_db_foundation.sql
-- 库：auth_db（仅 auth-service 读写）
-- 约束：无物理外键；id BIGINT 禁自增（14 位应用发号）；utf8mb4
-- 变更：add-foundation-db-schema / 对照 docs/database-design.md §3

-- ----------------------------
-- 1. sys_sequence 发号表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_sequence` (
  `id`          BIGINT       NOT NULL            COMMENT '主键（14 位或内部唯一值，禁自增）',
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
-- 2. sys_dept 部门树
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_dept` (
  `id`          BIGINT       NOT NULL            COMMENT '主键 14 位，禁自增',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0  COMMENT '父部门 id，根为 0',
  `ancestors`   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '祖先路径，如 0,1,5',
  `dept_name`   VARCHAR(64)  NOT NULL            COMMENT '部门名称',
  `dept_code`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '部门编码',
  `sort`        INT          NOT NULL DEFAULT 0  COMMENT '排序',
  `status`      TINYINT      NOT NULL DEFAULT 1  COMMENT '1 启用 / 0 停用',
  `leader_id`   BIGINT       NOT NULL DEFAULT 0  COMMENT '负责人 user_id（逻辑关联）',
  `remark`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注',
  `create_time` DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  KEY `idx_sys_dept_parent_id` (`parent_id`),
  UNIQUE KEY `uk_sys_dept_dept_code` (`dept_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局部门树';

-- ----------------------------
-- 3. sys_user 用户权威
-- password_hash 仅允许存在于此表，禁止同步到业务库
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`              BIGINT       NOT NULL            COMMENT '主键 14 位；跨系统 user_id，禁自增',
  `username`        VARCHAR(64)  NOT NULL            COMMENT '登录名，全局唯一',
  `password_hash`   VARCHAR(100) NOT NULL            COMMENT 'BCrypt 哈希，禁止出 auth 库',
  `real_name`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '姓名',
  `nickname`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
  `email`           VARCHAR(128) NOT NULL DEFAULT '' COMMENT '邮箱（出参脱敏）',
  `phone`           VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '手机（出参脱敏）',
  `avatar`          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像 URL',
  `dept_id`         BIGINT       NOT NULL DEFAULT 0  COMMENT '主属部门 id（逻辑关联 sys_dept.id）',
  `status`          TINYINT      NOT NULL DEFAULT 1  COMMENT '1 启用 / 0 停用',
  `last_login_time` DATETIME     NULL                COMMENT '最近登录时间',
  `last_login_ip`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '最近登录 IP',
  `pwd_update_time` DATETIME     NULL                COMMENT '最近改密时间',
  `remark`          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注',
  `create_time`     DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`       BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`       BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`         TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_dept_id` (`dept_id`),
  KEY `idx_sys_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户身份权威（含凭证）';

-- ----------------------------
-- 4. sys_login_log 登录日志（可选）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id`          BIGINT       NOT NULL            COMMENT '主键 14 位，禁自增',
  `user_id`     BIGINT       NOT NULL DEFAULT 0  COMMENT '用户 id（逻辑关联 sys_user.id）',
  `username`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '用户名快照',
  `login_ip`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '登录 IP',
  `user_agent`  VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'UA',
  `status`      TINYINT      NOT NULL DEFAULT 0  COMMENT '1 成功 / 0 失败',
  `message`     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
  `login_time`  DATETIME     NOT NULL            COMMENT '登录时间',
  `create_time` DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  KEY `idx_sys_login_log_user_id` (`user_id`),
  KEY `idx_sys_login_log_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录审计日志';

-- ----------------------------
-- 5. sys_sso_client SSO 客户端白名单（可选）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_sso_client` (
  `id`          BIGINT       NOT NULL            COMMENT '主键 14 位，禁自增',
  `client_id`   VARCHAR(64)  NOT NULL            COMMENT '客户端标识（system_code）',
  `client_name` VARCHAR(64)  NOT NULL            COMMENT '显示名',
  `return_url`  VARCHAR(255) NOT NULL DEFAULT '' COMMENT '允许回跳 URL',
  `status`      TINYINT      NOT NULL DEFAULT 1  COMMENT '1 启用 / 0 停用',
  `create_time` DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_sso_client_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO 接入客户端';
