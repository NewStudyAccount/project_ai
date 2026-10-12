-- blog_db · RBAC 框架基线数据（L2 自举骨架 + L4 域节点 + L3 admin + L5 绑定）
-- 对齐：docs/template/rbac-design.md §2.0 / §5（短号，非 16 位发号）
--       component 与 frontend/blog-admin/src/router/index.ts componentByPath 一致
-- 执行：USE blog_db 后执行；INSERT IGNORE + 幂等 UPDATE，可重复执行
-- 环境：仅测试/开发；生产执行前须按 CLAUDE.md §7 征询（生产一般只导 L1+L2+L3）
--
-- 号段（§5.3）：
--   菜单 d=1 100x | d=2 200x | 按钮 90000x | 接口点 500x
--   角色 9001-9999 | sys_role_menu 6001-6999 | sys_user_role 7001-7999
--   根 parent_id=0（禁止 id=0）

USE blog_db;

-- ============================================================
-- 1) L2 系统管理骨架（目录 + 菜单 + 按钮）
-- ============================================================
INSERT IGNORE INTO sys_menu (id, parent_id, type, name, permission, path, component, icon, hidden, requires_auth, sort, status) VALUES
  (1001, 0, 1, '系统管理', '', '', '', 'setting', 0, 1, 90, 1),
  (2001, 1001, 2, '菜单管理', 'blog:menu:list', '/menus', 'views/menu/MenuList.vue', '', 0, 1, 1, 1),
  (2002, 1001, 2, '角色管理', 'blog:role:list', '/roles', 'views/role/RoleList.vue', '', 0, 1, 2, 1),
  (90001, 2001, 3, '新建菜单', 'blog:menu:create', '', '', '', 0, 1, 1, 1),
  (90002, 2001, 3, '编辑菜单', 'blog:menu:update', '', '', '', 0, 1, 2, 1),
  (90003, 2001, 3, '删除菜单', 'blog:menu:delete', '', '', '', 0, 1, 3, 1),
  (90004, 2002, 3, '新建角色', 'blog:role:create', '', '', '', 0, 1, 1, 1),
  (90005, 2002, 3, '编辑角色', 'blog:role:update', '', '', '', 0, 1, 2, 1),
  (90006, 2002, 3, '删除角色', 'blog:role:delete', '', '', '', 0, 1, 3, 1),
  (90007, 2002, 3, '角色授权', 'blog:role:assign', '', '', '', 0, 1, 4, 1),
  (90008, 2002, 3, '分配用户', 'blog:role:user-assign', '', '', '', 0, 1, 5, 1);

UPDATE sys_menu SET component = 'views/menu/MenuList.vue' WHERE id = 2001;
UPDATE sys_menu SET component = 'views/role/RoleList.vue' WHERE id = 2002;

-- ============================================================
-- 2) L4 业务域：文件管理 + 审计日志
-- ============================================================
INSERT IGNORE INTO sys_menu (id, parent_id, type, name, permission, path, component, icon, hidden, requires_auth, sort, status) VALUES
  (1002, 0, 1, '业务域', '', '', '', 'folder', 0, 1, 10, 1),
  (2003, 1002, 2, '文件管理', 'blog:file:list', '/files', 'views/file/FileList.vue', '', 0, 1, 1, 1),
  (2004, 1002, 2, '审计日志', 'blog:audit:list', '/audits', 'views/audit/AuditList.vue', '', 0, 1, 2, 1),
  (90009, 2003, 3, '上传文件', 'blog:file:upload', '', '', '', 0, 1, 1, 1),
  (90010, 2003, 3, '删除文件', 'blog:file:delete', '', '', '', 0, 1, 2, 1),
  (90011, 2003, 3, '预览下载', 'blog:file:read', '', '', '', 0, 1, 3, 1);

UPDATE sys_menu SET component = 'views/file/FileList.vue' WHERE id = 2003;
UPDATE sys_menu SET component = 'views/audit/AuditList.vue' WHERE id = 2004;

-- ============================================================
-- 3) L3 admin 角色（运行时全量；role_code=admin 禁止改语义）
-- ============================================================
INSERT IGNORE INTO sys_role (id, role_code, role_name, data_scope, sort, status, remark) VALUES
  (9001, 'admin', '管理员', 1, 1, 1, 'blog rbac seed');

-- INSERT IGNORE INTO sys_role (id, role_code, role_name, data_scope, sort, status, remark) VALUES
--   (9002, 'operator', '运营', 1, 2, 1, 'optional'),
--   (9003, 'viewer', '只读', 3, 3, 1, 'optional');

-- ============================================================
-- 4) L5 绑定：admin 显式绑全量 + 冒烟用户 -> admin
-- ============================================================
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
  (6001, 9001, 1001),
  (6002, 9001, 2001),
  (6003, 9001, 2002),
  (6004, 9001, 90001),
  (6005, 9001, 90002),
  (6006, 9001, 90003),
  (6007, 9001, 90004),
  (6008, 9001, 90005),
  (6009, 9001, 90006),
  (6010, 9001, 90007),
  (6011, 9001, 90008),
  (6012, 9001, 1002),
  (6013, 9001, 2003),
  (6014, 9001, 2004),
  (6015, 9001, 90009),
  (6016, 9001, 90010),
  (6017, 9001, 90011);

INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES
  (7001, 2026092400000001, 9001);

-- 5) 幂等备注：新增节点按 §2.0.2 续号另开 seed；修改已有节点用按 id 的 UPDATE；禁止改历史 id