# Spec: auth-portal-ui

## ADDED Requirements

### Requirement: 统一登录入口前端

认证中心 SHALL 提供 `auth-portal` 前端（Vue3 + Element Plus）作为统一登录与 SSO 入口；SHALL NOT 仅提供无 UI 的 HTTP API 而缺失用户可操作登录页。

#### Scenario: 打开登录页

- **WHEN** 用户访问 auth-portal 登录路由
- **THEN** 展示用户名/密码登录表单，可提交至认证服务登录接口

#### Scenario: 登录成功进入会话

- **WHEN** 用户提交正确账密
- **THEN** 前端保存 Access/Refresh 策略并接收 sid Cookie（由服务端下发），进入登录后提示或按参数回跳

### Requirement: SSO 回跳与换令牌

前端 SHALL 支持 `client_id` / `return_url` 场景：无会话时展示登录；登录成功后引导 `/auth/sso/authorize`；处理回跳 `code` 并调用 `POST /auth/sso/token` 换取目标系统 JWT。

#### Scenario: 带参登录后回跳

- **WHEN** 登录 URL 携带 `return_url` 与 `client_id` 且登录成功
- **THEN** 前端跳转或引导至 SSO authorize，使目标系统获得一次性 code/JWT

#### Scenario: 处理授权码

- **WHEN** 页面收到合法 `code` 与 `client_id`
- **THEN** 调用 SSO token 接口换取 AccessToken；失败时展示错误且不泄露令牌日志

### Requirement: 令牌与错误展示

前端 SHALL 按统一返回体 `{code,msg,data}` 处理业务错误；SHALL NOT 将 password 写入持久化存储；SHALL NOT 在日志中输出完整 Token；登出 SHALL 调用认证登出并清理本地令牌。

#### Scenario: 登录失败提示

- **WHEN** 登录返回业务错误（如密码错误、账号停用、失败锁定）
- **THEN** 页面展示服务端 `msg`，不进行自动重试爆破

#### Scenario: 登出

- **WHEN** 用户点击登出
- **THEN** 调用 `/auth/logout`，清理本地 access/refresh，并回到登录页

### Requirement: 范围边界

auth-portal SHALL 聚焦认证与 SSO 入口；账号管理完整后台、各业务系统工作台 SHALL NOT 在本期 auth-portal 内实现。

#### Scenario: 非目标页面

- **WHEN** 规划 auth-portal 路由
- **THEN** 仅含登录/SSO/登出相关视图，不含业务系统菜单与角色管理
