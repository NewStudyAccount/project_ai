-- blog 系统接入统一认证中心：blog-admin OIDC RP 种子（2026-10-12）
-- 用途：blog-admin-spa（PUBLIC + PKCE S256），供 blog-admin 经统一登录门面换票
-- 执行：USE auth_db 后执行；INSERT IGNORE，可重复执行
-- 说明：仅测试/开发环境；生产执行前必须按 CLAUDE.md §7 征询
-- 对齐：frontend/blog-admin/src/utils/oidc.ts 默认 client_id；Vite 端口 5100

USE auth_db;

INSERT IGNORE INTO oauth_client (
  id, client_id, client_secret_hash, client_name, client_type, client_auth_method,
  grant_types, redirect_uris, scopes,
  require_pkce, require_consent, reuse_refresh_tokens,
  access_token_ttl_sec, refresh_token_ttl_sec,
  system_code, owner, env, enabled, remark
) VALUES (
  2026101200000021, 'blog-admin-spa', NULL, 'blog-admin-spa',
  'PUBLIC', 'NONE',
  'authorization_code,refresh_token',
  'http://localhost:5100/callback,http://127.0.0.1:5100/callback,https://blog-admin.example.local/callback',
  'openid,profile',
  1, 0, 0,
  600, 604800,
  'blog', 'scaffold-blog-system', 'local', 1, 'blog-admin OIDC RP'
);
