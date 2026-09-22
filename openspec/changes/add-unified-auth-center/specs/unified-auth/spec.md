# Spec: unified-auth

## ADDED Requirements

### Requirement: 单体自研轻量认证

认证中心 SHALL 以单体应用交付（单进程 auth-service）；认证协议与账号/SSO/令牌策略 SHALL 为项目自研；密码哈希与 JWT 签名验签 SHALL 使用成熟安全库；SHALL NOT 引入外购 IdP 产品作为必选组件；SHALL NOT 将认证拆成多个独立微服务进程。

#### Scenario: 部署形态

- **WHEN** 部署认证中心
- **THEN** 为单一 auth-service 进程对外提供登录/SSO/令牌/用户查询能力，不依赖 user/sso/token 等独立认证微服务

#### Scenario: 自研协议边界

- **WHEN** 实现登录与 SSO 回跳
- **THEN** 项目自行定义 sid、一次性 code 与回跳换 JWT 语义，密码哈希与 JWT 算法由成熟库实现而非自行设计密码学

### Requirement: 身份权威与凭证存储

认证中心 SHALL 作为用户身份与凭证的唯一权威源；密码 SHALL 仅以 BCrypt 或 Argon2 哈希存储；SHALL NOT 将凭证提供给业务系统库。

#### Scenario: 创建用户

- **WHEN** 管理员通过认证中心创建用户
- **THEN** 生成 14 位主键，哈希保存密码，用户名全局唯一，响应不含明文密码

#### Scenario: 业务系统不得持有凭证

- **WHEN** 业务系统同步或投影用户信息
- **THEN** 得到的字段中不含 password_hash 或等价凭证

### Requirement: 账号密码登录与令牌签发

系统 SHALL 支持账号密码登录；成功时 SHALL 签发 JWT Access Token（30 分钟）与 Refresh Token（7 天，Redis 可吊销）；失败登录 SHALL 受限制以降低爆破风险。

#### Scenario: 登录成功

- **WHEN** 用户名密码正确且账号启用
- **THEN** 返回 Access 与 Refresh Token，并更新最近登录信息

#### Scenario: 密码错误或已停用

- **WHEN** 密码错误或账号 status 为停用
- **THEN** 不签发 Token，返回认证失败

### Requirement: 单点登录

认证中心 SHALL 维护全局 SSO 会话（sid）；用户已建立有效 sid 时，进入其他已接入子系统 SHALL 无需再次输入密码即可获得该系统可用 JWT。

#### Scenario: 二次进入其他子系统

- **WHEN** 用户已登录认证中心（存在有效 sid）并访问子系统 B 的受保护入口
- **THEN** 经认证中心授权回调为 B 签发 JWT，无需再次输入密码

#### Scenario: 无 SSO 会话

- **WHEN** 用户无有效 sid 访问子系统受保护入口
- **THEN** 引导至认证中心登录页，登录成功后再签发子系统 JWT

### Requirement: 令牌刷新、登出与吊销

系统 SHALL 支持 Refresh 轮转与登出；登出或修改密码 SHALL 使该用户 Refresh 与 SSO 会话失效。

#### Scenario: 登出

- **WHEN** 用户登出
- **THEN** sid 与 Refresh 失效，无法静默进入子系统

#### Scenario: 修改密码

- **WHEN** 用户修改密码成功
- **THEN** 现有 SSO 会话与 Refresh 全部失效

### Requirement: 认证入口不经网关

认证服务 SHALL 由 Nginx 直接反向代理对外提供认证与 SSO 能力；SHALL NOT 要求经过业务系统网关。

#### Scenario: 访问认证接口

- **WHEN** 客户端请求登录或 SSO 接口
- **THEN** 流量路径为 Nginx 至认证服务，不经过任何业务系统 gateway

### Requirement: 用户只读查询 API

认证中心 SHALL 提供用户搜索与按 id 批量查询 API，供子系统选人与投影补洞；API SHALL NOT 提供角色或权限管理。

#### Scenario: 选人搜索

- **WHEN** 子系统管理端按关键字搜索用户
- **THEN** 返回用户 id、用户名、姓名、状态等展示字段，不含凭证

#### Scenario: 批量补洞

- **WHEN** 子系统按 user_id 列表批量查询
- **THEN** 返回对应用户展示信息，供投影 upsert

### Requirement: 账号安全维护接口

认证中心 SHALL 支持改密、启用/停用等账号安全操作；SHALL NOT 在认证中心实现各业务系统的角色授权管理。

#### Scenario: 停用账号

- **WHEN** 管理员停用用户
- **THEN** 该用户无法继续登录，已有会话/令牌按吊销策略失效
