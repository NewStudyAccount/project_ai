-- 冒烟测试种子数据（2026-09-24）
-- 用途：unified-auth-center / user-center 本地联调与 OIDC 冒烟（admin / admin123、auth-portal-spa）
-- 执行：先选库再执行对应段；均为 INSERT IGNORE，可重复执行
-- 说明：仅测试/开发环境；生产禁止执行；密码为 BCrypt，明文仅文档注释中出现

-- ============================================================
-- 1) user_db：用户主数据（账号 admin，密码在 auth_db.sys_credential）
-- ============================================================
USE user_db;

INSERT IGNORE INTO sys_user (
  id, username, real_name, nickname, email, phone, avatar, status, remark
) VALUES (
  2026092400000001, 'admin', '管理员', 'Admin',
  'admin@example.local', '13800000000', '', 1, 'smoke'
);

INSERT IGNORE INTO sys_user_profile (
  id, user_id, gender, birthday, address, extra_json
) VALUES (
  2026092400000002, 2026092400000001, 0, NULL, '', ''
);

-- ============================================================
-- 2) auth_db：凭证、OAuth Client、RBAC
-- ============================================================
USE auth_db;

-- 2.1 密码凭证（明文仅注释：admin123）
INSERT IGNORE INTO sys_credential (
  id, user_id, credential_type, secret_ref, verified, status
) VALUES (
  2026092400000011, 2026092400000001, 'PASSWORD',
  '$2a$10$e6kyewkxAzGlI4gEVuNM8u.ZENT3Qm3V9tps1vtLZndeW4vvxWa72',
  1, 1
);

-- 2.2 公开客户端（PKCE S256）
INSERT IGNORE INTO oauth_client (
  id, client_id, client_secret_hash, client_name, client_type, client_auth_method,
  grant_types, redirect_uris, scopes,
  require_pkce, require_consent, reuse_refresh_tokens,
  access_token_ttl_sec, refresh_token_ttl_sec,
  system_code, owner, env, enabled, remark
) VALUES (
  2026092400000021, 'auth-portal-spa', NULL, 'auth-portal-spa',
  'PUBLIC', 'NONE',
  'authorization_code,refresh_token',
  'http://127.0.0.1:5174/callback',
  'openid,profile',
  1, 0, 0,
  600, 604800,
  'auth', 'smoke', 'local', 1, 'local smoke client'
);

-- 2.3 菜单（1 目录 / 2 菜单 / 3 按钮）
INSERT IGNORE INTO sys_menu (id, parent_id, type, name, permission, path, component, icon, hidden, requires_auth, sort, status) VALUES
  (2026092400000031, 0, 1, 'ops', '', '', '', '', 0, 1, 1, 1),
  (2026092400000032, 2026092400000031, 2, 'clients', 'auth:client:list', '/clients', '', '', 0, 1, 1, 1),
  (2026092400000033, 2026092400000031, 2, 'grants', 'auth:grant:revoke', '/grants', '', '', 0, 1, 2, 1),
  (2026092400000034, 2026092400000031, 2, 'audit', 'auth:audit:list', '/audit', '', '', 0, 1, 3, 1),
  (2026092400000035, 2026092400000031, 2, 'menus', 'auth:menu:list', '/menus', '', '', 0, 1, 4, 1),
  (2026092400000036, 2026092400000031, 2, 'roles', 'auth:role:list', '/roles', '', '', 0, 1, 5, 1),
  (2026092400000037, 2026092400000031, 3, 'clientCreate', 'auth:client:create', '', '', '', 0, 1, 1, 1),
  (2026092400000038, 2026092400000031, 3, 'clientSecret', 'auth:client:secret', '', '', '', 0, 1, 2, 1),
  (2026092400000040, 2026092400000031, 3, 'grantList', 'auth:grant:list', '', '', '', 0, 1, 10, 1),
  (2026092400000041, 2026092400000031, 3, 'clientUpdate', 'auth:client:update', '', '', '', 0, 1, 11, 1),
  (2026092400000042, 2026092400000031, 3, 'clientDelete', 'auth:client:delete', '', '', '', 0, 1, 12, 1),
  (2026092400000043, 2026092400000031, 3, 'menuCreate', 'auth:menu:create', '', '', '', 0, 1, 13, 1),
  (2026092400000044, 2026092400000031, 3, 'menuUpdate', 'auth:menu:update', '', '', '', 0, 1, 14, 1),
  (2026092400000045, 2026092400000031, 3, 'menuDelete', 'auth:menu:delete', '', '', '', 0, 1, 15, 1),
  (2026092400000046, 2026092400000031, 3, 'roleCreate', 'auth:role:create', '', '', '', 0, 1, 16, 1),
  (2026092400000047, 2026092400000031, 3, 'roleUpdate', 'auth:role:update', '', '', '', 0, 1, 17, 1),
  (2026092400000048, 2026092400000031, 3, 'roleDelete', 'auth:role:delete', '', '', '', 0, 1, 18, 1),
  (2026092400000049, 2026092400000031, 3, 'roleAssign', 'auth:role:assign', '', '', '', 0, 1, 19, 1);

-- 2.4 角色与用户绑定
INSERT IGNORE INTO sys_role (id, role_code, role_name, data_scope, sort, status, remark) VALUES
  (2026092400000041, 'admin', 'admin', 'ALL', 1, 1, 'smoke admin');

INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES
  (2026092400000051, 2026092400000001, 2026092400000041);

-- 2.5 角色-菜单（授予上列全部菜单/按钮）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
  (2026092400000061, 2026092400000041, 2026092400000031),
  (2026092400000062, 2026092400000041, 2026092400000032),
  (2026092400000063, 2026092400000041, 2026092400000033),
  (2026092400000064, 2026092400000041, 2026092400000034),
  (2026092400000065, 2026092400000041, 2026092400000035),
  (2026092400000066, 2026092400000041, 2026092400000036),
  (2026092400000067, 2026092400000041, 2026092400000037),
  (2026092400000068, 2026092400000041, 2026092400000038),
  (2026092400000081, 2026092400000041, 2026092400000040),
  (2026092400000082, 2026092400000041, 2026092400000041),
  (2026092400000083, 2026092400000041, 2026092400000042),
  (2026092400000084, 2026092400000041, 2026092400000043),
  (2026092400000085, 2026092400000041, 2026092400000044),
  (2026092400000086, 2026092400000041, 2026092400000045),
  (2026092400000087, 2026092400000041, 2026092400000046),
  (2026092400000088, 2026092400000041, 2026092400000047),
  (2026092400000089, 2026092400000041, 2026092400000048),
  (2026092400000090, 2026092400000041, 2026092400000049);

-- ============================================================
-- 3) 冒烟账号速查
-- ------------------------------------------------------------
-- 登录：POST /login  username=admin  password=admin123
-- Client：client_id=auth-portal-spa（PUBLIC + PKCE S256）
-- redirect_uri：http://127.0.0.1:5174/callback
-- user.id / JWT sub：2026092400000001
-- ============================================================
