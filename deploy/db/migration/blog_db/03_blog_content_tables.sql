-- blog_db · 内容业务域表（blog-content-domain）
-- 字段契约：docs/blog-system/table-design.md §2（唯一源）
-- 审计 5 字段、16 位发号主键、uk_/idx_ 命名、单表索引 ≤5、无物理外键
-- 执行：USE blog_db 后人工执行；生产执行前须按 CLAUDE.md §7 征询
-- 可重复执行（IF NOT EXISTS）

USE blog_db;

CREATE TABLE IF NOT EXISTS blog_category (
  id BIGINT NOT NULL COMMENT '16 位发号',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父分类；0=根。应用校验最多 2 级',
  name VARCHAR(64) NOT NULL COMMENT '分类名',
  slug VARCHAR(64) NOT NULL COMMENT 'URL 标识，全局唯一',
  description VARCHAR(255) NOT NULL DEFAULT '' COMMENT '描述',
  sort INT NOT NULL DEFAULT 0 COMMENT '升序',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blog_category_slug (slug),
  KEY idx_blog_category_parent_id (parent_id),
  KEY idx_blog_category_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章分类';

CREATE TABLE IF NOT EXISTS blog_tag (
  id BIGINT NOT NULL COMMENT '16 位发号',
  name VARCHAR(64) NOT NULL COMMENT '标签名，全局唯一',
  slug VARCHAR(64) NOT NULL COMMENT 'URL 标识',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blog_tag_name (name),
  UNIQUE KEY uk_blog_tag_slug (slug),
  KEY idx_blog_tag_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签';

CREATE TABLE IF NOT EXISTS blog_article (
  id BIGINT NOT NULL COMMENT '16 位发号',
  slug VARCHAR(120) NOT NULL DEFAULT '' COMMENT '友好 URL；空则用 id；非空唯一',
  category_id BIGINT NOT NULL DEFAULT 0 COMMENT '分类；0=未分类',
  title VARCHAR(200) NOT NULL COMMENT '标题',
  summary VARCHAR(500) NOT NULL DEFAULT '' COMMENT '摘要',
  cover_file_id BIGINT NOT NULL DEFAULT 0 COMMENT '封面 sys_file.id；0=无',
  author_id BIGINT NOT NULL DEFAULT 0 COMMENT '作者 id 快照',
  author_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT '作者显示名快照',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 草稿 / 1 已发布 / 2 已下架',
  publish_time DATETIME NULL DEFAULT NULL COMMENT '最近发布时间',
  is_top TINYINT NOT NULL DEFAULT 0 COMMENT '1 置顶',
  view_count BIGINT NOT NULL DEFAULT 0 COMMENT '预留阅读量，本期无逻辑',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blog_article_slug (slug),
  KEY idx_blog_article_category_id (category_id),
  KEY idx_blog_article_status_pub (status, publish_time),
  KEY idx_blog_article_author_id (author_id),
  KEY idx_blog_article_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章主表';

CREATE TABLE IF NOT EXISTS blog_article_detail (
  id BIGINT NOT NULL COMMENT '16 位发号',
  article_id BIGINT NOT NULL COMMENT '与主表 1:1',
  content_file_id BIGINT NOT NULL DEFAULT 0 COMMENT '线上正文 sys_file.id；0=未发布过',
  draft_file_id BIGINT NOT NULL DEFAULT 0 COMMENT '草稿正文 sys_file.id；0=无草稿',
  content_format VARCHAR(16) NOT NULL DEFAULT 'markdown' COMMENT '线上格式 markdown|html',
  word_count INT NOT NULL DEFAULT 0 COMMENT '线上字数',
  draft_content_format VARCHAR(16) NOT NULL DEFAULT 'markdown' COMMENT '草稿格式',
  draft_word_count INT NOT NULL DEFAULT 0 COMMENT '草稿字数',
  seo_title VARCHAR(200) NOT NULL DEFAULT '' COMMENT '线上 SEO',
  seo_description VARCHAR(300) NOT NULL DEFAULT '' COMMENT '线上 SEO',
  draft_seo_title VARCHAR(200) NOT NULL DEFAULT '' COMMENT '草稿 SEO',
  draft_seo_description VARCHAR(300) NOT NULL DEFAULT '' COMMENT '草稿 SEO',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blog_article_detail_article_id (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章详细表（正文双轨文件引用）';

CREATE TABLE IF NOT EXISTS blog_article_tag (
  id BIGINT NOT NULL COMMENT '16 位发号',
  article_id BIGINT NOT NULL COMMENT '文章 id',
  tag_id BIGINT NOT NULL COMMENT '标签 id',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blog_article_tag_at (article_id, tag_id),
  KEY idx_blog_article_tag_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章-标签';

CREATE TABLE IF NOT EXISTS blog_site_config (
  id BIGINT NOT NULL COMMENT '16 位发号',
  config_key VARCHAR(64) NOT NULL COMMENT '配置键',
  config_value VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '配置值',
  remark VARCHAR(255) NOT NULL DEFAULT '' COMMENT '说明',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blog_site_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点配置';
