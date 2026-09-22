# Design: add-unified-auth-center

## Context

- 仓库为多系统容器；blog/file 等需统一登录、SSO 与 JWT 票据。探索结论已确认：**SAS 内核 + 自研 Client 后台**，且与**用户中心**拆分（用户主数据不在本系统）。
- 详见 `proposal.md`（Why / What）与 `docs/template/unified-auth-center-design.md`、`unified-auth-center-database.md`。
- 约束：`CLAUDE.md` §5.4–5.6 模块分层、§6.2/6.5 数据契约、§5.2 双重鉴权、§6.7/6.10 密钥与配置三文件。

## Goals / Non-Goals

**Goals:**

- 标准 OIDC / OAuth2.1 授权码 + PKCE（S256）协议能力与令牌签发（AT JWT / RT 轮转）。
- 账密登录 + 认证中心自有 SSO 会话；登录解析依赖用户中心 API。
- 自研 OAuth Client 运营与认证侧吊销/审计。
- 模块落点对齐 `auth-common`（零配置）/ `auth-framework`（需配置）/ `auth-service`（Entity 与业务）。

**Non-Goals:**

- 用户主数据 CRUD、资料权威、启停生命周期（用户中心）。
- 细粒度 RBAC / 权限码（各业务系统）。
- 短信/邮箱/MFA、社交登录、动态客户端注册、多租户、DPoP、OIDC Logout、Access jti 黑名单（预留）。
- BFF 会话主路径；全自建协议内核。

## Decisions

### D1. 协议内核：Spring Authorization Server

**选择：** SAS 作 OIDC/OAuth2.1 协议内核，存储适配到 `auth_db`。  
**替代：** 全自研授权码/令牌协议（成本高、易出安全缺陷）；Keycloak 等外挂 IdP（运维与定制 Client 后台成本高，且与多系统容器边界不贴合）。  
**理由：** 标准协议兼容各前端；自定义点收敛在 RT 生成器、TokenCustomizer、登录与 Client 仓库。

### D2. 公开客户端 RT（路线 1）

**选择：** 自定义 `OAuth2RefreshTokenGenerator`，允许 `client_auth_method=NONE` + `authorization_code` 签发 RT；强制 PKCE + 轮转 + 重用检测吊销全链。  
**替代：** 不给 SPA 发 RT（用户体验差，易把 AT 长期化）；改 confidential + BFF（否决 BFF 主路径）。  
**理由：** SPA 独立域名 SSO 下需要可续期；安全靠 PKCE + 轮转 + 重用检测补偿。

### D3. Client 定义权威

**选择：** `oauth_client` 表 → `RegisteredClientRepository`；协议状态走 SAS `OAuth2AuthorizationService`（如 `oauth2_authorization`）；运营吊销索引用 `auth_grant` + `auth_refresh_token`。  
**替代：** 内存/配置文件 Client（无法运营）；把协议状态与运营台账混一张表（职责纠缠）。

### D4. 模块落点

| 内容 | 模块 |
|------|------|
| Result/错误码/异常/分页契约、通用枚举/工具 | `auth-common`（禁 MyBatis-Plus/Redis/Entity） |
| MyBatis-Plus、Redis、SAS 存储适配、JWKS/JWT、Long 序列化、ID/审计填充 | `auth-framework` |
| 对用户中心 Feign + Fallback（仅 DTO） | 依赖 `user-api` 类跨系统契约模块 |
| 登录、Client 管理、凭证与令牌运营、Entity | `auth-service`（三份 `application*.yml`） |
| 登录页 / 管理端 | `frontend/auth-portal`、`frontend/auth-admin` |

Entity 仅在 `auth-service`；禁止放入 common/framework；禁止跨系统共享 Entity。

### D5. 对用户中心契约

- Feign：`GET /api/v1/users/by-username/{username}`、`GET /api/v1/users/{id}`、`GET /api/v1/users/{id}/profile`；必须 Fallback；超时建议连接 3s / 读取 10s。
- **禁止事务内 Feign**；可短 TTL 缓存用户 `status`。
- 建号：用户中心成功后调用「初始化凭证」或发事件，最终一致；用户中心**不**回调认证中心验密。

### D6. 令牌与 Claims

