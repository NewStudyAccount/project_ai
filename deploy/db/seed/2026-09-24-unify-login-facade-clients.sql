-- unify-login-facade：RP 独立 OAuth Client 种子（2026-09-24）
-- 用途：user-admin-spa / auth-admin-spa（PUBLIC + PKCE S256），供唯一登录门面下 RP 换票
-- 执行：USE auth_db 后执行；INSERT IGNORE，可重复执行
-- 说明：仅测试/开发环境；生产执行前必须按 CLAUDE.md §7 征询

USE auth_db;

-- 用户中心管理端（OIDC RP）
INSERT IGNORE INTO oauth_client (
  id, client_id, client_secret_hash, client_name, client_type, client_auth_method,
  grant_types, redirect_uris, scopes,
  require_pkce, require_consent, reuse_refresh_tokens,
  access_token_ttl_sec, refresh_token_ttl_sec,
  system_code, owner, env, enabled, remark
) VALUES (
  2026092400000022, 'user-admin-spa', NULL, 'user-admin-spa',
  'PUBLIC', 'NONE',
  'authorization_code,refresh_token',
  'http://localhost:5173/callback,http://127.0.0.1:5173/callback,https://user-admin.example.local/callback',
  'openid,profile',
  1, 0, 0,
  600, 604800,
  'user', 'unify-login-facade', 'local', 1, 'user-admin OIDC RP'
);

-- 认证运营后台（OIDC RP）
INSERT IGNORE INTO oauth_client (
  id, client_id, client_secret_hash, client_name, client_type, client_auth_method,
  grant_types, redirect_uris, scopes,
  require_pkce, require_consent, reuse_refresh_tokens,
  access_token_ttl_sec, refresh_token_ttl_sec,
  system_code, owner, env, enabled, remark
) VALUES (
  2026092400000023, 'auth-admin-spa', NULL, 'auth-admin-spa',
  'PUBLIC', 'NONE',
  'authorization_code,refresh_token',
  'http://localhost:5175/callback,http://127.0.0.1:5175/callback,https://auth-admin.example.local/callback',
  'openid,profile',
  1, 0, 0,
  600, 604800,
  'auth', 'unify-login-facade', 'local', 1, 'auth-admin OIDC RP'
);
