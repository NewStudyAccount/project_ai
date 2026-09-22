-- V1__blog_db_foundation.sql
-- 库：blog_db（仅 blog 服务读写）
-- 约束：无物理外键；id BIGINT 禁自增；关联表物理删；无 password_hash
-- 变更：add-blog-foundation-rbac / 对照 system_db V2 + docs/blog-system-design.md

-- ----------------------------
-- 1. sys_sequence
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_sequence` (
  `id`          BIGINT       NOT NULL            COMMENT '主键（禁自增）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本系统 ID 发号表';

-- ----------------------------
-- 2. sys_role
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`          BIGINT       NOT NULL            COMMENT '主键 14 位，禁自增',
  `system_code` VARCHAR(32)  NOT NULL            COMMENT '所属系统标识',
  `role_code`   VARCHAR(64)  NOT NULL            COMMENT '角色编码，系统内唯一',
  `role_name`   VARCHAR(64)  NOT NULL            COMMENT '角色名称',
  `sort`        INT          NOT NULL DEFAULT 0  COMMENT '排序',
  `status`      TINYINT      NOT NULL DEFAULT 1  COMMENT '1 启用 / 0 停用',
  `data_scope`  TINYINT      NOT NULL DEFAULT 1  COMMENT '数据范围预留',
  `remark`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注',
  `create_time` DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_system_code` (`system_code`, `role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色';

-- ----------------------------
-- 3. sys_permission
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id`              BIGINT       NOT NULL            COMMENT '主键 14 位，禁自增',
  `system_code`     VARCHAR(32)  NOT NULL            COMMENT '所属系统标识',
  `parent_id`       BIGINT       NOT NULL DEFAULT 0  COMMENT '父权限 id',
  `permission_type` TINYINT      NOT NULL            COMMENT '1 menu / 2 button / 3 api',
  `permission_code` VARCHAR(128) NOT NULL            COMMENT '权限码，系统内唯一',
  `permission_name` VARCHAR(64)  NOT NULL            COMMENT '权限名称',
  `path`            VARCHAR(255) NOT NULL DEFAULT '' COMMENT '菜单路由',
  `component`       VARCHAR(255) NOT NULL DEFAULT '' COMMENT '前端组件',
  `icon`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '图标',
  `sort`            INT          NOT NULL DEFAULT 0  COMMENT '排序',
  `visible`         TINYINT      NOT NULL DEFAULT 1  COMMENT '菜单是否显示',
  `status`          TINYINT      NOT NULL DEFAULT 1  COMMENT '1 启用 / 0 停用',
  `method`          VARCHAR(16)  NOT NULL DEFAULT '' COMMENT 'api 方法',
  `api_path`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'api 路径',
  `permission_str`  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '与注解对齐，可等于 code',
  `remark`          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注',
  `create_time`     DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`       BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`       BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`         TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_code` (`system_code`, `permission_code`),
  KEY `idx_sys_permission_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单/按钮/API 权限';

-- ----------------------------
-- 4. sys_user_role（物理删）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id`          BIGINT       NOT NULL            COMMENT '主键 14 位，禁自增',
  `user_id`     BIGINT       NOT NULL            COMMENT '用户 id = auth sys_user.id',
  `role_id`     BIGINT       NOT NULL            COMMENT '角色 id',
  `system_code` VARCHAR(32)  NOT NULL            COMMENT '所属系统标识',
  `create_time` DATETIME     NOT NULL            COMMENT '创建时间',
  `create_by`   BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `deleted`     TINYINT      NOT NULL DEFAULT 0  COMMENT '保留；删除请物理删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role_user_role` (`user_id`, `role_id`),
  KEY `idx_sys_user_role_user_id` (`user_id`),
  KEY `idx_sys_user_role_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联（物理删）';

-- ----------------------------
-- 5. sys_role_permission（物理删）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id`            BIGINT      NOT NULL            COMMENT '主键 14 位，禁自增',
  `role_id`       BIGINT      NOT NULL            COMMENT '角色 id',
  `permission_id` BIGINT      NOT NULL            COMMENT '权限 id',
  `system_code`   VARCHAR(32) NOT NULL            COMMENT '所属系统标识',
  `create_time`   DATETIME    NOT NULL            COMMENT '创建时间',
  `create_by`     BIGINT      NOT NULL DEFAULT 0  COMMENT '创建人',
  `deleted`       TINYINT     NOT NULL DEFAULT 0  COMMENT '保留；删除请物理删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission_rp` (`role_id`, `permission_id`),
  KEY `idx_sys_role_permission_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联（物理删）';

-- ----------------------------
-- 6. sys_user_ref 投影（无密码）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user_ref` (
  `user_id`        BIGINT       NOT NULL            COMMENT '主键 = auth sys_user.id',
  `username`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '用户名（展示）',
  `real_name`      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '姓名（展示）',
  `status`         TINYINT      NOT NULL DEFAULT 1  COMMENT '仅展示，不参与鉴权',
  `dept_id`        BIGINT       NOT NULL DEFAULT 0  COMMENT '部门 id',
  `dept_name`      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '部门名冗余',
  `sync_time`      DATETIME     NOT NULL            COMMENT '同步时间',
  `source_version` BIGINT       NOT NULL DEFAULT 0  COMMENT '中心版本号',
  `create_time`    DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL            COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户轻量投影（禁止密码字段）';
