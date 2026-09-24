## ADDED Requirements

### Requirement: OIDC 发现文档可用
系统 SHALL 提供 /.well-known/openid-configuration 发现文档，公开说明 issuer、授权端点、令牌端点、JWKS、userinfo 和支持的授权类型。

#### Scenario: 查询发现文档
- **WHEN** 客户端以 GET 请求 /.well-known/openid-configuration
- **THEN** 系统返回 200、JSON 文档，并包含本认证中心的实际端点与 RS256/JWKS 元数据

### Requirement: 授权码登录受 PKCE 和精确重定向约束
系统 SHALL 仅在 client_id 有效、redirect_uri 精确命中白名单、code_challenge 使用 S256 且 state 与 nonce 合法时签发 authorization code，并在登录成功后创建 SSO 会话。

#### Scenario: 公共 SPA 完成授权
- **WHEN** PUBLIC 客户端发起 /oauth2/authorize，携带合法 S256 PKCE、state、nonce 和精确 redirect_uri
- **THEN** 系统完成账密登录后重定向到 redirect_uri，并携带一次性 authorization code

#### Scenario: 重定向地址不匹配
- **WHEN** 授权请求的 redirect_uri 不在 client 白名单中
- **THEN** 系统拒绝授权，不签发 code，不创建 grant

### Requirement: 令牌端点签发最小令牌
系统 SHALL 在 /oauth2/token 支持 authorization_code 和 refresh_token grant，签发 RS256 JWT Access Token、不透明 Refresh Token 和 OIDC ID Token；Claims 仅包含 iss、sub、aud、exp、iat、jti、auth_time、preferred_username。

#### Scenario: 授权码换票
- **WHEN** 客户端提交有效 code、redirect_uri、code_verifier 和 client 身份
- **THEN** 系统返回 Access Token、Refresh Token、ID Token，并将 Access Token 的 sub 设置为用户中心 user.id 的 16 位字符串

#### Scenario: Claims 不含权限码
- **WHEN** 系统签发 Access Token
- **THEN** Token 中不包含业务权限码，业务系统自行按 RBAC 查询

### Requirement: Refresh Token 轮转
系统 SHALL 在 refresh_token grant 成功时签发新的 Refresh Token、吊销旧 Refresh Token，并保留旧键至原过期时间用于重用检测。

#### Scenario: 刷新令牌
- **WHEN** 客户端使用未过期且未标记已轮转的 Refresh Token 刷新
- **THEN** 系统返回新的 Access Token 和新的 Refresh Token，旧 Refresh Token 不再可用于正常刷新

#### Scenario: 检测 Refresh Token 重用
- **WHEN** 系统收到已被轮转的旧 Refresh Token
- **THEN** 系统判定为重用，吊销该 grant 全链并记录审计

### Requirement: userinfo 返回用户中心资料
系统 SHALL 对 /oauth2/userinfo 执行 Access Token 验签，并通过 user-api 获取用户中心资料后组装 OIDC claims。

#### Scenario: 已认证用户查询资料
- **WHEN** 客户端携带有效 Access Token 请求 /oauth2/userinfo
- **THEN** 系统返回与用户中心当前资料一致的 OIDC claims，不返回本地用户资料副本

### Requirement: 吊销端点支持 Refresh Token 与会话吊销
系统 SHALL 提供 /oauth2/revoke，并在有效凭据下吊销 Refresh Token、SSO 会话和关联 grant 台账。

#### Scenario: 吊销 Refresh Token
- **WHEN** 客户端提交可识别的 Refresh Token
- **THEN** 系统将其标记为吊销、停止后续刷新，并写入安全审计
