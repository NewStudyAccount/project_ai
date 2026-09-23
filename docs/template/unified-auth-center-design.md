# 统一认证中心 · 完整设计文档

> 状态：**设计已确认**（2026-09-24，逐项确认稿汇总；取代本目录旧版 `unified-auth-center-design.md` / `unified-auth-center-database.md` 的内容）
> 形态：**纯前后端分离的单体服务**（已裁决，不走 Cloud 微服务化）；SAS 协议内核 + 自研 Client 运营后台
> 范围：认证（Authentication）+ 令牌 + OAuth Client 运营 + 本系统自持 RBAC；**不含**用户主数据、**不含**集中式 RBAC
> 配套：`user-center-design.md`（用户中心）、`rbac-design.md`（RBAC 通用 4 表，本库同构一套）、`docs/version-baseline.md`（组件版本基线）
> 约束：`CLAUDE.md`（基础代码契约 §6.4、安全 §6.7、配置 local/test §6.10）；偏差登记见 §9

---

## 1. 目标与边界

### 1.1 目标

在多系统架构下提供**认证与令牌签发中心**：

- 账密登录、SSO、标准 OIDC 发码/换票/刷新/吊销
- 向各独立系统交付可校验身份（JWT Access Token，`sub` = 用户中心 `sys_user.id`，16 位，String）
- OAuth **客户端**注册与运营（自研管理后台）
- 本系统（`auth-admin`）自持 RBAC（表结构见 `rbac-design.md`）

### 1.2 边界（明确不做）

| 不在本中心 | 归属 |
|------------|------|
| 用户主数据（账号、资料、启停生命周期） | 用户中心 |
| 细粒度权限码 / RBAC 服务化 | 各子系统自持（`rbac-design.md`） |
| C 端注册 / 找回密码 | 不做（可二期另开变更） |
| 跨系统共享 Entity | 禁止；只经 API / OIDC / DTO |
| 生产密钥入仓 | Nacos / 密钥管理 / Jenkins Credentials |

### 1.3 与用户中心、业务系统的关系

- **用户中心**：账号权威（`user.id`、`username`、`status`、资料）；登录时本中心调用其 `by-username` 解析账号
- **本中心**：凭证（密码）、会话、令牌、OAuth Client；**不自存用户资料表**
- **业务系统**：独立部署；Resource Server 验 JWT（JWKS 公钥，**不依赖 SAS**）；查资料走用户中心

```text
  sys-a / sys-b（前端+后端+自己的库）      ┌─────────────┐
       │ OIDC 取票/刷新                    │  用户中心    │
       ▼                                  │  user 系统   │
  ┌─────────────────┐  解析账号/状态       │  user_db     │
  │  认证中心（单体） │ ──────────────────► └──────▲──────┘
  │  SAS + Redis     │  userinfo 取资料            │
  │  Token 管理      │ ──────────────────► 查询/同步 │
  │  auth_db         │                            │
  └─────────────────┘ ◄── 业务系统（RS 验签 JWKS）──┘
```

---

## 2. 形态与架构（已裁决：单体）

- **前后端分离单体**：`auth-service` 一个可运行 Spring Boot 应用 + 两个 SPA 前端；Nginx 托管静态并反代 `/api`、`/oauth2`
- **不引入**：网关（Nginx 直反 `auth-service`）、Nacos 注册/配置（纯 `application-local/-test.yml`）
- **对用户中心调用**：复用 `user-api` 契约构件，OpenFeign **直连 URL 模式**（配置 `user-center.base-url`）+ Fallback + 固定超时；禁止事务内调用
- **鉴权单层**：服务内 Spring Security 验签，`X-User-*` 不经网关注入，**以已验签令牌 `sub` 为准**（更严格）
- **CORS** 归 `auth-service` 自配（信任域名登记 `docs/test-env.md`）
- 保留 **Redis/Redisson**（Token 管理权威存储 + `@RateLimit` RRateLimiter + 幂等，见 §5）

---

## 3. 模块划分（已裁决：common + framework + service 三模块）

```
backend/auth/                       # Maven 多模块，包名 com.qjj.auth.*
├── auth-common                     # Result/错误码/异常/分页契约/BaseEnum/常量/工具（零配置纯基础）
├── auth-framework                  # 基础设施装配（见组件表）
└── auth-service                    # 可运行单体：SAS 内核 + 管理 API（三份 yml）
     ↑ 依赖 user-api（跨系统契约构件，来自用户中心）
frontend/auth-portal/               # 登录页（IdP Login，SSO 会话）
frontend/auth-admin/                # Client 运营 + 认证审计管理端
```

