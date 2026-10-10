-- blog_db 基础表（示例系统设计 §6.2，表名按 blog 映射）
-- 人工执行；生产执行前须按 CLAUDE.md §7 征询
CREATE DATABASE IF NOT EXISTS blog_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE blog_db;

CREATE TABLE IF NOT EXISTS sys_sequence (
  id BIGINT NOT NULL COMMENT '主键=业务日 yyyyMMdd',
  seq_date DATETIME NOT NULL COMMENT '业务日（Asia/Shanghai）',
  current_val BIGINT NOT NULL DEFAULT 0 COMMENT '当日已发最大值',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_sequence_seq_date (seq_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按日段式发号';

CREATE TABLE IF NOT EXISTS sys_file (
  id BIGINT NOT NULL COMMENT '16 位发号',
  bucket VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'MinIO 桶',
  object_key VARCHAR(255) NOT NULL COMMENT '对象键 UUID',
  original_name VARCHAR(255) NOT NULL DEFAULT '',
  content_type VARCHAR(128) NOT NULL DEFAULT '',
  size_bytes BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除即文件删除语义',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_file_object_key (object_key),
  KEY idx_sys_file_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传文件元数据';

CREATE TABLE IF NOT EXISTS blog_audit_log (
  id BIGINT NOT NULL COMMENT '16 位发号',
  action VARCHAR(64) NOT NULL,
  actor_user_id BIGINT NULL,
  target_type VARCHAR(32) NOT NULL DEFAULT '',
  target_id VARCHAR(64) NOT NULL DEFAULT '',
  detail VARCHAR(512) NOT NULL DEFAULT '',
  ip VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_blog_audit_log_action (action),
  KEY idx_blog_audit_log_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计';
