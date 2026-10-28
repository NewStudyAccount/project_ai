-- blog 系统接入统一认证中心：blog-portal OIDC RP 种子（2026-10-29，blog-knowledge-base 提案 tasks 10.1）
-- 用途：blog-portal-spa（PUBLIC + PKCE S256），供 blog-portal 问答页登录态（/api/v1/knowledge/ask 需 JWT）
-- 执行：USE auth_db 后执行；INSERT IGNORE，可重复执行
-- 说明：仅测试/开发环境；生产执行前必须按 CLAUDE.md §7 征询
-- 对齐：frontend/blog-portal/src/utils/oidc.ts 默认 client_id；Vite 端口 5200（先例：2026-10-12-blog-admin-spa-client.sql）

USE auth_db;

INSERT IGNORE INTO oauth_client (
  id, client_id, client_secret_hash, client_name, client_type, client_auth_method,
  grant_types, redirect_uris, scopes,
  require_pkce, require_consent, reuse_refresh_tokens,
  access_token_ttl_sec, refresh_token_ttl_sec,
  system_code, owner, env, enabled, remark
) VALUES (
  2026102900000021, 'blog-portal-spa', NULL, 'blog-portal-spa',
  'PUBLIC', 'NONE',
  'authorization_code,refresh_token',
  'http://localhost:5200/callback,http://127.0.0.1:5200/callback,https://blog-portal.example.local/callback',
  'openid,profile',
  1, 0, 0,
  600, 604800,
  'blog', 'scaffold-blog-system', 'local', 1, 'blog-portal OIDC RP（知识问答登录态）'
);
