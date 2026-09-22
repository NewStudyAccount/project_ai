# 统一认证中心 · 设计说明

> 状态：探索结论已确认（SAS 内核 + 自研客户端后台；**与用户中心拆分**）  
> 范围：认证（Authentication）+ 令牌 + OAuth Client 运营；**不含**用户主数据、**不含**细粒度 RBAC  
> 配套：`docs/template/user-center-design.md`、`unified-auth-center-database.md`、`user-center-database.md`  
> 约束：`CLAUDE.md`（多系统容器、common/framework 分层、Entity 归属、密钥分级、配置三文件）

---

## 1. 目标与边界

### 1.1 目标

在多系统架构下提供**认证与令牌签发中心**：

- 账密登录、SSO、标准 OIDC 发码/换票/刷新/吊销
- 向各独立系统交付可校验身份（JWT Access Token，`sub` = 用户中心 `user.id`）
- OAuth **客户端**注册与运营（自研管理后台）

### 1.2 边界（明确不做）

| 不在本中心 | 归属 |
|------------|------|
| **用户主数据**（账号、资料、启停生命周期） | **用户中心**（独立系统） |
| 细粒度权限码 / RBAC | 各业务系统 |
| 业务扩展资料 | 各业务系统或用户中心 |
| 跨系统共享 Entity | 禁止；只经 API / OIDC / DTO |
| 生产密钥入仓 | Nacos / 密钥管理 / Jenkins Credentials |

### 1.3 与用户中心、业务系统的关系

- **用户中心**：账号权威（`user.id`、`username`、`status`、资料）；提供用户查询/管理 API。  
- **认证中心**：凭证（密码）、会话、令牌、OAuth Client；**登录时调用用户中心**解析账号。  
- **业务系统**：独立部署；Resource Server 验 JWT；用 `sub` 做投影；查资料走用户中心。

```
  sys-a / sys-b / sys-c          ┌─────────────┐
  （前端+后端+自己的库）            │  用户中心    │
       │                         │  user 系统   │
       │ OIDC 取票/刷新            │  user_db    │
       ▼                         └──────▲──────┘
  ┌─────────────────┐   解析账号/状态    │
  │  认证中心        │ ─────────────────►│
  │  SAS + Client   │   userinfo 取资料  │
  │  后台           │ ─────────────────►│
  │  auth_db        │                   │
  └─────────────────┘                   │
       ▲                                │ 查询/同步
       └────────── 业务系统 ─────────────┘
```

---

## 2. 已确认决策

| 轴 | 决策 |
|----|------|
| 职责 | **仅 Authentication**（凭证 + 会话 + 令牌 + Client）；**不含**用户主数据、**不含** RBAC |
| 用户数据 | **独立用户中心系统**；用户表**不在**认证库 |
| 协议 | 标准 **OIDC / OAuth2.1 授权码**；前端 **公开客户端 + PKCE（S256）** |
| SSO | 支持**独立域名**；认证中心自有 SSO 会话；各系统 code 换票 |
| IdP 实现 | **Spring Authorization Server** 作协议内核 |
| 令牌 | 短 **Access Token（JWT/JWKS）** + **Refresh Token 轮转** |
| RT 对公开客户端 | **路线 1：自定义 `OAuth2RefreshTokenGenerator`** 允许 public + authorization_code 签发 RT |
| 客户端管理 | **自研后台**；`oauth_client` 表 → `RegisteredClientRepository` |
| 业务后端 | **Resource Server**；**不是** `oauth2Login` 会话型 Client |
| 凭证 | 首期账密；`sys_credential` 多类型预留；**仅存认证库** |
| `sub` | 用户中心 `sys_user.id`（14 位） |
| 不采用 | BFF 会话主路径；全自建协议内核；客户端后台兼做用户 API |

---

## 3. 架构

### 3.1 逻辑架构

```text
┌────────────────────────────────────────────────────────────┐
│  统一认证中心 backend/auth（独立系统）                        │
│                                                            │
│  ┌──────────────────────┐    ┌──────────────────────────┐  │
│  │ 协议内核 SAS          │    │ 运营应用（自研）           │  │
│  │ /oauth2/authorize    │    │ Client CRUD / 启停 / 重置  │  │
│  │ /oauth2/token        │◄──►│ 登录与令牌审计             │  │
│  │ /oauth2/jwks         │    │ 「踢下线」（吊销 grant）    │  │
│  │ /oauth2/revoke       │    └──────────────────────────┘  │
│  │ /oauth2/userinfo     │                                 │
│  │ /.well-known/…       │    ┌──────────────────────────┐  │
│  │ 自定义 RT 生成器      │    │ 用户中心客户端（Feign）     │  │
│  │ TokenCustomizer      │───►│ getUserByUsername / status │  │
│  └──────────────────────┘    │ getUserProfile（userinfo） │  │
│                              └──────────────────────────┘  │
│  auth_db：credential / session / grant / RT / client…       │
└────────────────────────────────────────────────────────────┘
         ▲ 标准 OIDC
    各系统 SPA（public + PKCE）     业务后端（RS）
                    │
                    ▼ 查询/投影
              用户中心 user_db
```

