-- blog_db · sys_storage_provider 建表 + sys_file 加 storage_code（blog-object-storage-multi-provider）
-- 对齐 docs/object-storage-design/§5.1/§5.2；可重复执行（存在性检查）
-- 执行：USE blog_db 后人工执行；生产执行前须按 CLAUDE.md §7 征询

USE blog_db;

-- ============================================================
-- 1) sys_storage_provider 表（服务商配置真相源）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_storage_provider (
  id BIGINT NOT NULL COMMENT '16 位发号',
  code VARCHAR(64) NOT NULL COMMENT '服务商编码，系统内唯一',
  name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '展示名',
  provider_type VARCHAR(32) NOT NULL COMMENT 'MINIO/AWS_S3/ALIYUN_OSS/TENCENT_COS/HUAWEI_OBS/CUSTOM_S3',
  endpoint VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'S3 endpoint',
  region VARCHAR(64) NOT NULL DEFAULT '' COMMENT '区域；类型要求必填时校验',
  bucket VARCHAR(64) NOT NULL DEFAULT '' COMMENT '默认桶',
  access_key VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'AK',
  secret_key VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'SK 密文（AES-GCM）',
  path_style TINYINT NOT NULL DEFAULT 1 COMMENT '1=path-style / 0=virtual-hosted',
  public_base_url VARCHAR(255) NOT NULL DEFAULT '' COMMENT '公开读基址',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0 停用 / 1 启用',
  is_default TINYINT NOT NULL DEFAULT 0 COMMENT '1=默认服务商',
  description VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 未删 / 1 已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_storage_provider_code (code),
  KEY idx_sys_storage_provider_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象存储服务商配置';

-- ============================================================
-- 2) sys_file 加 storage_code 列 + 索引
-- ============================================================
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'storage_code'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sys_file ADD COLUMN storage_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''服务商编码（指向 sys_storage_provider.code）'' AFTER bucket',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_sys_file_storage_code'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE sys_file ADD KEY idx_sys_file_storage_code (storage_code)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3) 回填存量 sys_file.storage_code='minio-local'
-- ============================================================
UPDATE sys_file SET storage_code = 'minio-local' WHERE storage_code = '' OR storage_code IS NULL;