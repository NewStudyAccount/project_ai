# Proposal: add-unified-auth-center

## Why

本仓库要在同一容器内交付多个相互独立的后台系统，用户为同一批企业内部人员。  
若无统一身份与认证，会重复建账号、无法 SSO、改密/停用无法一次生效；若 RBAC 与用户副本散落各系统，又会出现双权威与串权。  
需在脚手架落地前固定：**统一认证中心（只做认证 + SSO）**、**每系统独立网关与独立库**、**权限各系统自管**、**用户权威只在认证库、子系统仅引用/轻量投影**，以及认证中心采用**单体部署、自研轻量协议**（非外购 IdP、非微服务化认证）。

## What Changes

- 引入**认证中心服务**（独立部署）：登录、登出、SSO 会话、JWT 签发/刷新/吊销；**Nginx 直反代，不经过网关**。
- 认证中心采用**单体**（一个 Spring Boot 进程：Credential / SsoSession / Token / UserQuery）；**自研轻量认证**（Spring Security + JWT/BCrypt 库 + Redis 自管 sid/refresh），不采用 Keycloak 等外购 IdP，本期不做 OAuth2/OIDC 全量。
- 明确**每业务系统一网关、一套库、独立发布**；网关只验 JWT，不做业务 RBAC。
- 落地**各系统本地 RBAC**：角色/权限/用户-角色/角色-权限均在业务库；鉴权裁决不依赖用户投影。
- 用户身份**唯一权威在认证库**（含凭证）；子系统**不存完整账号**，配权限只存 `user_id`，并可选 `sys_user_ref` 轻量投影（无密码）。
- 投影同步走**登录 upsert + 赋权快照 + 列表 batch 补洞**；暂不引入 MQ/全量复制。
- 项目基础目录：`backend/auth` + `backend/<system>`、`frontend/auth-portal` + `frontend/<system>`；common 契约与 14 位 ID 发号。
- 补齐**统一认证前端 `auth-portal`**：账密登录页、SSO 回跳/换码、令牌与 sid 会话交互；仅有 `auth-service` 而无入口 UI 视为不完整。
- **不包含**：认证中心网关、认证微服务拆分（user/sso/token 独立进程）、跨系统共享业务库、子系统用户账号副本、外购 IdP（Keycloak/CAS 等）、本期 OAuth2/OIDC 全量协议、MQ 用户变更广播、完整账号管理后台（可后置）。

## Capabilities

### New Capabilities

- `unified-auth`: 认证中心——单体自研轻量认证；身份权威、凭证校验、SSO 会话、JWT 生命周期、用户查询 API；认证入口不经网关。
- `auth-portal-ui`: 统一认证前端——登录/SSO 入口 UI；账密登录、会话与令牌存储、SSO authorize 回跳与 code 换令牌；不含业务系统页面。
- `rbac-authorization`: 各系统本地 RBAC——角色/权限模型、用户-角色关联（仅 `user_id`）、按系统鉴权。
- `user-identity-projection`: 子系统用户投影——`sys_user_ref` 只读读模型、同步触发与补洞、展示与授权边界。

### Modified Capabilities

（无——`openspec/specs/` 当前为空。）

## Impact

- **服务**：新增单体 `auth-service`（可选 `auth-api` 仅作 Feign 契约 jar）；各系统 `*-gateway` + 业务服务 + 本地 RBAC 表；`common` 落 Result/审计/ID/JWT 验签组件。
- **数据**：`auth_db`（用户/凭证/SSO/序列）；各 `*_db`（角色/权限/关联/业务/可选 `sys_user_ref`）。
- **API**：认证（登录/SSO/刷新/登出/用户查询）；各系统角色权限管理与鉴权；批量查人接口供选人/补洞。
- **前端**：**必须有 `frontend/auth-portal`（Vue3 + Element Plus）**——统一登录/SSO 入口；各系统按权限码控菜单按钮；选人组件调认证中心。
- **运维**：Nginx 静态托管 auth-portal；`/auth`→auth-service，按域名/前缀→各系统网关；JWT 密钥走配置中心；Redis 存 SSO 会话/Refresh/黑名单。
