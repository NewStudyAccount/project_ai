-- V1__file_db_foundation.sql
-- 库：file_db（仅 file-service 读写）
-- 约束：无物理外键；id BIGINT 禁自增（14 位应用发号）；utf8mb4
-- 变更：add-file-service / 对照 docs/blog-system-design.md §4.2 file_object

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
-- 2. file_object 对象元数据
-- ----------------------------
CREATE TABLE IF NOT EXISTS `file_object` (
  `id`           BIGINT       NOT NULL            COMMENT '主键 14 位，禁自增',
  `object_key`   VARCHAR(255) NOT NULL            COMMENT '对象键 UK，如 blog/posts/{id}/content.html',
  `bucket`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '存储桶',
  `url`          VARCHAR(512) NOT NULL DEFAULT '' COMMENT '访问 URL（公开或签名预留）',
  `content_type` VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'MIME 类型',
  `size`         BIGINT       NOT NULL DEFAULT 0  COMMENT '字节大小',
  `owner_id`     BIGINT       NOT NULL DEFAULT 0  COMMENT '上传人 user_id（中心）',
  `biz_type`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '业务类型，如 blog_post',
  `biz_id`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '业务 id',
  `scene`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '场景，如 content/cover/assets',
  `create_time`  DATETIME     NOT NULL            COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL            COMMENT '更新时间',
  `create_by`    BIGINT       NOT NULL DEFAULT 0  COMMENT '创建人',
  `update_by`    BIGINT       NOT NULL DEFAULT 0  COMMENT '更新人',
  `deleted`      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除 0/1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_object_object_key` (`object_key`),
  KEY `idx_file_object_biz_type_biz_id` (`biz_type`, `biz_id`),
  KEY `idx_file_object_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象存储文件元数据';