| 令牌 | 形态 | 策略 |
|------|------|------|
| Access Token | JWT RS256，JWKS 验签 | TTL 默认 600s（5–15 可配） |
| Refresh Token | 不透明，存哈希 | TTL 默认 7 天；每次刷新轮转 |
| ID Token | JWT | `sub` = 用户中心 `sys_user.id` |

Claims 最小集：`iss, sub, aud, exp, iat, jti, auth_time, preferred_username`。禁止业务权限码；资料 claim 走 `userinfo`。

### D7. 前端与鉴权

- `frontend/auth-portal`：IdP 登录 UI，承载 SSO 会话 Cookie，不作 OAuth Client。
- `frontend/auth-admin`：public + PKCE Client（路线 1）或首期同域会话简化；菜单：客户端列表/详情、密钥重置、令牌与会话（踢下线）、登录/安全审计。
- API 层统一 axios 解包 `{code,msg,data}`；`id` 为 string；路由 meta：`title/icon/hidden/requiresAuth`。
- 管理端授权：首期「已登录即可管」或简单角色；细粒度禁止前端硬编码角色名当唯一鉴权。业务 Resource Server 本地验 JWT（双重鉴权：网关认证 + 服务授权）。

### D8. 配置与密钥

- 可运行模块三份：`application.yml` + `application-dev.yml` + `application-test.yml`；连接信息以 `docs/test-env.md` 为准。
- 密码仅 `sys_credential.secret_ref`（BCrypt/Argon2）；生产密钥禁止入仓；`redirect_uri` 白名单、强制 PKCE、校验 `state`/`nonce`。

### D9. 基础依赖与日志追踪（对齐 CLAUDE.md §2.1 / §6.11.1）

| 能力 | 选型 | 落点 |
|------|------|------|
| ORM | MyBatis-Plus（统一审计填充 / `@TableLogic` / 分页） | `auth-framework` 装配；Entity/Mapper 仅在 `auth-service` |
| 缓存/锁 | Spring Data Redis + **Redisson**（登录限流计数等） | `auth-framework` 统一连接与序列化 |
| 对象映射 | **MapStruct**（Entity↔DTO/VO） | 业务侧转换器；禁止 BeanUtils 做主路径 |
| 样板 / 健康 | Lombok；Actuator `/actuator/health` | 编译期；`auth-service` 必暴露 |
| 链路追踪 | **Micrometer Tracing**（禁止 Sleuth） | `auth-framework` 传播/采样 |
| 日志 | SLF4J + Logback；模式含 `[%X{traceId:-}]` | MDC；禁止业务手写透传头；脱敏见 6.11.1 |

- 版本只在父 POM `dependencyManagement` 维护；`auth-common` 禁止依赖 MyBatis-Plus / Redis / Actuator。

## Risks / Trade-offs

- [公开客户端 RT 被盗] → PKCE + 轮转 + 重用检测吊销全链；短 AT；HTTPS 强制。  
- [用户中心不可用导致无法登录] → 超时/熔断/Fallback；登录失败语义 503；可短 TTL 状态缓存（最终一致）。  
- [SAS 存储与运营台账不一致] → 吊销先走协议层失效再更新台账；对账任务可选。  
- [跨域 Cookie/SSO] → `SameSite` 按 redirect 场景设计；强制 HTTPS；必要时走中间登录页回跳。  
- [管理端权限过粗] → 首期可接受；审计留痕；后续再演进管理端角色。

## Migration Plan

1. 建 `auth_db` 与迁移脚本（`deploy/db/migration/auth_db/`），含 `sys_sequence` 发号。  
2. 搭 `backend/auth` 多模块（common → framework → service）与 SAS 端点。  
3. 对接用户中心 Feign；初始化凭证 API/事件打通建号。  
4. 部署 `frontend/auth-portal`；再上 `frontend/auth-admin`。  
5. 业务系统切换为 Resource Server 验 JWT；Nginx 按域名/前缀分流到认证中心入口。  
**回滚：** 业务可暂回本地登录或旧票据校验；认证库独立，不影响业务库。

## Open Questions

1. 管理端首期是否与登录页同域简化会话，还是立即独立 public + PKCE Client（建议后者，接受首期成本）。  
2. SSO 会话绝对超时与空闲超时具体数值（建议绝对 ≤ 12h，空闲 ≤ 2h，可配）。  
3. 用户状态缓存 TTL（建议 30–60s）与踢下线后的 AT 是否等待自然过期（首期是，jti 黑名单预留）。