| 模块 | 组件（版本一律取 `docs/version-baseline.md`，先改表再改 pom） |
|------|------|
| `auth-common` | Lombok + 注解类（jakarta.validation / jackson-annotations）；⛔ 禁 MP/Redis/数据源/Actuator/MQ/MinIO/JWT 库/Hutool |
| `auth-framework` | starter-web / validation / aop / actuator / data-redis + Redisson 3.52.0 + MyBatis-Plus 3.5.17 + **`spring-boot-starter-oauth2-authorization-server`（SAS 1.5.8，随 Boot BOM）** + `oauth2-resource-server` + OpenFeign + springdoc 2.9.1 + micrometer-tracing-bridge-brave |
| `auth-service` | auth-common/framework + `user-api` + mysql-connector-j + MapStruct；⛔ 无 Nacos/网关/MinIO/RocketMQ |

**包结构**（§5.6.2）：`com.qjj.auth.{module}` → controller / dto / vo / service / bo / converter（MapStruct 唯一落点）/ mapper / entity / feign / config / constants / enums / util。

---

## 4. 协议与令牌

### 4.1 端点（Spring Authorization Server）

| 端点 | 用途 | 放行 |
|------|------|------|
| `GET/POST /oauth2/authorize` | 登录 + 授权 + 发 `code` | 公开 |
| `POST /oauth2/token` | `authorization_code` / `refresh_token` | 公开（限流） |
| `GET /oauth2/jwks` | 资源方验签公钥 | 公开 |
| `POST /oauth2/revoke` | 吊销 refresh | 公开（限流） |
| `GET /oauth2/userinfo` | OIDC 声明（资料来自**用户中心**） | 需 AT |
| `GET /.well-known/openid-configuration` | 发现文档 | 公开 |
| `GET/POST /connect/logout` | OIDC 登出 | 二期 |

### 4.2 令牌模型（已裁决）

| 令牌 | 形态 | 策略 |
|------|------|------|
| Access Token | JWT（**RS256**，Nimbus 单栈） | **默认 10 分钟（5–15 可配）**；`oauth_client.access_token_ttl_sec` 默认 600 |
| Refresh Token | 不透明串 | **7 天**；**每次刷新轮转**；重用检测 → 吊销该 grant 全链 |
| ID Token | JWT | `sub` = 用户中心 `user.id`（16 位 String） |

- **Claims 最小集**：`iss, sub, aud, exp, iat, jti, auth_time, preferred_username`；⛔ 禁业务权限码（各服务本地查 RBAC）
- **公开客户端**：SPA = public + **PKCE（S256）**；自定义 `OAuth2RefreshTokenGenerator`（路线 1：允许 public + authorization_code 签发 RT），强制轮转
- **封号联动**：用户中心改 `status`；即时踢下线走认证侧管理 API（吊销 RT + 会话）

### 4.3 登录时序

```text
SPA                auth-service                      user-service
 │  /authorize      │                                    │
 │─────────────────►│  getUserByUsername（Feign 直连）      │
 │                  │───────────────────────────────────►│
 │                  │◄──── user_id + status ──────────────┤
 │                  │  BCrypt 验 secret_ref（sys_credential）
 │                  │  建 SSO 会话（Redis）· 发 code        │
 │◄──── cb?code ────┤                                    │
 │  /oauth2/token   │                                    │
 │─────────────────►│  AT + RT + ID Token                │
 │  Bearer AT       │                                    │
 │─────────────────►│  业务 RS 本地验 JWT（JWKS）           │
```

`userinfo`：认证中心 Feign 用户中心组装 profile，不自存用户资料表。

### 4.4 SAS 集成点（自研扩展清单）

| 扩展 | 实现 |
|------|------|
| Client 存储 | `oauth_client` 表 → `RegisteredClientRepository` 适配（secret 仅哈希；`reuse_refresh_tokens` 必须 0） |
| 授权/令牌存储 | `OAuth2AuthorizationService` 的 **Redis 适配实现**（见 §5） |
| RT 签发 | 自定义 `OAuth2RefreshTokenGenerator`（路线 1） |
| Claims | `OAuth2TokenCustomizer<JwtEncodingContext>`（最小集） |
| 登录 | `formLogin` 指向 `auth-portal` 登录路由；失败/锁定提示不泄露账号存在性 |
| 密码 | **BCrypt**（`DelegatingPasswordEncoder`，默认 `{bcrypt}` 前缀，留升级 Argon2 余地） |

---

## 5. Token 管理（已裁决：Redis 管 Token，MySQL 管台账）

