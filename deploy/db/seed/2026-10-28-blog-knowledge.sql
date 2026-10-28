-- blog_db · 知识库 RBAC + ai_provider 引导种子（blog-knowledge-base）
-- 契约唯一源：docs/knowledge-base-design/table-design.md §4（号段/权限位以此为准）
-- 幂等：INSERT IGNORE + 按 id UPDATE，可重复执行
-- 执行：USE blog_db 后人工执行；生产执行前须按 CLAUDE.md §7 征询
--
-- 本域续号（避开既有占用 1001-1003 / 2001-2008 / 90001-90022 / 6033）：
--   目录 1004 | 菜单 2009-2012 | 按钮 90023-90031 | sys_role_menu 6034-6047
-- 菜单排序：2009 sort=1 / 2012 sort=2 / 2011 sort=3 / 2010 sort=4

USE blog_db;

-- ============================================================
-- 1) 知识库目录 + 菜单（table-design.md §4.1）
-- ============================================================
INSERT IGNORE INTO sys_menu (id, parent_id, type, name, permission, path, component, icon, hidden, requires_auth, sort, status) VALUES
  (1004, 0, 1, '知识库', '', '', '', 'collection', 0, 1, 30, 1),
  (2009, 1004, 2, '知识文档', 'blog:knowledge:list', '/knowledge/docs', 'views/knowledge/DocList.vue', '', 0, 1, 1, 1),
  (2012, 1004, 2, '文章入库', 'blog:knowledge:ingest', '/knowledge/ingest', 'views/knowledge/ArticleIngest.vue', '', 0, 1, 2, 1),
  (2011, 1004, 2, '手动导入', 'blog:knowledge:create', '/knowledge/import', 'views/knowledge/ImportDoc.vue', '', 0, 1, 3, 1),
  (2010, 1004, 2, 'AI 服务商', 'blog:ai-provider:list', '/knowledge/ai-providers', 'views/knowledge/AiProviderList.vue', '', 0, 1, 4, 1);

UPDATE sys_menu SET name = '知识库', type = 1, parent_id = 0, sort = 30, status = 1 WHERE id = 1004;
UPDATE sys_menu SET permission = 'blog:knowledge:list', path = '/knowledge/docs', component = 'views/knowledge/DocList.vue', parent_id = 1004, sort = 1, status = 1 WHERE id = 2009;
UPDATE sys_menu SET permission = 'blog:knowledge:ingest', path = '/knowledge/ingest', component = 'views/knowledge/ArticleIngest.vue', parent_id = 1004, sort = 2, status = 1 WHERE id = 2012;
UPDATE sys_menu SET permission = 'blog:knowledge:create', path = '/knowledge/import', component = 'views/knowledge/ImportDoc.vue', parent_id = 1004, sort = 3, status = 1 WHERE id = 2011;
UPDATE sys_menu SET permission = 'blog:ai-provider:list', path = '/knowledge/ai-providers', component = 'views/knowledge/AiProviderList.vue', parent_id = 1004, sort = 4, status = 1 WHERE id = 2010;

-- ============================================================
-- 2) 按钮（ingest 与 create 分权、revectorize 与 reindex 分权）
-- ============================================================
INSERT IGNORE INTO sys_menu (id, parent_id, type, name, permission, path, component, icon, hidden, requires_auth, sort, status) VALUES
  (90023, 2009, 3, '编辑知识', 'blog:knowledge:update', '', '', '', 0, 1, 1, 1),
  (90024, 2009, 3, '删除知识', 'blog:knowledge:delete', '', '', '', 0, 1, 2, 1),
  (90025, 2009, 3, '重建本文', 'blog:knowledge:revectorize', '', '', '', 0, 1, 3, 1),
  (90026, 2009, 3, '重建索引', 'blog:knowledge:reindex', '', '', '', 0, 1, 4, 1),
  (90027, 2009, 3, '知识库运维', 'blog:knowledge:manage', '', '', '', 0, 1, 5, 1),
  (90028, 2010, 3, '新建服务商', 'blog:ai-provider:create', '', '', '', 0, 1, 1, 1),
  (90029, 2010, 3, '编辑服务商', 'blog:ai-provider:update', '', '', '', 0, 1, 2, 1),
  (90030, 2010, 3, '删除服务商', 'blog:ai-provider:delete', '', '', '', 0, 1, 3, 1),
  (90031, 2010, 3, '测试连通', 'blog:ai-provider:test', '', '', '', 0, 1, 4, 1);

UPDATE sys_menu SET permission = 'blog:knowledge:update', parent_id = 2009, status = 1 WHERE id = 90023;
UPDATE sys_menu SET permission = 'blog:knowledge:delete', parent_id = 2009, status = 1 WHERE id = 90024;
UPDATE sys_menu SET permission = 'blog:knowledge:revectorize', parent_id = 2009, status = 1 WHERE id = 90025;
UPDATE sys_menu SET permission = 'blog:knowledge:reindex', parent_id = 2009, status = 1 WHERE id = 90026;
UPDATE sys_menu SET permission = 'blog:knowledge:manage', parent_id = 2009, status = 1 WHERE id = 90027;
UPDATE sys_menu SET permission = 'blog:ai-provider:create', parent_id = 2010, status = 1 WHERE id = 90028;
UPDATE sys_menu SET permission = 'blog:ai-provider:update', parent_id = 2010, status = 1 WHERE id = 90029;
UPDATE sys_menu SET permission = 'blog:ai-provider:delete', parent_id = 2010, status = 1 WHERE id = 90030;
UPDATE sys_menu SET permission = 'blog:ai-provider:test', parent_id = 2010, status = 1 WHERE id = 90031;

-- ============================================================
-- 3) admin(9001) 角色绑定（续号 6034-6047）
-- ============================================================
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
  (6034, 9001, 1004),
  (6035, 9001, 2009),
  (6036, 9001, 2012),
  (6037, 9001, 2011),
  (6038, 9001, 2010),
  (6039, 9001, 90023),
  (6040, 9001, 90024),
  (6041, 9001, 90025),
  (6042, 9001, 90026),
  (6043, 9001, 90027),
  (6044, 9001, 90028),
  (6045, 9001, 90029),
  (6046, 9001, 90030),
  (6047, 9001, 90031);

-- ============================================================
-- 4) ai_provider 引导默认（table-design.md §4.2）
--    base_url/api_key 由 yml 引导（blog.ai.providers[]）或管理端录入；
--    LLM 型号已裁决锁定 Qwen/Qwen3-8B（P0-2，回填 docs/test-env.md §2.5）
-- ============================================================
INSERT IGNORE INTO ai_provider
  (id, code, kind, type, name, base_url, model, dim, api_key, is_default, status, create_by, update_by)
VALUES
  (2026102800000001, 'siliconflow-embed', 1, 'SILICONFLOW', 'SiliconFlow Embedding（默认）',
   '', 'BAAI/bge-m3', 1024, '', 1, 1, 0, 0),
  (2026102800000002, 'siliconflow-llm', 2, 'SILICONFLOW', 'SiliconFlow LLM（默认）',
   '', 'Qwen/Qwen3-8B', 0, '', 1, 1, 0, 0);

-- 幂等回填 model（重复执行时保持引导型号一致；管理端改过后可按需跳过本段）
UPDATE ai_provider SET model = 'BAAI/bge-m3', dim = 1024, is_default = 1, status = 1 WHERE id = 2026102800000001 AND api_key = '';
UPDATE ai_provider SET model = 'Qwen/Qwen3-8B', is_default = 1, status = 1 WHERE id = 2026102800000002 AND api_key = '';