### 3.2 模块落点（对齐 CLAUDE.md §5.4）

| 内容 | 模块 |
|------|------|
| 契约、错误码、通用枚举/工具 | `auth-common`（零配置） |
| MyBatis-Plus、Redis、SAS 存储适配、JWKS/JWT、Long 序列化 | `auth-framework` |
| 对用户中心 Feign + Fallback | `user-api`（跨系统契约）依赖；实现仅 DTO |
| Client 管理、凭证与令牌管理 | `auth-service`（可运行，三份 `application*.yml`） |
| 登录页前端 | `frontend/auth-portal`（见 §5.1） |
| 客户端管理前端 | `frontend/auth-admin`（见 §5.2） |
| Entity | 仅在 `auth-service`（本库表）；**无**用户主数据 Entity |

### 3.3 对用户中心接口（认证侧依赖）

| 能力 | 用途 | 失败策略 |
|------|------|----------|
| `GET /api/v1/users/by-username/{username}` | 登录解析 `user_id` / `status` | 401/503；可短 TTL 缓存状态 |
| `GET /api/v1/users/{id}` | `userinfo`、审计展示 | 按需 |
| `GET /api/v1/users/{id}/profile` | OIDC profile claims | 按需 |
| （可选）`POST /api/v1/users` 等管理 API | 由**用户中心管理端**调用，非认证中心职责 | — |

- 认证中心 **Feign** 调用，必须有 **Fallback**；禁止 RestTemplate。  
- 用户中心 **不**调用认证中心验证密码。  
- 建号：用户中心创建账号成功后，调用认证中心「初始化凭证」或发事件；最终一致。

---

## 4. 协议与令牌

### 4.1 端点（Spring Authorization Server）

| 端点 | 用途 |
|------|------|
| `GET/POST /oauth2/authorize` | 登录 + 授权 + 发 `code` |
| `POST /oauth2/token` | `authorization_code` / `refresh_token` |
| `GET /oauth2/jwks` | 资源方验签 |
| `POST /oauth2/revoke` | 吊销 refresh |
| `GET /oauth2/userinfo` | OIDC 声明（资料来自**用户中心**） |
| `GET /.well-known/openid-configuration` | 发现文档 |
| `GET/POST /connect/logout` | 可二期 |

### 4.2 令牌模型

| 令牌 | 形态 | 策略 |
|------|------|------|
| Access Token | JWT（RS256） | 建议 **10 分钟**（5–15 可配） |
| Refresh Token | 不透明串 | 建议 **7 天**；**每次刷新轮转** |
| ID Token | JWT | `sub` = 用户中心 `user.id` |

**Claims 最小集：** `iss, sub, aud, exp, iat, jti, auth_time, preferred_username`。  
**禁止**业务权限码；资料性 claim 仅在 `userinfo` 或登录后同步时获取。

**轮转 / 吊销：** 同前设计；封号在**用户中心**改 `status`，认证侧按策略吊销该用户 RT（管理端「踢下线」）。

### 4.3 公开客户端与 RT（路线 1）

SAS 默认对 `none` + `authorization_code` **不发 RT**；本设计自定义 `OAuth2RefreshTokenGenerator` 允许签发，并强制 PKCE + 轮转（详见决策表）。

### 4.4 登录时序（依赖用户中心）

```text
SPA                auth                         user
 │  /authorize      │                            │
 │─────────────────►│  getUserByUsername         │
 │                  │───────────────────────────►│
 │                  │◄─ user_id + status ────────┤
 │                  │  验 password（本地 credential）
 │                  │  建 SSO 会话 · 发 code      │
 │◄── cb?code ──────┤                            │
 │  /oauth2/token   │                            │
 │─────────────────►│  AT + RT + ID Token        │
 │  Bearer AT       │                            │
 │─────────────────►│  业务 RS 本地验 JWT         │
```

`userinfo`：认证中心 Feign 用户中心组装 profile，不自存用户资料表。

---

## 5. 前端设计

认证中心涉及**两类**前端，均在 `frontend/` 多系统容器下独立工程，技术栈对齐 `CLAUDE.md` §2 / §5.5  
（Vue3 + Vue Router + TypeScript + Pinia + Element Plus，`<script setup lang="ts">`）。

| 工程 | 域名/路径（示例） | 角色 | OIDC |
|------|-------------------|------|------|
| `frontend/auth-portal`（或 `auth-login`） | `auth.example.com` | **登录/SSO 页**（IdP 用户界面） | 不作为 OAuth Client；承载认证中心会话 |
| `frontend/auth-admin` | `auth-admin.example.com` 或 `auth.example.com/admin` | **客户端管理端** + 认证运营（吊销/审计） | 公开客户端 + PKCE（路线 1） |

