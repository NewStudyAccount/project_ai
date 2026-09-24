## Why

当前仓库内的多个后台系统仍缺少统一的登录、令牌签发和 OAuth Client 运营能力。为避免每个系统重复实现账号校验、SSO、JWT 和客户端治理，本变更引入一个纯前后端分离的统一认证中心单体，向各业务系统交付可校验的身份令牌，并提供本系统自持的 Client 运营后台。

## What Changes

- 新增统一认证中心单体：`auth-service`（Spring Authorization Server 内核）+ `auth-common` + `auth-framework`，配套 `frontend/auth-portal` 登录页与 `frontend/auth-admin` 运营后台。
- 交付标准 OIDC/OAuth 能力：账密登录、SSO 会话、authorization_code、PKCE、token、refresh token 轮转、revocation、userinfo、JWKS、发现文档。
- 交付 OAuth Client 注册与运营能力，支持 PUBLIC/CONFIDENTIAL 客户端、精确 redirect_uri 白名单、scope、TTL、启停、负责人/环境登记和密钥重置。
- 交付 Token 管理能力：Refresh Token、SSO 会话、grant/会话索引保存在 Redis；MySQL 保存凭证、Client、授权台账、登录尝试和安全审计，不保存 Token 本体。
- 交付认证侧管理能力：Client CRUD、密钥重置、grant/会话查询与吊销、登录审计、安全审计。
- 交付本系统自持 RBAC 管理能力：菜单、角色、用户-角色、角色-菜单和当前操作员菜单/权限查询。
- 新增 `auth_db` 数据库 SQL（业务表 6 张 + RBAC 表 4 张），SQL 随变更提供并人工执行；主键与审计字段遵循 `CLAUDE.md` §6.4.5。
- 新增错误码登记与接口契约：统一 `{code,msg,data}`、Long `String` 序列化、Bean Validation、排序白名单、`@Idempotent` 与 `@RateLimit`。

### Non-goals / 项目边界

- 不做用户主数据，用户账号、资料和启停生命周期归用户中心。
- 不做集中式或细粒度权限码服务，业务系统自行维护 RBAC；本变更只做认证中心自身的 RBAC 后台。
- 不做 C 端注册、找回密码、短信/邮箱/MFA、社交登录、动态客户端注册、多租户。
- 不做 Access Token 即时黑名单和 OIDC `/connect/logout`（二期预留）。
- 不引入 Cloud 网关、Nacos、MinIO、RocketMQ、Quartz、Sentinel、独立监控面、DB 迁移框架或其他与 `CLAUDE.md` §2 冲突的平行选型。
- 不跨系统共享 Entity/Mapper，跨用户中心只走 `user-api` 的显式 Feign 契约。

## Capabilities

### New Capabilities
- `auth-oidc-login`: 用户登录、SSO 会话、OIDC 发现文档、授权码、PKCE、token、refresh token 轮转、revoke、userinfo、JWKS 与登出预留。
- `auth-token-management`: Redis 中的 Refresh Token、SSO 会话、grant/会话二级索引、重用检测和踢下线，以及 MySQL 中的授权台账。
- `auth-client-operations`: OAuth Client 注册、配置、启停、密钥创建/重置、redirect_uri 白名单、scope 与 TTL 运营管理。
- `auth-credentials`: 以用户中心 `user.id` 为逻辑用户主键的 BCrypt 凭证校验、登录尝试与失败限制。
- `auth-admin-rbac`: 认证中心运营后台自身的菜单/角色/用户-角色/角色-菜单管理和当前操作员权限查询。
- `auth-audit`: 登录尝试、令牌签发/刷新/吊销、Client 更新等安全审计查询，且不记录密码或完整 Token。

### Modified Capabilities

无。

## Impact

- 新增后端：`backend/auth/auth-common`、`backend/auth/auth-framework`、`backend/auth/auth-service`，包名前缀 `com.qjj.auth.*`。
- 新增前端：`frontend/auth-portal`、`frontend/auth-admin`，均按 Vue3 + TypeScript + Pinia + Element Plus 的项目结构实现。
- 新增依赖：Spring Boot、Spring Authorization Server、Spring Security OAuth2 Resource Server、MyBatis-Plus、Redis/Redisson、OpenFeign、springdoc、Micrometer Tracing、MapStruct、MySQL 驱动；具体版本按 `docs/version-baseline.md` 取用。
- 新增数据库：`auth_db`，SQL 输出到 `deploy/db/migration/auth_db/`，由人工执行；生产执行前须按项目红线征询。
- 新增配置：`application.yml`、`application-local.yml`、`application-test.yml`；组件 IP/端口/账密和信任域名登记到 `docs/test-env.md`。
- 新增 API：OIDC `/oauth2/**`、`/.well-known/**` 端点与 `/api/v1` 管理 API；错误码先进入 `docs/error-code-ranges.md`，再落 `AuthErrorCodeEnum`。
- 依赖系统：通过 `user-api` 的 OpenFeign 直连 URL 调用用户中心的 `by-username` 和 `userinfo` 能力，禁止事务内调用。
- 与既有系统边界：认证中心单体通过 Nginx 托管静态并反代 `/api`、`/oauth2`；业务系统独立部署，通过 JWKS 离线验签。
