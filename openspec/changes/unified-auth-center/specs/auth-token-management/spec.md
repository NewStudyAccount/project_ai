## ADDED Requirements

### Requirement: Redis 是 Token 与会话的权威存储
系统 SHALL 将 SAS 授权元数据、Refresh Token 哈希键、SSO 会话、user 到 grant/会话二级索引、限流和幂等占位保存在 Redis；MySQL 不保存 Token 本体。

#### Scenario: 保存新签发授权
- **WHEN** 系统签发一组授权
- **THEN** Redis 中存在带 TTL 的授权元数据、Refresh Token 哈希键和 user/grant 索引，MySQL 只更新台账字段

### Requirement: Redis 键带命名和 TTL
所有认证中心 Redis 键 SHALL 使用 auth:{模块}:{业务标识} 格式，并设置符合业务生命周期的 TTL。

#### Scenario: 新建 Redis 键
- **WHEN** 系统创建 Refresh Token、会话、限流或幂等键
- **THEN** 键名符合统一命名，且不存在无 TTL 的认证临时键

### Requirement: 重用检测能够吊销 grant 全链
系统 SHALL 在旧 Refresh Token 被重用时，通过 grant/会话索引找到并吊销该用户、客户端和 grant 的 Refresh Token、SSO 会话与活跃台账。

#### Scenario: 重用旧 Refresh Token
- **WHEN** 系统识别到已轮转的 Refresh Token
- **THEN** 该 grant 的新旧 Refresh Token、关联会话和台账状态均被处理为不可继续使用

### Requirement: 踢下线即时失效
系统 SHALL 支持按用户、客户端或 grant 吊销 RT、SSO 会话和活跃授权，并保证 Redis 中的后续刷新请求失败。

#### Scenario: 管理员踢下线
- **WHEN** 管理员调用 grant 或用户级吊销管理接口
- **THEN** Redis 中相关 Refresh Token 与会话失效，auth_grant 状态更新为吊销并记录原因

### Requirement: MySQL 台账不成为 Token 事实源
auth_grant SHALL 仅记录授权/令牌链运营与审计信息，不得保存 Refresh Token 明文、Access Token 或会话内容。

#### Scenario: 查询授权台账
- **WHEN** 管理员查询 auth_grant
- **THEN** 返回用户、client、scope、状态和吊销信息，但不返回任何 Token 值