| 层 | 存什么 | 说明 |
|---|---|---|
| **Redis（Token 权威）** | ① SAS `OAuth2AuthorizationService` Redis 实现（授权码/AT 元数据/授权记录）；② RT：哈希键 + TTL 7 天，**轮转**= 新键 + 旧键「已轮转」标记（保留至原过期，支撑**重用检测 → 吊销 grant 全链**）；③ SSO 会话（TTL 可配）；④ `user → grant/会话` 二级索引键（「踢下线」）；⑤ 限流（RRateLimiter）/ 幂等占位 | 吊销/轮转/踢下线即时生效 |
| **MySQL（凭证/台账/审计）** | `sys_credential`、`oauth_client`、`auth_grant`（运营台账）、`login_attempt`、`auth_audit_log` | Token 本体**不落库**，避免双源 |

- 缓存键命名遵循 §6.9（`auth:{模块}:{业务标识}`），全部带 TTL
- AT 为自包含 JWT，验签离线不受 Redis 影响
- **风险与对策**：[Redis 持久化依赖] Redis 数据丢失 = 全员 RT/会话失效（重新登录），无凭证/台账损失（AT 验签至过期仍有效）→ 开 AOF everysec + RDB，运维保障

---

## 6. 数据库设计（`auth_db`，10 表，utf8mb4 / InnoDB / 禁物理外键）

### 6.1 通用约定

主键 `id` = 16 位发号（`yyyyMMdd` + 8 位日序列，`BIGINT`，出参 String；**发号唯一域 = 本库**）；审计 5 字段全表必备；字符串 `NOT NULL` + 默认值；索引 `uk_/idx_表名_字段`，单表 ≤ 5；禁止 `SELECT *`。**已删除的表**（相对旧稿）：`auth_session`、`auth_refresh_token`、`oauth2_authorization`（Token/会话/协议状态进 Redis，见 §5）。

### 6.2 业务表（6 张）

**`sys_sequence` — 按日发号（基础设施表）**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键 = 业务日数字形态 `yyyyMMdd`（天然唯一，无鸡生蛋） |
| seq_date | DATETIME | N | — | 业务日（`Asia/Shanghai` 日切） |
| current_val | BIGINT | N | 0 | 当日已发到的最大值（段式：每次 +500） |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一**：`uk_sys_sequence_seq_date (seq_date)`；由 `IdGenerator` 直接 SQL 操作

**`sys_credential` — 登录凭证（密码唯一存放处）**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| user_id | BIGINT | N | — | 用户中心 `sys_user.id`（逻辑引用，无 FK） |
| credential_type | VARCHAR(32) | N | — | `PASSWORD` / `SMS` / `EMAIL` / `TOTP`… 首期 `PASSWORD` |
| secret_ref | VARCHAR(255) | N | `''` | **BCrypt 哈希**（`{bcrypt}` 前缀）；⛔ 禁明文 |
| verified | TINYINT | N | 0 | 1 已验证 |
| status | TINYINT | N | 1 | 1 启用 / 0 停用 |
| expires_at | DATETIME | Y | NULL | |
| pwd_update_time | DATETIME | Y | NULL | |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一**：`uk_sys_credential_user_type (user_id, credential_type)`；**索引**：`idx_sys_credential_user_id`

**`oauth_client` — 客户端注册与运营**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| client_id | VARCHAR(64) | N | — | |
| client_secret_hash | VARCHAR(100) | Y | NULL | 机密客户端哈希；公开客户端可空；明文仅创建/重置时一次 |
| client_name | VARCHAR(64) | N | `''` | |
| client_type | VARCHAR(32) | N | `PUBLIC` | `PUBLIC` / `CONFIDENTIAL` |
| client_auth_method | VARCHAR(32) | N | `NONE` | |
| grant_types | VARCHAR(255) | N | — | 如 `authorization_code,refresh_token` |
| redirect_uris | VARCHAR(1024) | N | — | 精确白名单 |
| scopes | VARCHAR(255) | N | `openid,profile` | |
| require_pkce | TINYINT | N | 1 | |
| require_consent | TINYINT | N | 0 | |
| reuse_refresh_tokens | TINYINT | N | 0 | **必须 0** |
| access_token_ttl_sec | INT | N | 600 | AT 默认 10 分钟（5–15 可配） |
| refresh_token_ttl_sec | INT | N | 604800 | RT 7 天 |
| system_code | VARCHAR(32) | N | `''` | 归属系统标识（运营归类用） |
| owner | VARCHAR(64) | N | `''` | 负责人 |
| env | VARCHAR(16) | N | `''` | 登记环境（local/test/prod） |
| enabled | TINYINT | N | 1 | |
| remark | VARCHAR(255) | N | `''` | |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一**：`uk_oauth_client_client_id (client_id)`；**索引**：`idx_oauth_client_enabled`、`idx_oauth_client_system_code`（3/5）

