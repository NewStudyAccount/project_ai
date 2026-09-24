## ADDED Requirements

### Requirement: 登录账号由用户中心解析
认证中心 SHALL 通过 user-api 的 by-username 契约解析账号并获得 user.id 与 status，不在本地保存用户资料表。

#### Scenario: 用户名解析成功
- **WHEN** 用户名存在且用户中心返回启用状态
- **THEN** 系统以返回的 user.id 作为认证主体继续校验凭证

#### Scenario: 用户名不存在或停用
- **WHEN** 用户中心无法解析账号或 status 非启用
- **THEN** 系统返回不泄露账号存在性的登录失败

### Requirement: 密码仅保存 BCrypt 哈希
sys_credential.secret_ref SHALL 仅保存 BCrypt 哈希，登录校验使用 DelegatingPasswordEncoder 的 {bcrypt} 兼容格式；任何响应和日志不得输出明文密码。

#### Scenario: 校验密码
- **WHEN** 用户提交正确密码
- **THEN** 系统使用 secret_ref 中的 BCrypt 哈希完成校验

#### Scenario: 错误密码
- **WHEN** 用户提交错误密码
- **THEN** 系统记录登录失败，但不输出或返回密码内容

### Requirement: 登录失败有风控记录和限制
系统 SHALL 记录 username、user_id、success、fail_reason、ip、user_agent、client_id 的 login_attempt，并按失败次数实施登录限制。

#### Scenario: 多次失败登录
- **WHEN** 同一账号或 IP 达到失败阈值
- **THEN** 后续登录在限制期内被拒绝，并返回通用的失败/锁定提示

### Requirement: 登录失败提示不泄露账号存在性
认证中心 SHALL 对账号不存在、密码错误、账号停用和达到锁定阈值使用不区分账号存在性的外部提示。

#### Scenario: 不同失败原因
- **WHEN** 登录请求分别因账号不存在、密码错误或账号停用失败
- **THEN** 对外提示不揭示具体失败原因或账号是否存在
