## ADDED Requirements

### Requirement: OAuth Client 可创建和维护
系统 SHALL 提供 /api/v1/clients/** 的 Client 查询、创建、更新、启停和删除能力，并记录操作者、目标和变更时间。

#### Scenario: 创建客户端
- **WHEN** 已授权运营人员提交合法 Client 创建请求
- **THEN** 系统生成 client_id，按客户端类型决定是否生成 secret，并返回创建结果和唯一一次可见的明文 secret

### Requirement: Client secret 只保存哈希
机密客户端的 client_secret SHALL 使用 BCrypt 或 DelegatingPasswordEncoder 的 bcrypt 格式保存；公开客户端的 client_secret_hash 为空；创建和重置后明文只允许返回一次。

#### Scenario: 重置客户端密钥
- **WHEN** 运营人员调用 /clients/{id}/secret
- **THEN** 系统生成新 secret，只在响应中返回一次明文，数据库中只保存哈希

### Requirement: redirect_uri 精确白名单
Client SHALL 保存精确 redirect URI 集合；授权和换票流程只接受完全匹配项，不接受前缀匹配或任意通配。

#### Scenario: 配置跳转地址
- **WHEN** 运营人员更新 redirect_uris
- **THEN** 后续授权请求仅对精确匹配的 URI 成功，其他 URI 返回明确错误

### Requirement: Client 安全配置可约束令牌策略
Client SHALL 支持 client_type、client_auth_method、grant_types、scopes、require_pkce、require_consent、access_token_ttl_sec、refresh_token_ttl_sec 和 reuse_refresh_tokens 等配置；reuse_refresh_tokens 必须为 0。

#### Scenario: 配置公共 SPA
- **WHEN** 运营人员创建 PUBLIC 客户端
- **THEN** require_pkce 默认为 1，refresh token TTL 默认为 604800，reuse_refresh_tokens 保持为 0

#### Scenario: 配置机密客户端令牌 TTL
- **WHEN** 运营人员将 access_token_ttl_sec 设置为 300 或 900
- **THEN** 系统接受配置并在令牌签发时应用

#### Scenario: 拒绝复用 Refresh Token
- **WHEN** 任何创建或更新请求把 reuse_refresh_tokens 设置为非 0
- **THEN** 系统拒绝写入并返回参数校验错误

### Requirement: Client 运营变更可审计
所有 Client 创建、更新、启停和 secret 重置 SHALL 写入安全审计，且审计 detail 不包含 secret 明文或完整 Token。

#### Scenario: 查询 Client 审计
- **WHEN** 运营人员查询安全审计
- **THEN** 可按 action、target_id、时间和 actor 过滤 Client 运营事件