**`auth_grant` — 授权/令牌链运营台账（查询/审计用，权威在 Redis）**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| user_id | BIGINT | N | — | 用户中心 id |
| client_id | VARCHAR(64) | N | — | |
| scopes | VARCHAR(255) | N | `''` | |
| status | TINYINT | N | 1 | 1 活跃 / 0 吊销 |
| revoked_at | DATETIME | Y | NULL | |
| revoke_reason | VARCHAR(64) | N | `''` | |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **索引**：`idx_auth_grant_user_id`、`idx_auth_grant_client_id`、`idx_auth_grant_status`（3/5）

**`login_attempt` — 登录风控**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| username | VARCHAR(64) | N | `''` | 尝试登录名 |
| user_id | BIGINT | Y | NULL | |
| success | TINYINT | N | 0 | |
| fail_reason | VARCHAR(64) | N | `''` | |
| ip | VARCHAR(64) | N | `''` | |
| user_agent | VARCHAR(255) | N | `''` | |
| client_id | VARCHAR(64) | N | `''` | |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **索引**：`idx_login_attempt_username`、`idx_login_attempt_ip`、`idx_login_attempt_create_time`（3/5）

**`auth_audit_log` — 安全审计**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| action | VARCHAR(64) | N | — | `LOGIN` / `TOKEN_ISSUE` / `REFRESH` / `REVOKE` / `CLIENT_UPDATE`… |
| actor_user_id | BIGINT | Y | NULL | |
| target_type | VARCHAR(32) | N | `''` | |
| target_id | VARCHAR(64) | N | `''` | |
| detail | VARCHAR(512) | N | `''` | ⛔ 禁密码/完整令牌 |
| ip | VARCHAR(64) | N | `''` | |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **索引**：`idx_auth_audit_log_action`、`idx_auth_audit_log_create_time`（2/5）

### 6.3 RBAC 表（4 张，已裁决建）

按 **`rbac-design.md` §2** 同构建于本库（`sys_menu` / `sys_role` / `sys_user_role` / `sys_role_menu`，**无 `system_code`**），供 `auth-admin` 自用；本文件不复制字段定义。

### 6.4 逻辑关系

```text
（用户中心）sys_user.id ──（逻辑）──► sys_credential.user_id / auth_grant.user_id / login_attempt.user_id
oauth_client 1 ──── * auth_grant
本库 RBAC：sys_user_role / sys_role_menu / sys_menu 树（rbac-design.md §2.5）
Token/会话/RT/协议状态 ──► Redis（§5）
```

---

## 7. 接口草案（管理 API，`/api/v1`，经服务内鉴权）

| 方法 | 路径 | 用途 |
|------|------|------|
| GET/POST/PUT/DELETE | `/clients/**` | Client CRUD（secret 明文仅创建/重置时一次） |
| POST | `/clients/{id}/secret` | 重置密钥 |
| GET | `/grants` | 授权/令牌链查询（按 user/client/状态） |
| POST | `/grants/{id}/revoke` | 吊销（踢下线：RT + 会话 + 台账） |
| GET | `/login-attempts` | 登录审计查询 |
| GET | `/audit-logs` | 安全审计查询 |
| GET/POST/PUT/DELETE | `/menus/**`、`/roles/**` | RBAC 管理 |
| POST/DELETE | `/users/{id}/roles` | 用户-角色分配/回收 |
| GET | `/me/menus`、`/me/permissions` | 当前操作员菜单树/权限集 |

- 统一 `{code,msg/data}`、`id` String、Bean Validation、排序白名单；关键写 `@Idempotent`；登录/token 端点 `@RateLimit`
- 错误码：先在 `docs/error-code-ranges.md` 登记系统号，再进 `AuthErrorCodeEnum`
- **预留（二期）**：凭证初始化/重置 API（供用户中心建号调用，最终一致编排另开变更）

---

## 8. 前端（两工程，均按 §5.5 结构）

| 工程 | 职责 | 页面 |
|------|------|------|
| `frontend/auth-portal` | 登录页（IdP Login） | 账密登录、失败/锁定提示、（预留）验证码/MFA 入口；**不做**注册/找回；建立/延续 SSO 会话 Cookie；防撞库文案不泄露账号存在性 |
| `frontend/auth-admin` | Client 运营 + 认证审计 | 客户端列表/详情编辑/新建与重置密钥/令牌与会话（踢下线）/登录审计 + **RBAC 管理页** |

