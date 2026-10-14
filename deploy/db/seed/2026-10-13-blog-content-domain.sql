-- blog_db · 内容业务域种子（blog-content-domain）
-- 对齐：docs/template/rbac-design.md §2.0 短号；INSERT IGNORE 可重复执行
-- 号段：菜单 d=1 100x | d=2 200x | 按钮 90000x | sys_role_menu 6001-6999
-- 执行：USE blog_db 后执行；仅测试/开发
--
-- 本文件新增：
--   1003 内容管理目录
--   2005 分类管理 / 2006 标签管理 / 2007 文章管理 / 2008 站点配置
--   按钮 90012+
--   site.* 配置键
--   可选默认分类「未分类」

USE blog_db;

-- ============================================================
-- 1) 内容管理菜单域
-- ============================================================
INSERT IGNORE INTO sys_menu (id, parent_id, type, name, permission, path, component, icon, hidden, requires_auth, sort, status) VALUES
  (1003, 0, 1, '内容管理', '', '', '', 'document', 0, 1, 20, 1),
  (2005, 1003, 2, '分类管理', 'blog:category:list', '/categories', 'views/content/CategoryList.vue', '', 0, 1, 1, 1),
  (2006, 1003, 2, '标签管理', 'blog:tag:list', '/tags', 'views/content/TagList.vue', '', 0, 1, 2, 1),
  (2007, 1003, 2, '文章管理', 'blog:article:list', '/articles', 'views/content/ArticleList.vue', '', 0, 1, 3, 1),
  (2008, 1003, 2, '站点配置', 'blog:site:list', '/site-config', 'views/content/SiteConfig.vue', '', 0, 1, 4, 1);

UPDATE sys_menu SET component = 'views/content/CategoryList.vue' WHERE id = 2005;
UPDATE sys_menu SET component = 'views/content/TagList.vue' WHERE id = 2006;
UPDATE sys_menu SET component = 'views/content/ArticleList.vue' WHERE id = 2007;
UPDATE sys_menu SET component = 'views/content/SiteConfig.vue' WHERE id = 2008;

INSERT IGNORE INTO sys_menu (id, parent_id, type, name, permission, path, component, icon, hidden, requires_auth, sort, status) VALUES
  (90012, 2005, 3, '新建分类', 'blog:category:create', '', '', '', 0, 1, 1, 1),
  (90013, 2005, 3, '编辑分类', 'blog:category:update', '', '', '', 0, 1, 2, 1),
  (90014, 2005, 3, '删除分类', 'blog:category:delete', '', '', '', 0, 1, 3, 1),
  (90015, 2006, 3, '新建标签', 'blog:tag:create', '', '', '', 0, 1, 1, 1),
  (90016, 2006, 3, '编辑标签', 'blog:tag:update', '', '', '', 0, 1, 2, 1),
  (90017, 2006, 3, '删除标签', 'blog:tag:delete', '', '', '', 0, 1, 3, 1),
  (90018, 2007, 3, '新建文章', 'blog:article:create', '', '', '', 0, 1, 1, 1),
  (90019, 2007, 3, '编辑文章', 'blog:article:update', '', '', '', 0, 1, 2, 1),
  (90020, 2007, 3, '发布下架', 'blog:article:publish', '', '', '', 0, 1, 3, 1),
  (90021, 2007, 3, '删除文章', 'blog:article:delete', '', '', '', 0, 1, 4, 1),
  (90022, 2008, 3, '编辑站点配置', 'blog:site:update', '', '', '', 0, 1, 1, 1);

-- ============================================================
-- 2) admin 角色绑定内容菜单（续号 6018+）
-- ============================================================
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
  (6018, 9001, 1003),
  (6019, 9001, 2005),
  (6020, 9001, 2006),
  (6021, 9001, 2007),
  (6022, 9001, 2008),
  (6023, 9001, 90012),
  (6024, 9001, 90013),
  (6025, 9001, 90014),
  (6026, 9001, 90015),
  (6027, 9001, 90016),
  (6028, 9001, 90017),
  (6029, 9001, 90018),
  (6030, 9001, 90019),
  (6031, 9001, 90020),
  (6032, 9001, 90021),
  (6033, 9001, 90022);

-- ============================================================
-- 3) 站点配置种子
-- ============================================================
INSERT IGNORE INTO blog_site_config (id, config_key, config_value, remark, create_by, update_by) VALUES
  (1, 'site.title', 'Blog', '站点标题', 0, 0),
  (2, 'site.description', '', '站点描述', 0, 0),
  (3, 'site.copyright', '', '备案/版权', 0, 0),
  (4, 'site.about', '', '关于正文', 0, 0);

-- ============================================================
-- 4) 可选默认分类「未分类」（id 用 16 位发号风格占位，实施环境可改）
-- ============================================================
INSERT IGNORE INTO blog_category (id, parent_id, name, slug, description, sort, status, create_by, update_by)
VALUES (2026101300000001, 0, '未分类', 'uncategorized', '默认分类', 0, 1, 0, 0);
