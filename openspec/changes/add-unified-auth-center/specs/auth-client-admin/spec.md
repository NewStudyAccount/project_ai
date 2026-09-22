# Spec: auth-client-admin

## Purpose

提供 OAuth Client 注册与运营能力，以及授权台账、令牌吊销与安全审计查询，支撑各业务系统作为标准客户端接入与认证侧日常运营。

## ADDED Requirements

### Requirement: OAuth Client 注册与管理

系统 SHALL 以自研管理端提供 OAuth Client 的创建、查询、编辑、启停；`oauth_client` 为客户端定义权威，驱动协议层 `RegisteredClient`。

#### Scenario: 创建客户端

- **WHEN** 管理员创建客户端并提交 `client_id`、`client_name`、`client_type`、`grant_types`、`redirect_uris`、`scopes`、TTL、负责人等
- **THEN** 写入 `oauth_client`，协议层可按 `client_id` 解析该客户端

#### Scenario: 查询与搜索客户端

- **WHEN** 管理员按名称、`client_id`、`system_code` 或启用状态检索
- **THEN** 返回分页列表（`records/total/size/current`），可查看详情

#### Scenario: 编辑客户端

- **WHEN** 管理员修改 redirect_uris、scopes、TTL、负责人、备注等
- **THEN** 更新 `oauth_client`，后续授权/换票按新配置生效

#### Scenario: 启停客户端

- **WHEN** 管理员停用客户端
- **THEN** 该客户端无法继续授权码签发与换票；启用后恢复

### Requirement: redirect_uri 与 PKCE 策略

客户端 SHALL 维护精确 `redirect_uri` 白名单；公开客户端 SHALL 强制 PKCE（S256）。

#### Scenario: 公开客户端强制 PKCE

- **WHEN** `client_type=PUBLIC` 且 `require_pkce=1` 的客户端发起授权
- **THEN** 缺少或使用非 S256 的 `code_challenge` 时拒绝授权

#### Scenario: 精确匹配 redirect_uri

- **WHEN** 授权或换票携带的 `redirect_uri` 与注册值不完全一致
- **THEN** 拒绝请求

### Requirement: 客户端密钥一次性明文

机密客户端 `client_secret` SHALL 仅在创建或重置时明文返回一次；存储 SHALL 为哈希。

#### Scenario: 创建机密客户端

- **WHEN** 创建 `client_type=CONFIDENTIAL` 客户端
- **THEN** 响应中明文 secret 仅出现一次，库中仅存 `client_secret_hash`

#### Scenario: 重置密钥

- **WHEN** 管理员重置某客户端密钥
- **THEN** 生成新 secret 明文返回一次，旧 secret 失效

### Requirement: 授权台账与踢下线

系统 SHALL 维护 `auth_grant` / `auth_refresh_token` 台账，支持按用户或客户端吊销（「踢下线」）。

#### Scenario: 按用户踢下线

- **WHEN** 管理员对某用户执行踢下线
- **THEN** 该用户名下活跃 grant 标记吊销，其 RT 链全部失效，业务侧刷新失败

#### Scenario: 按客户端吊销

- **WHEN** 管理员按客户端吊销授权
- **THEN** 该客户端相关 grant/RT 失效

#### Scenario: 台账与协议一致

- **WHEN** 吊销操作执行
- **THEN** 先使 SAS 协议层授权失效/revocation，再更新运营台账

### Requirement: 认证运营审计查询

管理端 SHALL 支持查询登录尝试与安全审计，便于追溯异常登录与令牌操作。

#### Scenario: 查询登录审计

- **WHEN** 管理员按时间、用户名、IP、`client_id` 或结果筛选
- **THEN** 返回 `login_attempt` 分页结果

#### Scenario: 查询安全审计

- **WHEN** 管理员按 `action`、时间、操作者筛选
- **THEN** 返回 `auth_audit_log` 分页结果；展示内容不含密码或完整 token

### Requirement: 管理端不做业务用户管理

客户端管理后台 SHALL 仅覆盖 OAuth Client 与认证侧运营；SHALL NOT 提供业务用户主数据管理或用户查询 API。

#### Scenario: 无用户管理入口

- **WHEN** 使用认证客户端管理端
- **THEN** 菜单与 API 不包含用户资料编辑、启停等用户中心职责
