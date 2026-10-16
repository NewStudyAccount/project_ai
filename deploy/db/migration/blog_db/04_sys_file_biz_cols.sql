-- blog_db · sys_file 增量补列（blog-content-domain）
-- 仅补 biz_type / biz_id + 索引；不改既有列与 deleted 删除语义
-- 可重复执行（存在性检查）
-- 执行：USE blog_db 后人工执行；生产执行前须按 CLAUDE.md §7 征询

USE blog_db;

-- biz_type：FILE / ARTICLE_COVER / ARTICLE_LIVE / ARTICLE_DRAFT
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'biz_type'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sys_file ADD COLUMN biz_type VARCHAR(32) NOT NULL DEFAULT ''FILE'' COMMENT ''对象用途'' AFTER size_bytes',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'biz_id'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sys_file ADD COLUMN biz_id BIGINT NOT NULL DEFAULT 0 COMMENT ''业务 id，非业务为 0'' AFTER biz_type',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_sys_file_biz'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE sys_file ADD KEY idx_sys_file_biz (biz_type, biz_id)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
