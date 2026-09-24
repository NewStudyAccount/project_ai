## Why

仓库内已有统一认证中心（IdP：`auth-portal` + `auth-service`）与用户中心，但三套前端仍各自持有登录入口：`auth-portal` 账密表单、`auth-admin` 复制表单、`user-admin` 壳阶段本地放行。多系统边界（独立部署/数据/权限）是合理的，但登录职责未收敛，SSO Cookie 跨子域也不共享，「统一认证」未兑现为唯一登录体验。本变更把登录面收敛到 `auth-portal` 一处，并将 `user-admin` / `auth-admin` 切换为标准 OIDC RP；同时把「唯一登出」从二期拉入本期。

## What Changes

- **唯一登录门面**：仅 `auth-portal` 保留账密表单；`user-admin` / `auth-admin` **删除**本地 `Login.vue` 与自建 `POST /login`，路由守卫在未认证时直接跳转 `/oauth2/authorize`（方案 1：无本地跳板页）。
- **`user-admin` 接真 OIDC**：authorization_code + PKCE S256，经 `user-gateway` 的资源侧验签（JWKS）；移除壳阶段本地放行。
- **`auth-admin` 改走 OIDC**：与 `user-admin` 同构 public + PKCE；废除「首期同域会话 / form-login」临时策略。
- **三个独立 OAuth Client**：`auth-portal-spa`（已有）、`user-admin-spa`、`auth-admin-spa`（后两个新增种子；redirect_uri 精确白名单；TTL/启停/审计按端隔离）。
- **`auth-portal` 不自走 OIDC**：IdP 壳继续以 `AUTH_SSO_SESSION` Cookie 维持 SSO，不为自己发码。
- **唯一登出**：实现 OIDC `/connect/logout`（从二期提前）；RP 登出清本地 AT/RT 后回到 IdP 统一登出，失效 SSO Cookie 与关联 grant；跨端共享同一 IdP 会话的登出语义可预期。
- **种子与文档**：补 `oauth_client` 两行种子；登记 redirect 白名单与信任域名（`docs/test-env.md`）；OIDC 接线偏差从「壳登录」表述中移除。

### Non-goals / 项目边界

- **不**合并三前端为单 SPA / 单壳多模块（三前端边界已裁决保留）。
- **不**做用户主数据、集中式 RBAC、C 端注册/找回密码、短信/邮箱/MFA、社交登录、动态客户端注册、多租户。
- **不**做 Access Token 即时黑名单（jti 黑名单仍留二期）；登出以吊销 RT + SSO 会话 + grant 台账为准，短 TTL AT 过期自然失效。
- **不**让业务系统自建登录/验密；**不**在 `user-admin` / `auth-admin` 保留任何账密表单。
- **不**引入与 `CLAUDE.md` §2 冲突的平行选型；**不**跨系统共享 Entity/Mapper。
- **不**改 `unified-auth-center` / `scaffold-user-center` 既有 proposal/specs/design 定义；本变更只消费其产出（OIDC 端点、`user-api`、JWKS）。

## Capabilities

### New Capabilities

- `rp-oidc-login`: 业务/运营前端作为 OIDC RP 的登录接入——路由守卫跳转 authorize、code+PKCE 换票、AT/RT/ID Token 本地持有与刷新、唯一登录门面约束（禁止本地账密表单）、三个独立 public client 的 redirect 白名单。
- `oidc-single-logout`: 统一登出——`/connect/logout`、SSO Cookie 失效、RT/grant 吊销、RP 本地令牌清理与登出后回跳；跨 RP 共享 IdP 会话时的登出预期。

### Modified Capabilities

（无——`openspec/specs/` 尚无已归档能力；IdP 侧授权/换票/revoke 已由 `unified-auth-center` 的 `auth-oidc-login` 覆盖，本变更不改其需求，只新增 RP 接入与登出能力。）

## Impact

- **前端**：
  - `frontend/user-admin`：删本地登录壳，接 OIDC RP（路由守卫、`src/api` 带 AT、刷新与登出）。
  - `frontend/auth-admin`：删 form-login 与同域会话假设，接 OIDC RP（同构）。
  - `frontend/auth-portal`：保持 IdP 登录门面；配合统一登出入口/回跳（无自用 OIDC client 流程）。
- **后端**：
  - `auth-service`：实现 `/connect/logout` 与 SSO/RT/grant 联动吊销（能力落在既有认证中心，不新开后端系统）。
  - `user-gateway` / 资源侧：确认 JWKS 验签路径对 `user-admin` SPA 的 AT 生效（纯网关鉴权模型不变）。
- **数据**：`auth_db.oauth_client` 增补种子 `user-admin-spa`、`auth-admin-spa`（`INSERT IGNORE`，人工执行；生产前征询）。
- **配置/文档**：redirect 白名单、CORS/信任域名对齐 `docs/test-env.md`；冒烟清单含「唯一登录 / 唯一登出 / 双 RP 换票」。
- **依赖系统边界**：RP 只与 `auth-service` 的 `/oauth2/**`、`/connect/logout` 交互；`user-admin` 业务 API 仍走 `user-gateway`；禁止跨系统直连库表。
- **与 in-progress change 关系**：依赖 `unified-auth-center`（OIDC 端点、Client 运营、Token 管理）与 `scaffold-user-center`（`user-api`、user-admin 壳）已交付能力；不修改二者的 proposal/specs/design。