- 登录方式：`auth-admin` 首期可与登录页同域会话简化，长期走 public + PKCE（路线 1）；请求只经 `src/api`，`id` 一律 string，统一解包

---

## 9. 关键技术决策与偏差登记（已全部裁决）

| # | 决策 | 说明 / 相对旧稿或全局契约的偏差 |
|---|------|------|
| 1 | **单体形态**：前后端分离单体，无网关、无 Nacos | **偏差**：§5.1 网关链路对本系统豁免（Nginx 直反）；CORS 归 `auth-service` 自配（§5.2 唯一归属豁免）；鉴权单层（以验签 `sub` 为准） |
| 2 | **三模块** `auth-common` / `auth-framework` / `auth-service` | 已裁决（备选单模块否决） |
| 3 | **Redis 管 Token**：SAS 授权/RT/SSO 会话/踢下线索引全在 Redis；MySQL 只留台账 | 已裁决；旧稿 `auth_session`/`auth_refresh_token`/`oauth2_authorization` **三表删除** |
| 4 | **BCrypt**（`DelegatingPasswordEncoder`，默认 `{bcrypt}`） | 已裁决；留升级 Argon2 余地 |
| 5 | **Nimbus 单栈**（SAS 原生 JOSE；JJWT 撤销） | 全局裁决 |
| 6 | **AT 默认 10 分钟（5–15 可配）** + RT 7 天轮转 | 全局裁决（§6.7 已回填），与旧稿一致 |
| 7 | **16 位发号**（`yyyyMMdd` + 8 位日序列），`sub` 同步 16 位 | **偏差**：旧稿 14 位（§6.4.5 裁决） |
| 8 | **配置 local/test** 三份 yml | **偏差**：旧稿 dev/test（§6.10 裁决） |
| 9 | **RBAC 4 表**同构建于 `auth_db`（无 `system_code`） | 已裁决；`rbac-design.md` 为准 |
| 10 | SQL 落 `deploy/db/migration/auth_db/` **人工执行** | 不引 Flyway（§2.2） |
| 11 | `user-api` Feign **直连 URL** 复用 + Fallback + 超时 | 无注册中心下的服务间调用方式 |

---

## 10. 安全约定

- 密码仅 `auth_db.sys_credential.secret_ref`（BCrypt 哈希）；用户中心无凭证字段
- `redirect_uri` 精确白名单；强制 PKCE（S256）；校验 `state` / `nonce`
- RT 不透明、不落库明文（Redis 哈希键）；secret 明文仅创建/重置时返回一次
- 登录失败限制（`login_attempt`）；登录/token/换票端点 `@RateLimit`（RRateLimiter）
- 生产密钥禁止入仓（Nacos/密钥管理/Jenkins Credentials）；连接信息见 `docs/test-env.md`
- 审计禁密码/完整令牌；日志脱敏（§6.11.1）

---

## 11. 部署与迁移

1. 测试环境建库 `auth_db`（utf8mb4），人工执行 `deploy/db/migration/auth_db/` SQL（10 表；生产执行前按 §7 征询）
2. 准备 Redis（AOF everysec + RDB）；部署 `auth-service`（profile=`test`），健康检查 `/actuator/health`
3. Nginx：托管 `auth-portal`/`auth-admin` 静态；反代 `/api`、`/oauth2` → `auth-service`
4. 冒烟：OIDC 发现文档 → 登录 → 发码换票 → 刷新轮转 → revoke/踢下线 → JWKS 验签 → Client CRUD
5. 回滚：下线应用 + 保留库与 Redis 快照（无破坏性数据变更）

---

## 12. 非目标与演进

**首期不做**：短信/邮箱/MFA、社交登录、动态客户端注册、多租户、Access 即时吊销（jti 黑名单）、OIDC Logout（`/connect/logout` 二期）。
**预留**：多类型凭证（`credential_type` 已留）、DPoP、凭证初始化 API（用户中心建号联动，最终一致）、jti 黑名单、Argon2 升级。

---

## 13. 相关文档

| 文档 | 内容 |
|------|------|
| `CLAUDE.md` | 项目全局契约（偏差登记见 §9） |
| `user-center-design.md` | 用户中心（账号权威，被本中心调用） |
| `rbac-design.md` | RBAC 通用 4 表与模型约定 |
| `docs/version-baseline.md` | 组件版本基线（唯一取用处） |
| `docs/test-env.md` | 环境 IP/端口/账密/信任域名 |
