-- RBAC 框架基线数据模板（L2 自举骨架 + L3 admin）
-- 用途：新建系统复制本文件，替换 {sys} 与 USE 库名后执行；对齐 rbac-design.md §5
-- 执行：INSERT IGNORE + 幂等 UPDATE，可重复执行；仅测试/开发；生产前征询
--
-- 替换清单：
--   {DB}   目标库名
--   {SYS}  权限前缀（全小写，如 user / example / order）
--   {PREFIX_ID} 固定 id 前缀 16 位日期段，如 20260925000000
--   {USER_ID} 用户中心 sys_user.id（绑定 admin，可空则跳过 3.2）

-- USE {DB};

-- ============================================================
-- 1) L2 系统管理骨架（目录 + 菜单 + 按钮）
--    component 须与前端 componentByPath 一致
-- ============================================================
INSERT IGNORE INTO sys_menu (id, parent_id, type, name, permission, path, component, icon, hidden, requires_auth, sort, status) VALUES
  ({PREFIX_ID}31, 0, 1, '系统管理', '', '', '', 'setting', 0, 1, 90, 1),
  ({PREFIX_ID}32, {PREFIX_ID}31, 2, '菜单管理', '{SYS}:menu:list', '/menus', 'views/menu/MenuList.vue', '', 0, 1, 1, 1),
  ({PREFIX_ID}33, {PREFIX_ID}31, 2, '角色管理', '{SYS}:role:list', '/roles', 'views/role/RoleList.vue', '', 0, 1, 2, 1),
  ({PREFIX_ID}37, {PREFIX_ID}31, 3, '新建菜单', '{SYS}:menu:create', '', '', '', 0, 1, 1, 1),
  ({PREFIX_ID}38, {PREFIX_ID}31, 3, '编辑菜单', '{SYS}:menu:update', '', '', '', 0, 1, 2, 1),
  ({PREFIX_ID}39, {PREFIX_ID}31, 3, '删除菜单', '{SYS}:menu:delete', '', '', '', 0, 1, 3, 1),
  ({PREFIX_ID}40, {PREFIX_ID}31, 3, '新建角色', '{SYS}:role:create', '', '', '', 0, 1, 4, 1),
  ({PREFIX_ID}41, {PREFIX_ID}31, 3, '编辑角色', '{SYS}:role:update', '', '', '', 0, 1, 5, 1),
  ({PREFIX_ID}42, {PREFIX_ID}31, 3, '删除角色', '{SYS}:role:delete', '', '', '', 0, 1, 6, 1),
  ({PREFIX_ID}43, {PREFIX_ID}31, 3, '角色授权', '{SYS}:role:assign', '', '', '', 0, 1, 7, 1);

-- 已存在旧数据时回填 component
UPDATE sys_menu SET component = 'views/menu/MenuList.vue' WHERE id = {PREFIX_ID}32;
UPDATE sys_menu SET component = 'views/role/RoleList.vue' WHERE id = {PREFIX_ID}33;

-- ============================================================
-- 2) L3 admin 角色（运行时全量授权，无需 sys_role_menu）
-- ============================================================
INSERT IGNORE INTO sys_role (id, role_code, role_name, data_scope, sort, status, remark) VALUES
  ({PREFIX_ID}51, 'admin', '管理员', 1, 1, 1, 'framework rbac');

-- ============================================================
-- 3) 绑定
-- 3.1 admin 显式绑 L2（便于角色授权页展示；运行时 admin 本已全量）
-- 3.2 将用户中心账号绑到 admin
-- ============================================================
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
  ({PREFIX_ID}71, {PREFIX_ID}51, {PREFIX_ID}31),
  ({PREFIX_ID}72, {PREFIX_ID}51, {PREFIX_ID}32),
  ({PREFIX_ID}73, {PREFIX_ID}51, {PREFIX_ID}33),
  ({PREFIX_ID}74, {PREFIX_ID}51, {PREFIX_ID}37),
  ({PREFIX_ID}75, {PREFIX_ID}51, {PREFIX_ID}38),
  ({PREFIX_ID}76, {PREFIX_ID}51, {PREFIX_ID}39),
  ({PREFIX_ID}77, {PREFIX_ID}51, {PREFIX_ID}40),
  ({PREFIX_ID}78, {PREFIX_ID}51, {PREFIX_ID}41),
  ({PREFIX_ID}79, {PREFIX_ID}51, {PREFIX_ID}42),
  ({PREFIX_ID}80, {PREFIX_ID}51, {PREFIX_ID}43);

-- INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES
--   ({PREFIX_ID}61, {USER_ID}, {PREFIX_ID}51);
