## ADDED Requirements

### Requirement: 认证与运营事件可审计
系统 SHALL 记录 LOGIN、TOKEN_ISSUE、REFRESH、REVOKE、CLIENT_UPDATE、CLIENT_SECRET_RESET 等安全事件的 actor、target、detail、ip 和时间。

#### Scenario: 记录令牌签发
- **WHEN** 系统成功签发一组令牌
- **THEN** 系统写入 TOKEN_ISSUE 审计，包含用户、client 和 grant 目标标识

### Requirement: 登录尝试可查询
系统 SHALL 提供 /api/v1/login-attempts 查询登录成功、失败、锁定记录，支持 username、user_id、client_id、ip、时间范围和 success 过滤。

#### Scenario: 查询登录审计
- **WHEN** 管理员按用户名和时间范围查询
- **THEN** 系统返回分页结果、总数、页大小和当前页，且不包含密码或完整 Token

### Requirement: 安全审计可查询
系统 SHALL 提供 /api/v1/audit-logs 查询安全审计，支持 action、actor、target、时间和 IP 过滤，并按创建时间倒序分页。

#### Scenario: 查询吊销事件
- **WHEN** 管理员查询 REVOKE 事件
- **THEN** 系统返回对应 grant/target、操作者、原因和时间

### Requirement: 审计内容必须脱敏
auth_audit_log.detail SHALL 不保存密码、secret 明文、Refresh Token、Access Token 或完整会话值，日志输出同样遵循敏感信息脱敏规则。

#### Scenario: 记录 Client 密钥重置
- **WHEN** 运营人员重置 Client secret
- **THEN** 审计只记录重置动作和目标，不记录新旧 secret 明文或哈希全文