### 5.1 登录页（IdP Login）

- **归属**：认证中心域；建立/延续 **SSO 会话 Cookie**（`auth` 域）。
- **形态**：Vue SPA 或服务端登录页均可；推荐 Vue 登录应用，便于主题与后续 MFA。
- **职责**：账密登录、失败提示与锁定提示、（预留）验证码/MFA 入口；**不做**注册、找回密码（可二期，且属用户中心/认证策略）。
- **与 SAS**：`formLogin` 指向本登录路由；登录成功回 `/oauth2/authorize` 继续发码。
- **安全**：防撞库文案不泄露账号是否存在；强制 HTTPS；`SameSite` Cookie 策略按跨站 redirect 设计。

**登录页信息架构（首期）：**

```text
┌─────────────────────────────────┐
│  统一认证                         │
│  [用户名]  [密码]  [登录]         │
│  错误提示 / 锁定提示              │
└─────────────────────────────────┘
```

### 5.2 客户端管理端（auth-admin）

- **角色**：OAuth **Client** 运营 + 认证侧审计/吊销；**不提供**业务用户管理（用户在用户中心前端）。
- **登录**：自身为 **public + PKCE** Client（路线 1，可拿 AT/RT），或首期与登录页同域会话简化（不推荐长期混用）。
- **页面：**

| 菜单 | 功能 |
|------|------|
| 客户端列表 | 搜索、启停、查看详情 |
| 客户端详情/编辑 | `client_id`、redirect_uris、grant/scope、TTL、PKCE、负责人 |
| 新建/重置密钥 | 机密客户端 secret **仅创建/重置时明文一次** |
| 令牌与会话 | 按用户/客户端查看 grant，「踢下线」吊销 RT |
| 登录审计 | `login_attempt` / `auth_audit_log` 查询 |

- **路由 meta**：`title` / `icon` / `hidden` / `requiresAuth`（`CLAUDE.md` §5.5）。
- **API 层**：`src/api` 统一 axios；拦截器解包 `{code,msg,data}`；`id` 一律 `string`；401 跳转登录或续期。
- **权限**：管理端首期「已登录即可管」或简单角色；细粒度以后再定，禁止前端硬编码业务角色名当唯一鉴权。

### 5.3 目录结构（每个管理/登录工程）

```text
frontend/auth-admin/   # 或 auth-portal
├── package.json
└── src/
    ├── api/          # oauth-client、audit、auth（登录/续期）
    ├── views/        # clients/、audit/、session/、login/…
    ├── components/   # layout / common
    ├── stores/       # user.ts、token.ts（Pinia）
    ├── router/
    ├── types/
    ├── utils/        # pkce、token 存取
    ├── styles/
    └── locales/
```

### 5.4 与后端边界

| 前端 | 调用 |
|------|------|
| 登录页 | 认证中心登录 API / SAS 登录流程；**不**直连用户中心写接口 |
| auth-admin | `auth-service` 管理 API（Client CRUD、吊销、审计） |
| 业务 SPA | 标准 OIDC（authorize/token）+ 业务 API；见 §4.4 |

---

## 6. 客户端管理后台（自研，产品边界）

职责仅限 **OAuth Client** 与 **认证侧运营**（令牌吊销、登录审计），**不提供**业务用户查询 API。  
界面实现见 **§5.2**；用户管理 UI / 用户 API → **用户中心系统**（其前端见 `user-center-design.md`）。

与 SAS 咬合方式不变：`oauth_client` 表驱动 `RegisteredClientRepository`。

---

## 7. 安全约定

- 密码仅 `auth_db.sys_credential`；用户中心**无** `password_hash`。
- 生产密钥禁止入仓；连接信息见 `docs/test-env.md`。
- 配置三文件：`application.yml` / `application-dev.yml` / `application-test.yml`。
- 对用户中心的调用：超时、熔断/降级策略明确；禁止在事务内 Feign。
- `redirect_uri` 白名单；强制 PKCE；校验 `state` / `nonce`。

---

## 8. 非目标与演进

**首期不做：** 短信/邮箱/MFA、社交登录、动态客户端注册、多租户、RBAC、Access 即时吊销（jti）。

**预留：** 多类型凭证、DPoP、OIDC Logout、凭证初始化 API（供用户中心建号）、`jti` 黑名单。

---

## 9. 相关文档

| 文档 | 内容 |
|------|------|
| `CLAUDE.md` | 项目全局契约 |
| `docs/test-env.md` | 环境 IP/端口/账密 |
| `docs/template/user-center-design.md` | 用户中心设计 |
| `docs/template/unified-auth-center-database.md` | 认证库表 |
| `docs/template/user-center-database.md` | 用户库表 |
