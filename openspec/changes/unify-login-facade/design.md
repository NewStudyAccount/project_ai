## Context

认证中心（`auth-service` + `auth-portal`）已具备 OIDC 发码/换票/刷新/revoke 与 `AUTH_SSO_SESSION`；用户中心已有 `user-admin` 壳与 `user-gateway` JWKS 验签。三前端边界保留，但登录面分裂：`auth-portal` 账密表单、`auth-admin` 重复表单（同域会话临时策略）、`user-admin` 本地放行。Cookie 未设 `Domain`，跨子域不共享。详见 `proposal.md`（Why / What Changes）。

约束：`CLAUDE.md` §5.2 纯网关鉴权（业务服务不验用户登录）、§5.5 前端结构、§6.7 Token/密钥、§6.10 三份 yml；组件接入以 `docs/test-env.md` 为准；不修改 `unified-auth-center` / `scaffold-user-center` 既有制品定义。

## Goals / Non-Goals

**Goals:**

- 登录面收敛：唯一账密表单在 `auth-portal`；两 RP 零表单、路由守卫直跳 authorize（已裁决方案 1）。
- `user-admin` / `auth-admin` 同构 OIDC RP：public + PKCE S256，独立 client，AT 资源侧验签。
- 统一登出：`/connect/logout` + SSO/RT/grant 联动 + RP 本地清理；`auth-portal` 不自走 OIDC。
- 三个独立 `oauth_client` 种子与 redirect 白名单可运营（启停、审计按端隔离）。

**Non-Goals:**

- 不合并前端工程；不为 portal 增加 OIDC client 流程。
- 不做 AT jti 黑名单、DPoP、MFA、注册/找回、动态客户端注册。
- 不改 IdP 授权/换票/revoke 的既有需求语义（由 `unified-auth-center` 交付）；不改用户主数据与集中 RBAC。
- 不引入新中间件/平行框架；不在业务服务内做用户鉴权。

## Decisions

### 1. 唯一登录门面 = `auth-portal` IdP 壳；RP 零表单、方案 1 直跳 authorize

- **选择**：删除 `user-admin` / `auth-admin` 的 `Login.vue` 与 `POST /login`；路由守卫在无有效本地会话时 `window.location`（或 `location.assign`）跳转  
  `GET {issuer}/oauth2/authorize?response_type=code&client_id=...&redirect_uri=...&scope=openid profile&state=...&nonce=...&code_challenge=...&code_challenge_method=S256`。
- **理由**：已裁决方案 1；避免第二套错误落地页与表单；登录 UX 只在 IdP 一处。
- **备选否决**：方案 2 薄跳板页——多一次无表单 UI，state/错误可用 `sessionStorage` + authorize 查询参数回传，不值得多一个路由。

### 2. RP 令牌模型：SPA 本地持有 AT/RT，刷新走 `/oauth2/token`

- AT：内存或 `sessionStorage`（推荐内存 + 刷新滑动）；请求 `Authorization: Bearer`。
- RT：`sessionStorage`（或 `localStorage` + 可配；默认 session 降低共享机器残留）；刷新**轮转**以 IdP 行为为准。
- ID Token：仅用于展示 `preferred_username` 等 claims，不作 API 凭证。
- **备选否决**：BFF/Cookie 网关代持令牌——与「无网关的 auth 单体 + user 微服务」现状不匹配，且引入新的会话亲和问题。

### 3. 三个独立 PUBLIC client（PKCE S256），portal 不注册自用 client 流程

| client_id | 给谁 | redirect_uri（精确白名单，示例 local） | 用途 |
|-----------|------|----------------------------------------|------|
| `auth-portal-spa` | portal（已有，维持） | `http://127.0.0.1:5174/callback` 等 | IdP 自身/兼容既有种子；**登录会话仍靠 Cookie，不强制 portal 走 code 流** |
| `user-admin-spa` | 用户中心管理端 | `http://127.0.0.1:5173/callback`、生产域名 `/callback` | RP |
| `auth-admin-spa` | 认证运营后台 | `http://127.0.0.1:5175/callback`、生产域名 `/callback` | RP |

- grant_types：`authorization_code,refresh_token`；scopes：`openid,profile`；`require_pkce=1`；TTL 与全局默认一致（AT 600s / RT 604800s）。
- **理由**：已裁决三端独立——redirect、TTL、启停、审计按端隔离。
- **portal**：登录成功只建/续 `AUTH_SSO_SESSION`；不发起面向自己的 authorize。既有 `auth-portal-spa` 保留以便冒烟与后续若 portal 内嵌需票场景，但**不作为「第四张登录页」**。

### 4. 方案 1 下的回调路径

- 两 RP 增加前端路由 `GET /callback`（**不是登录页**）：校验 `state`、取 `code`、`POST /oauth2/token`（带 `code_verifier`）、写入令牌后 `replace` 回 `sessionStorage` 中保存的 `returnTo`（默认 `/`）。
- 失败（用户拒绝、state 不符）：提示并重新跳 authorize；**禁止**渲染账密表单。

### 5. 唯一登出：实现 `/connect/logout`，RP 先清本地再回 IdP

