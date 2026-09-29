-- RBAC 框架基线数据模板（L2 自举骨架 + L3 admin + 可选 L3b）
-- 用途：新建系统复制本文件，替换占位符后执行；对齐 docs/template/rbac-design.md §2.0 / §5
-- 执行：INSERT IGNORE + 幂等 UPDATE，可重复执行；仅测试/开发；生产前征询
--
-- 替换清单：
--   {DB}      目标库名
--   {SYS}     权限前缀（全小写，如 user / example / order）
--   {USER_ID} 用户中心 sys_user.id（绑定 admin，可空则注释 3.2）
--
-- id 规范（§2.0 / §2.0.5）：
--   type=1/2 目录/菜单  按树深度：d=1 100x | d=2 200x | d=3 300x | d=4 400x
--                       300x 仅三级菜单，按钮禁止占用
--   type=3 按钮         90001–99999（90000x）；必须挂 type=2 菜单
--   type=4 接口点       5000–5999（500x）；必须挂 type=2 菜单
--   根：parent_id=0（禁止 id=0）
--   sys_role        9001–9999
--   sys_user_role   7001–7999
--   sys_role_menu   6001–6999
--   用户 user_id    用户中心 16 位发号（不在本规范内）

-- USE {DB};

-- ============================================================
-- 1) L2 系统管理骨架（目录 + 菜单 + 按钮）
--    component 须与前端 componentByPath 一致
-- ============================================================
INSERT IGNORE INTO sys_menu (id, parent_id, type, name, permission, path, component, icon, hidden, requires_auth, sort, status) VALUES
  (1001, 0, 1, '系统管理', '', '', '', 'setting', 0, 1, 90, 1),
  (2001, 1001, 2, '菜单管理', '{SYS}:menu:list', '/menus', 'views/menu/MenuList.vue', '', 0, 1, 1, 1),
  (2002, 1001, 2, '角色管理', '{SYS}:role:list', '/roles', 'views/role/RoleList.vue', '', 0, 1, 2, 1),
  (90001, 2001, 3, '新建菜单', '{SYS}:menu:create', '', '', '', 0, 1, 1, 1),
  (90002, 2001, 3, '编辑菜单', '{SYS}:menu:update', '', '', '', 0, 1, 2, 1),
  (90003, 2001, 3, '删除菜单', '{SYS}:menu:delete', '', '', '', 0, 1, 3, 1),
  (90004, 2002, 3, '新建角色', '{SYS}:role:create', '', '', '', 0, 1, 4, 1),
  (90005, 2002, 3, '编辑角色', '{SYS}:role:update', '', '', '', 0, 1, 5, 1),
  (90006, 2002, 3, '删除角色', '{SYS}:role:delete', '', '', '', 0, 1, 6, 1),
  (90007, 2002, 3, '角色授权', '{SYS}:role:assign', '', '', '', 0, 1, 7, 1);

-- 已存在旧数据时回填 component（INSERT IGNORE 不更新旧行）
UPDATE sys_menu SET component = 'views/menu/MenuList.vue' WHERE id = 2001;
UPDATE sys_menu SET component = 'views/role/RoleList.vue' WHERE id = 2002;

-- ============================================================
-- 2) L3 admin 角色（运行时全量授权，无需依赖 sys_role_menu）
-- ============================================================
INSERT IGNORE INTO sys_role (id, role_code, role_name, data_scope, sort, status, remark) VALUES
  (9001, 'admin', '管理员', 1, 1, 1, 'framework rbac');

-- 2b) 可选 L3b：运营 / 只读（按系统需要取消注释；data_scope 必须为 1/2/3）
-- INSERT IGNORE INTO sys_role (id, role_code, role_name, data_scope, sort, status, remark) VALUES
--   (9002, 'operator', '运营', 1, 2, 1, 'optional'),
--   (9003, 'viewer', '只读', 3, 3, 1, 'optional');

-- ============================================================
-- 3) 绑定
-- 3.1 admin 显式绑 L2（便于角色授权页展示；运行时 admin 本已全量）
-- 3.2 将用户中心账号绑到 admin（生产勿预置业务人员）
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
  (6010, 9001, 90007);

-- INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES
--   (7001, {USER_ID}, 9001);

-- ============================================================
-- 4) L4 域节点示例（各系统自行追加）
--    目录/菜单按深度 100x/200x/300x；按钮 90000x；接口点 500x
-- ============================================================
-- INSERT IGNORE INTO sys_menu (id, parent_id, type, name, permission, path, component, icon, hidden, requires_auth, sort, status) VALUES
--   (1002, 0, 1, '业务域', '', '', '', 'folder', 0, 1, 1, 1),
--   (2003, 1002, 2, '示例列表', '{SYS}:demo:list', '/demos', 'views/demo/DemoList.vue', '', 0, 1, 1, 1),
--   (90008, 2003, 3, '新建示例', '{SYS}:demo:create', '', '', '', 0, 1, 1, 1);
