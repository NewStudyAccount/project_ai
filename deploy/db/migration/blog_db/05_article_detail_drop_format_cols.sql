-- blog_db · blog_article_detail 删除正文格式列（article-content-markdown-only）
-- 正文唯一 Markdown：删 content_format / draft_content_format（保留 word_count / draft_word_count）
-- 可重复执行（存在性检查）
-- 【不可逆】执行前须按 CLAUDE.md §7 征询；建议先备份表结构
-- 执行：USE blog_db 后人工执行

USE blog_db;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'blog_article_detail' AND COLUMN_NAME = 'content_format'
);
SET @sql := IF(@col_exists > 0,
  'ALTER TABLE blog_article_detail DROP COLUMN content_format',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'blog_article_detail' AND COLUMN_NAME = 'draft_content_format'
);
SET @sql := IF(@col_exists > 0,
  'ALTER TABLE blog_article_detail DROP COLUMN draft_content_format',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