时序：

```text
任一 RP 点「退出」
  → 清本地 AT/RT/ID Token
  → 跳转 GET/POST /connect/logout
       (可选 id_token_hint / post_logout_redirect_uri / state)
  → auth-service：使 AUTH_SSO_SESSION 失效（Redis 删会话 + Cookie 过期）
                 并按 user/grant 索引吊销关联 RT 与 grant 台账（与既有踢下线同源）
  → 302 post_logout_redirect_uri（精确白名单，登记在 client 或全局允许列表）
  → 同浏览器打开另一 RP：无 SSO 会话 → 再次进 auth-portal 登录
```

- `post_logout_redirect_uri`：各 RP 登记自己的 `/logged-out` 或 `/`；非法 URI 拒绝。
- Cookie：登出时 `Max-Age=0` + 与登录时相同 Path；**不**把 Cookie 范围扩到跨子域 Domain（避免扩大 CSRF 面）——统一登出走 IdP 端点，而不是共享 Cookie 域。
- **备选否决**：`Domain=.example.local` 共享 Cookie 免登 admin——已裁决 admin 走 OIDC；且扩大 Cookie 作用域与 CSRF 风险。

### 6. 鉴权与资源侧

- `user-admin` API：仍 `浏览器 → Nginx → user-gateway → user-service`；网关验 JWT（JWKS，`unified-auth-center` 已有配置项）并注入 `X-User-*`；业务服务不验登录。
- `auth-admin` API：`auth-service` 自管验签（系统例外，`CLAUDE.md` §5.2）；管理写接口沿用本系统 RBAC + `@Idempotent` + 审计。
- 前端菜单/按钮权限仍各自 `user-rbac` / `auth-admin-rbac` 的 `/me/*`，**不**塞进 AT claims。

### 7. 配置与环境

- RP 需配置：`issuer`、`client_id`、`redirect_uri`、`post_logout_redirect_uri`、`scope`；local/test 走 `docs/test-env.md` 对齐的域名/端口（5173/5174/5175、9080）。
- CORS / 信任域名：authorize/token/logout 由 `auth-service` 既有 `allowed-origins` 覆盖；新增 callback 源一并登记 `docs/test-env.md`。
- 不新增 yml 契约形态；后端配置仍三份文件。

## Risks / Trade-offs

- [方案 1 无落地页，OIDC 失败体验生硬] → `sessionStorage` 存 `returnTo` + 失败原因 query；文案指向 IdP；冒烟覆盖拒绝授权。
- [AT 短 TTL + 无 jti 黑名单，登出后旧 AT 仍可用至过期] → 接受（既有决策）；RT/会话/grant 即时吊销；敏感场景依赖踢下线 API。
- [SPA 存 RT 有 XSS 窃取面] → 禁 `v-html`、CSP 可后续加固；RT 轮转 + 重用检测全链吊销；不把 RT 放 Cookie 以免 CSRF。
- [portal 与 RP 登出不同步（仅清 RP 本地）] → 强制「退出」必须经 `/connect/logout`；UI 不提供「仅本地退出」主按钮。
- [三 client 种子手工执行漂移] → `INSERT IGNORE` 种子入仓 + `docs/test-env.md` 登记 redirect；生产执行前征询。
- [依赖两个 in-progress change 的端点契约] → 实现前对照 `unified-auth-center` 任务冒烟清单；缺口只在本变更 tasks 内补齐，不改对方 specs。

## Migration Plan

1. 种子：向 `auth_db.oauth_client` 插入 `user-admin-spa`、`auth-admin-spa`（及如需的 logout 白名单字段/登记）；人工执行，生产前征询。
2. `auth-service`：实现 `/connect/logout` 与会话/RT/grant 联动；补 Client 管理对 `post_logout_redirect_uri` 的登记（若表无字段则以配置白名单过渡，并在 tasks 标注）。
3. `user-admin`：删登录壳与本地放行；接 OIDC RP + `/callback` + 登出。
4. `auth-admin`：删 form-login；接 OIDC RP + `/callback` + 登出。
5. `auth-portal`：登出入口/回跳对齐；确认无自用 code 流。
6. 文档：`docs/test-env.md` 登记 redirect/信任域名；冒烟清单更新。
7. 冒烟：portal 登录 → user-admin 跳转免登（有 SSO）/ 露表单（无 SSO）→ code 换票调 API → auth-admin 同理 → 任一端登出 → 另一端需重新登录。
8. 回滚：前端回退上一构建；`/connect/logout` 无破坏性数据变更；client 种子可 `enabled=0` 停用。

## Open Questions

- `oauth_client` 若需持久化 `post_logout_redirect_uri` 列：随本变更附 `ALTER`/建列 SQL（人工执行），或先用 `auth-service` 配置白名单——实现时按现有表结构择一，**不**改 `unified-auth-center` 已归档口径的表意。
- 本地开发多端口（5173/5175）下 SameSite 策略以 `Lax` + 顶层跳转 authorize/logout 为准；若遇浏览器限制再评估 `None; Secure`（需 HTTPS）。
