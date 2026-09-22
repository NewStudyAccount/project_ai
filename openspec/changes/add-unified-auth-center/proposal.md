# Proposal: add-unified-auth-center

## Why

多系统容器（blog、file 及后续业务系统）需要统一的登录、SSO 与令牌签发能力。若各系统各自实现登录与发票，凭证、会话、OAuth Client、令牌生命周期会随业务扩散，无法支撑独立域名 SSO 与标准 OIDC 对接。  
需要**平台级独立认证中心**（与用户中心拆分）：只做认证与令牌签发，用户主数据归用户中心，细粒度 RBAC 归各业务系统。

## What Changes

- 新增独立系统 `backend/auth`（`auth-service` 为可运行模块）与 **`auth_db`**。
- 接入 **Spring Authorization Server** 协议内核，提供标准 OIDC / OAuth2.1 端点：`/oauth2/authorize`、`/oauth2/token`、`/oauth2/jwks`、`/oauth2/revoke`、`/oauth2/userinfo`、`/.well-known/openid-configuration`。
- 账密登录 + 认证中心自有 SSO 会话；登录时 Feign 调用**用户中心**解析 `username → user_id + status`，本地校验密码凭证。
- 令牌模型：短寿命 **Access Token（JWT/JWKS）** + **Refresh Token 轮转**；`sub` = 用户中心 `sys_user.id`。
- 公开客户端（SPA）走 **PKCE（S256）**；自定义 `OAuth2RefreshTokenGenerator` 允许 `authorization_code` + `public` 签发 RT（路线 1）。
- 自研 **OAuth Client 运营后台**（`oauth_client` 表驱动 `RegisteredClientRepository`）：客户端 CRUD / 启停 / 重置密钥 / 登录审计 / 「踢下线」吊销 grant。
- 两类前端：`frontend/auth-portal`（登录/SSO 页）、`frontend/auth-admin`（客户端管理与认证运营）。
- 凭证仅存认证库 `sys_credential`（首期 PASSWORD，多类型预留）；用户主数据表**不在** `auth_db`。
- **不包含（Non-goals / 项目边界）：**
  - 用户主数据（账号、资料、启停生命周期）→ **用户中心**独立系统
  - 细粒度权限码 / RBAC → 各业务系统
  - 短信/邮箱/MFA、社交登录、动态客户端注册、多租户（预留演进）
  - BFF 会话主路径、全自建协议内核
  - 生产密钥入仓（Nacos / 密钥管理 / Jenkins Credentials）
  - 跨系统共享 Entity（只经 API / OIDC / DTO）

## Capabilities

### New Capabilities

- `auth-oidc-protocol`: 标准 OIDC / OAuth2.1 授权码 + PKCE 流程、令牌签发（AT JWT / RT 轮转 / ID Token）、JWKS 与发现文档、userinfo 声明组装。
- `auth-login-sso`: 账密登录、SSO 会话、登录风控（`login_attempt`）、对用户中心账号解析依赖、凭证初始化约定。
- `auth-client-admin`: OAuth Client 注册与运营（CRUD / 启停 / 密钥重置）、授权台账与「踢下线」、安全审计查询。

### Modified Capabilities

（无。仓库尚无 `openspec/specs/` 正式规格；本变更为全新能力。）

## Impact

- **服务**：新增 `backend/auth` 系统（`auth-common` / `auth-framework` / `auth-service` + 可选网关）；对齐 CLAUDE.md §5.4 模块分层。
- **前端**：新增 `frontend/auth-portal`、`frontend/auth-admin` 两个独立 Vue3 工程。
- **数据**：新增 `auth_db`（`sys_sequence`、`sys_credential`、`oauth_client`、`auth_session`、`auth_grant`、`auth_refresh_token`、`login_attempt`、`auth_audit_log` + SAS 协议存储）；迁移目录 `deploy/db/migration/auth_db/`。
- **跨系统契约**：依赖用户中心 API（`GET /api/v1/users/by-username/{username}` 等），Feign + Fallback；用户中心建号后调用认证中心「初始化凭证」或发事件（最终一致）。业务后端作为 **Resource Server** 本地验 JWT，不共享 Entity。
- **安全**：密码仅 `auth_db.sys_credential`；生产密钥禁止入仓；`redirect_uri` 白名单、强制 PKCE、校验 `state`/`nonce`。
- **基础依赖（对齐 CLAUDE.md §2.1）**：MyBatis-Plus、Spring Data Redis + **Redisson**、Lombok、Spring Boot Actuator（`/actuator/health`）、**MapStruct**（Entity↔DTO/VO）、SpringDoc、Bean Validation、Jackson、SLF4J + Logback、**Micrometer Tracing**（禁止 Sleuth）；需配置组件只在 `auth-framework` 装配一次，`auth-common` 禁止依赖 ORM/Redis/Actuator。
- **日志与追踪（对齐 CLAUDE.md §6.11.1）**：日志模式含 **MDC `traceId`/`spanId`**；网关注入 TraceId，Feign/线程池自动透传；禁止业务手写透传头；敏感信息脱敏；可按 `traceId` 检索全链路。
