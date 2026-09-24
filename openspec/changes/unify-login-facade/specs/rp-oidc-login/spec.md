## Purpose

约束业务与运营前端作为 OIDC RP 的登录接入方式：在唯一登录门面下完成授权码 + PKCE 登录与令牌持有，禁止各 RP 自建账密表单。

## ADDED Requirements

### Requirement: 唯一登录门面

系统 SHALL 仅在 `auth-portal` 提供账密登录表单；`user-admin` 与 `auth-admin` MUST NOT 提供账密登录表单或调用账密登录接口作为主登录路径。

#### Scenario: RP 未登录访问受保护路由

- **WHEN** 用户在无有效本地令牌且无 IdP SSO 会话时访问 `user-admin` 或 `auth-admin` 的受保护路由
- **THEN** 前端跳转到认证中心 `/oauth2/authorize`（public + PKCE S256），本地不渲染账密表单

#### Scenario: RP 代码不包含本地登录 API

- **WHEN** 检查 `user-admin` 与 `auth-admin` 的前端登录路径
- **THEN** 不存在向业务后端或认证中心提交 username/password 的登录表单组件作为主入口

### Requirement: RP 使用独立 OAuth Client 完成授权码登录

每个 RP SHALL 使用独立的 PUBLIC client（`user-admin-spa`、`auth-admin-spa`），以 authorization_code + PKCE S256 完成登录；`redirect_uri` MUST 精确命中该 client 白名单。

#### Scenario: 用户中心管理端完成换票

- **WHEN** `user-admin` 从 `redirect_uri` 回调中收到 `code` 与 `state`，且 `state` 与本地记录一致
- **THEN** 使用 `code_verifier` 向 `/oauth2/token` 换取 Access Token、Refresh Token 与 ID Token，并进入原目标路由

#### Scenario: 认证运营后台使用独立 client

- **WHEN** `auth-admin` 发起授权
- **THEN** 使用 `auth-admin-spa` 与自身 `redirect_uri` 白名单，不与 `user-admin-spa` 混用 client_id

#### Scenario: state 校验失败

- **WHEN** 回调 `state` 与本地保存值不一致
- **THEN** 拒绝使用该 `code`，清理本次授权临时状态，并提示后重新发起授权

### Requirement: RP 持有并刷新令牌

RP SHALL 在本地持有 Access Token 并对业务 API 使用 `Authorization: Bearer`；Access Token 过期时 SHALL 使用 Refresh Token 经 `/oauth2/token` 刷新，且 MUST 将业务权限码排除在令牌之外（权限走各系统 RBAC）。

#### Scenario: 带票调用业务 API

- **WHEN** RP 持有未过期 Access Token 调用受保护 API
- **THEN** 请求携带 Bearer 令牌并通过资源侧 JWKS 验签

#### Scenario: 刷新后旧 Refresh Token 不可用

- **WHEN** RP 成功刷新令牌
- **THEN** 保存新 Access Token 与新 Refresh Token，旧 Refresh Token 不再用于后续刷新

### Requirement: portal 不自走 OIDC 登录流

`auth-portal` SHALL 以 IdP 身份建立/延续 SSO 会话，MUST NOT 作为 RP 通过 `/oauth2/authorize` 为自己获取 Access Token 以完成「登录」。

#### Scenario: 门户账密登录

- **WHEN** 用户在 `auth-portal` 提交正确账密
- **THEN** 由认证中心建立 SSO 会话（Cookie），并按 `return_url` 规则回跳或停留在登录成功态，不要求 portal 自身持有 AT 才能进入

### Requirement: 三个 client 可独立运营

系统 SHALL 为 `user-admin-spa` 与 `auth-admin-spa` 提供可登记的 client 配置（含精确 redirect_uri 白名单、启停、TTL），使停用某一 RP 不影响其他 RP 登录。

#### Scenario: 停用某一 RP client

- **WHEN** 运营方停用 `auth-admin-spa`
- **THEN** `auth-admin` 无法完成授权码登录；`user-admin` 仍可正常使用 `user-admin-spa` 登录
