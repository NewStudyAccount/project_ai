# Spec: auth-login-sso

## Purpose

提供账密登录与认证中心自有 SSO 会话能力，登录时依赖用户中心解析账号状态，密码凭证仅存认证库。

## ADDED Requirements

### Requirement: 账密登录

系统 SHALL 支持用户名密码登录；登录成功建立 SSO 会话并继续 OIDC 授权发码。

#### Scenario: 登录成功

- **WHEN** 用户提交正确用户名与密码
- **THEN** 建立 SSO 会话，可继续 `/oauth2/authorize` 发放授权码

#### Scenario: 密码错误

- **WHEN** 用户名存在但密码错误
- **THEN** 登录失败，提示凭据错误；**不**泄露账号是否存在的差异文案之外的信息

#### Scenario: 用户中心账号停用

- **WHEN** 密码正确但用户中心 `status` 为停用
- **THEN** 拒绝登录，返回账号不可用提示

#### Scenario: 用户不存在或用户中心不可用

- **WHEN** 用户名在用户中心不存在，或用户中心调用失败
- **THEN** 登录失败（401/503 语义），不建立会话

### Requirement: 账号解析依赖用户中心

认证中心 SHALL NOT 自存用户主数据权威；登录解析 `username → user_id + status` SHALL 走用户中心 API。

#### Scenario: 登录时解析账号

- **WHEN** 登录流程需要解析用户名
- **THEN** 调用用户中心 `GET /api/v1/users/by-username/{username}` 获取 `user_id` 与 `status`

#### Scenario: 事务外 Feign

- **WHEN** 认证中心调用用户中心
- **THEN** 调用发生在数据库事务之外，且具备超时与 Fallback

### Requirement: SSO 会话

认证中心 SHALL 持有自有 SSO 会话（与业务系统会话分离）；有效会话可跨客户端静默续权发码。

#### Scenario: 同域 SSO 续权

- **WHEN** 用户已有未过期 SSO 会话，再次进入另一客户端的 `/oauth2/authorize`
- **THEN** 无需重复输入密码即可发码

#### Scenario: 会话过期或吊销

- **WHEN** SSO 会话过期或被吊销
- **THEN** 下次授权要求重新登录

#### Scenario: 会话安全属性

- **WHEN** 下发 SSO 会话 Cookie
- **THEN** 强制 HTTPS；`SameSite` 策略按跨站 redirect 场景配置，会话令牌仅存哈希

### Requirement: 密码凭证仅存认证库

用户密码 SHALL 仅以单向哈希存于认证库 `sys_credential`（`secret_ref`）；用户中心 SHALL NOT 存储 `password_hash`。

#### Scenario: 凭证初始化

- **WHEN** 用户中心创建账号后调用认证中心初始化凭证（或经事件最终一致）
- **THEN** 在 `sys_credential` 创建 `credential_type=PASSWORD` 记录，`secret_ref` 为 BCrypt/Argon2 哈希

#### Scenario: 凭证校验

- **WHEN** 登录校验密码
- **THEN** 使用 `sys_credential` 中该 `user_id` 的 PASSWORD 哈希比对，禁止明文存储或日志输出密码

#### Scenario: 多类型凭证预留

- **WHEN** 查询凭证
- **THEN** 以 `(user_id, credential_type)` 唯一定位；首期仅启用 PASSWORD，SMS/EMAIL/TOTP 等类型可后续扩展

### Requirement: 登录风控与审计

系统 SHALL 记录登录尝试与安全审计，支持失败限制与事后追溯。

#### Scenario: 记录登录尝试

- **WHEN** 发生登录尝试（成功或失败）
- **THEN** 写入 `login_attempt`（username、user_id、success、fail_reason、ip、user_agent、client_id）

#### Scenario: 失败锁定提示

- **WHEN** 短时间内同一账号或 IP 失败次数超阈值
- **THEN** 拒绝后续尝试或要求冷却，提示锁定/稍后再试，不泄露账号是否存在

#### Scenario: 安全审计

- **WHEN** 发生 LOGIN / TOKEN_ISSUE / REFRESH / REVOKE / CLIENT_UPDATE 等动作
- **THEN** 写入 `auth_audit_log`；`detail` SHALL NOT 包含密码或完整 token

### Requirement: 登录页职责边界

登录页 SHALL 负责账密登录与失败/锁定提示；SHALL NOT 提供注册、找回密码（归用户中心或二期认证策略）。

#### Scenario: 不提供注册

- **WHEN** 访问认证中心登录页
- **THEN** 仅提供登录入口，无注册/自助找回密码流程
