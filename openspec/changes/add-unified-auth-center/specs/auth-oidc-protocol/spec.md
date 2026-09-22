# Spec: auth-oidc-protocol

## Purpose

为各独立业务系统提供标准 OIDC / OAuth2.1 授权码 + PKCE 的认证与令牌签发协议能力，使业务后端可作为 Resource Server 本地校验 JWT 身份。

## ADDED Requirements

### Requirement: 标准 OIDC 发现与授权端点

系统 SHALL 提供标准 OIDC 发现文档与授权码流程端点，支持独立域名 SSO 下各系统的取票与换票。

#### Scenario: 获取发现文档

- **WHEN** 客户端请求 `GET /.well-known/openid-configuration`
- **THEN** 返回 issuer、authorize/token/jwks/userinfo/revoke 等端点元数据

#### Scenario: 授权码签发

- **WHEN** 已登录用户在 `/oauth2/authorize` 携带合法 `client_id`、`redirect_uri`、`state`、`code_challenge`（S256）、`scope=openid`
- **THEN** 重定向回 `redirect_uri` 并携带一次性 `code` 与原 `state`

#### Scenario: redirect_uri 不在白名单

- **WHEN** `/oauth2/authorize` 的 `redirect_uri` 未在客户端白名单中精确匹配
- **THEN** 拒绝授权，不向该 URI 发放 `code`

### Requirement: 授权码换票

系统 SHALL 在 `/oauth2/token` 支持 `authorization_code` 换取 Access Token、Refresh Token 与 ID Token；公开客户端 MUST 校验 PKCE。

#### Scenario: 公开客户端 PKCE 换票成功

- **WHEN** SPA 以 `grant_type=authorization_code`、合法 `code`、`code_verifier`、`redirect_uri`、`client_id` 调用 `/oauth2/token`
- **THEN** 返回 `access_token`（JWT）、`refresh_token`、`id_token`、`token_type=Bearer`、过期秒数

#### Scenario: PKCE 校验失败

- **WHEN** `code_verifier` 与授权时 `code_challenge` 不匹配
- **THEN** 拒绝发票，返回 OAuth2 错误，不签发任何令牌

#### Scenario: 授权码重放

- **WHEN** 同一 `code` 第二次用于换票
- **THEN** 拒绝请求，并作废该授权链相关 RT（重用检测）

### Requirement: Access Token 为可校验 JWT

Access Token SHALL 为 RS256 JWT，经 JWKS 可本地验签；业务 Resource Server SHALL 能离线校验而不回调认证中心。

#### Scenario: 资源方本地验签

- **WHEN** 业务后端收到 `Authorization: Bearer <AT>`
- **THEN** 用 `GET /oauth2/jwks` 公钥验签成功后，从 `sub` 得到用户中心 `user.id`

#### Scenario: Claims 最小集

- **WHEN** 签发 Access Token 或 ID Token
- **THEN** 包含 `iss, sub, aud, exp, iat, jti, auth_time, preferred_username`；`sub` 为用户中心 14 位用户 id；**不含**业务权限码

### Requirement: Refresh Token 轮转

Refresh Token SHALL 不透明存储、每次刷新轮转；公开客户端在 `authorization_code` 流程下 SHALL 可获得 RT。

#### Scenario: 刷新并轮转

- **WHEN** 以合法有效 RT 调用 `grant_type=refresh_token`
- **THEN** 返回新 AT 与新 RT，旧 RT 标记为已轮转作废

#### Scenario: 公开客户端签发 RT

- **WHEN** `client_type=PUBLIC` 且 `client_auth_method=NONE` 的客户端完成授权码换票（含 PKCE）
- **THEN** 响应中包含 `refresh_token`

#### Scenario: 非有效 RT 重用

- **WHEN** 使用已轮转、已吊销或过期的 RT 再次刷新
- **THEN** 拒绝请求，并吊销该 `grant_id` 下全部 RT 链

### Requirement: userinfo 声明来自用户中心

`GET /oauth2/userinfo` SHALL 返回 OIDC profile 声明；资料性数据 SHALL 由认证中心实时或短缓存从用户中心获取，认证库 SHALL NOT 自存用户资料权威表。

#### Scenario: 获取 userinfo

- **WHEN** 携带合法 Access Token 请求 `/oauth2/userinfo`
- **THEN** 返回 `sub`、`preferred_username` 及 profile 类声明（来自用户中心）

#### Scenario: 无效或过期 AT

- **WHEN** Access Token 无效或过期
- **THEN** 返回 401，不返回任何用户信息

### Requirement: 吊销端点

系统 SHALL 提供 `POST /oauth2/revoke` 用于吊销 Refresh Token。

#### Scenario: 吊销 RT

- **WHEN** 客户端以合法 RT 调用 revoke
- **THEN** 该 RT 及轮转链标记为吊销，后续刷新失败
