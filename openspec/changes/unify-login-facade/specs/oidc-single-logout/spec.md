## Purpose

定义跨 RP 与 IdP 的统一登出行为：一次登出同时清理本地令牌、SSO 会话与关联授权，使同浏览器后续访问任意 RP 均需重新登录。

## ADDED Requirements

### Requirement: 统一登出端点

认证中心 SHALL 提供 OIDC `/connect/logout`，在合法请求下失效当前 SSO 会话，并吊销与该会话/用户关联的 Refresh Token 与活跃 grant 台账。

#### Scenario: 用户经 IdP 统一登出

- **WHEN** 用户经 RP 或门户发起统一登出并到达 `/connect/logout`
- **THEN** SSO 会话被删除且 Cookie 过期，关联 Refresh Token 不再可用于刷新，并写入安全审计

#### Scenario: 无会话时登出

- **WHEN** `/connect/logout` 在无有效 SSO 会话时被调用
- **THEN** 返回可预期的登出完成响应或跳转，不泄露账号是否存在

### Requirement: RP 登出必须清理本地并经过统一登出

`user-admin` 与 `auth-admin` 的登出 SHALL 先清除本地 Access Token / Refresh Token / ID Token，再跳转 `/connect/logout`；MUST NOT 仅清本地令牌而不经过统一登出作为主登出路径。

#### Scenario: 任一 RP 退出

- **WHEN** 用户在 `user-admin` 或 `auth-admin` 点击退出
- **THEN** 本地令牌被清除，浏览器被带到 `/connect/logout`，完成后进入允许的 post-logout 地址

#### Scenario: 登出后访问另一 RP

- **WHEN** 用户在 `user-admin` 统一登出后打开 `auth-admin` 受保护路由
- **THEN** 无 SSO 会话且无本地令牌，被引导至唯一登录门面重新登录

### Requirement: 登出回跳地址受控

`/connect/logout` 的 `post_logout_redirect_uri` MUST 精确命中允许列表；非法或缺失时使用认证中心默认登出完成页，禁止开放重定向。

#### Scenario: 合法登出回跳

- **WHEN** 登出请求携带已在允许列表内的 `post_logout_redirect_uri`
- **THEN** 登出完成后 302 到该地址

#### Scenario: 非法登出回跳

- **WHEN** 登出请求携带不在允许列表内的 `post_logout_redirect_uri`
- **THEN** 不跳转到该地址，并使用默认登出完成响应

### Requirement: 登出与踢下线同源失效

统一登出 SHALL 与管理端「踢下线」共享同一套会话/RT/grant 失效语义，保证 Redis 中后续刷新失败。

#### Scenario: 登出后刷新失败

- **WHEN** 某 RP 在统一登出后仍持有旧 Refresh Token 并尝试刷新
- **THEN** 刷新失败，系统不签发新令牌
