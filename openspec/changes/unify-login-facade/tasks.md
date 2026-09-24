## 1. Client 种子与允许列表

- [x] 1.1 编写 `deploy/db/seed/` SQL（`INSERT IGNORE`）：新增 `user-admin-spa`、`auth-admin-spa` 两个 PUBLIC client（PKCE S256、`authorization_code,refresh_token`、精确 `redirect_uris`、TTL 默认 600/604800），生产执行前征询。
- [x] 1.2 在 `auth-service` 配置 `post-logout-redirect-uris` 允许列表（覆盖两 RP 的 `/logged-out` 或 `/` 与 local/test 源），**不**改 `oauth_client` 表结构；登记 `docs/test-env.md`。
- [x] 1.3 更新 `docs/test-env.md`：`user-admin`/`auth-admin` 的 redirect_uri、post_logout_redirect_uri、信任域名/CORS。

## 2. 认证中心统一登出

- [x] 2.1 实现 `/connect/logout`（GET/POST）：校验 `post_logout_redirect_uri` 精确命中允许列表，非法则走默认登出完成响应，禁止开放重定向。
- [x] 2.2 登出时失效 `AUTH_SSO_SESSION`（Redis 删键 + Cookie 过期），并按既有 user/grant/会话索引吊销 RT 与活跃 grant 台账（与踢下线同源），写安全审计；无会话时不泄露账号存在性。
- [x] 2.3 放行安全配置中的 `/connect/logout`，登录/token/logout 端点 `@RateLimit`；日志带 traceId 且不含密码/完整 Token。
- [x] 2.4 后端自检：`cd backend/auth && mvn -q compile`。

## 3. user-admin 切换为 OIDC RP

- [x] 3.1 删除 `user-admin` 本地 `Login.vue` 账密/放行壳与相关路由；未登录路由守卫改为拼装 PKCE S256 的 `/oauth2/authorize` 顶层跳转（存 `state`/`nonce`/`code_verifier`/`returnTo`）。
- [x] 3.2 新增 `/callback` 路由（非登录表单）：校验 `state`、`code` 换票、保存 AT/RT/ID Token、`replace` 回 `returnTo`；失败提示并重新授权，禁止本地账密表单。
- [x] 3.3 `src/api` 统一附加 Bearer；过期用 RT 起新 AT（轮转保存）；401 清本地并回登录门面；权限仍走 `/me/menus`、`/me/permissions`。
- [x] 3.4 实现退出：清本地令牌 → `/connect/logout` → 回 `post_logout_redirect_uri`；主入口不得提供“仅本地退出”。
- [x] 3.5 前端自检：`cd frontend/user-admin && npm run lint && npm run type-check`。

## 4. auth-admin 切换为 OIDC RP

- [x] 4.1 删除 `auth-admin` 本地 form-login（`views/Login.vue`、`api/login.ts`）与同域会话假设；路由守卫直跳 `auth-admin-spa` 的 `/oauth2/authorize`（PKCE S256）。
- [x] 4.2 新增 `/callback` 换票与令牌保存（与 user-admin 同构，独立 client_id/redirect）；管理 API 走 `src/api` 带 AT。
- [x] 4.3 实现退出：清本地令牌 → `/connect/logout` → 回允许的 post-logout 地址。
- [x] 4.4 前端自检：`cd frontend/auth-admin && npm run lint && npm run type-check`。

## 5. auth-portal 配合唯一登录/登出

- [x] 5.1 确认 `auth-portal` 无自用 OIDC code 登录流；登录成功仍只建/续 `AUTH_SSO_SESSION`。
- [x] 5.2 增加统一登出入口（或成功态退出）：跳转 `/connect/logout`，完成后回允许地址。

## 6. 文档与冒烟

- [x] 6.1 更新 `docs/auth-deploy.md` 冒烟清单：唯一登录门面、双 RP 换票、任一端登出后另一端需重新登录、非法 post-logout 被拒。
- [x] 6.2 local 冒烟：无 SSO 时 RP 跳 portal 表单；有 SSO 免登回跳换票；`user-admin`/`auth-admin` 各自 client 隔离；统一登出后 RT 刷新失败。
- [x] 6.3 对照 `CLAUDE.md` §7.1 反模式自查（无本地验密、无自建返回体、无 `console.log` 残留）；生产 SQL/依赖锁变更前按红线征询。
