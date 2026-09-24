## Context

统一认证中心是为多个独立后台系统提供登录、令牌签发和 OAuth Client 运营的系统。已确认形态是纯前后端分离的单体服务：一个可运行的 Spring Boot 应用 auth-service，加上 auth-common、auth-framework 两个基础模块，配套 frontend/auth-portal 登录页和 frontend/auth-admin 运营后台。认证协议内核使用 Spring Authorization Server，业务系统通过 JWKS 离线验签，不依赖认证中心在线可用。

本变更的约束以 CLAUDE.md 为准。由于本系统采用单体、无网关、无 Nacos、CORS 由 auth-service 自配，相关偏差必须按统一认证中心设计文档 §9 登记，不得扩展成其他系统的默认架构。

## Goals / Non-Goals

**Goals:**

- 交付账密登录、SSO 会话、OIDC 授权码、PKCE、Access Token、Refresh Token、ID Token、JWKS、userinfo、revoke 和发现文档。
- 交付 OAuth Client 注册、配置、启停、密钥创建/重置和审计能力。
- 交付 Redis 管理 Token/会话、MySQL 管理凭证和台账的分层模型。
- 交付 auth-admin 自持的 RBAC 管理和安全审计查询。
- 保持业务系统独立部署，认证中心只通过 user-api 的显式 Feign 契约访问用户中心。

**Non-Goals:**

- 不实现用户主数据、集中式 RBAC、C 端注册、找回密码、短信/邮箱/MFA、社交登录、动态客户端注册、多租户。
- 不实现 Access Token 即时黑名单和 OIDC /connect/logout，留作二期。
- 不引入网关、Nacos、MinIO、RocketMQ、Quartz、Sentinel、独立监控面、DB 迁移框架或其他与 CLAUDE.md §2 冲突的平行选型。
- 不跨服务共享 Entity/Mapper，不把用户中心用户表复制到 auth_db。

## Decisions

### 1. 采用前后端分离单体，而不是微服务

auth-service 是唯一可运行后端应用，Nginx 直接托管前端静态资源并反代 /api、/oauth2。这样减少部署组件和跨服务鉴权链路，符合本系统已裁决形态。备选的网关/Nacos 微服务形态被否决，因为它与本系统范围不匹配，且会引入不必要的注册、配置和边缘治理复杂度。对应偏差：本网关链路豁免、CORS 由 auth-service 自配、鉴权单层。

### 2. 采用 common + framework + service 三模块

auth-common 只放 Result、错误码、异常、分页契约、BaseEnum、常量和零配置工具。auth-framework 封装 Spring Security、SAS、MyBatis-Plus、Redis/Redisson、OpenFeign、MapStruct 之外的装配、JWT、审计、幂等、限流、追踪和配置绑定。auth-service 负责 Controller、Service、Entity、Mapper、业务规则和应用配置。

备选的单模块工程被否决，因为它无法清晰隔离零配置契约与需要中间件装配的基础能力，且不利于遵循 CLAUDE.md §5.4 的落点规则。

### 3. SAS 负责协议内核，自研扩展点集中在 framework

授权端点、令牌端点、JWKS、发现文档、userinfo 使用 Spring Authorization Server。RegisteredClientRepository 适配 oauth_client 表，OAuth2AuthorizationService 使用 Redis 适配实现，OAuth2TokenCustomizer 负责最小 Claims，OAuth2RefreshTokenGenerator 允许 public client 在 authorization_code 下获得 Refresh Token，并强制刷新轮转。

Access Token 使用 RS256 JWT，默认 10 分钟，允许 5–15 分钟配置。Refresh Token 不透明、Redis 哈希键保存、7 天 TTL、每次刷新轮转，旧键保留到原过期时间以支持重用检测和 grant 全链吊销。Claims 只包含 iss、sub、aud、exp、iat、jti、auth_time、preferred_username，禁止塞入业务权限码。

### 4. Redis 管 Token，MySQL 管台账

Redis 是授权码元数据、Refresh Token、SSO 会话、user 到 grant/会话二级索引、限流和幂等占位的权威存储。MySQL 只保存 sys_credential、oauth_client、auth_grant、login_attempt、auth_audit_log 以及 RBAC 表，不保存 Token 本体，避免双源事实。Redis 缓存键遵循 CLAUDE.md §6.9，使用 auth:{模块}:{业务标识} 格式并设置 TTL。

### 5. 用户解析与 userinfo 复用 user-api

登录时 auth-service 通过 OpenFeign 直连 URL 调用用户中心 by-username，获得 user.id 和 status。userinfo 同样通过 user-api 获取资料。Feign 必须配置 Fallback 和固定连接/读取超时，禁止在事务内调用。认证中心只保存凭证，不保存用户资料表。

### 6. 密码与凭证策略

sys_credential.secret_ref 只保存 BCrypt 哈希，使用 DelegatingPasswordEncoder，默认 {bcrypt} 前缀，给后续 Argon2 升级留余地。登录失败限制记录 login_attempt，失败提示不泄露账号存在性。公开 SPA 使用 PKCE S256，redirect_uri 必须精确匹配白名单，授权请求校验 state 和 nonce。

### 7. 数据库与发号

auth_db 使用 utf8mb4/InnoDB，禁止物理外键。业务表 6 张，RBAC 表 4 张，共 10 张。主键 id 使用 16 位发号，按业务日 yyyyMMdd 加 8 位日序列，出参 Long 序列化为 String。所有业务表包含 createTime、updateTime、createBy、updateBy、deleted，审计赋值由 framework 统一处理。SQL 输出到 deploy/db/migration/auth_db/，人工执行，不使用 Flyway/Liquibase。

### 8. 配置和安全

auth-service 必须提供 application.yml、application-local.yml、application-test.yml。组件 IP、端口、账号密码、信任域名登记在 docs/test-env.md；生产敏感项走 Nacos/密钥管理/Jenkins Credentials。登录、换票和 revoke 使用 @RateLimit，关键写使用 @Idempotent。日志必须带 traceId，审计日志禁止密码和完整 Token。

## Risks / Trade-offs

- [Redis 数据丢失会导致 Refresh Token 和 SSO 会话失效] → 开启 AOF everysec 与 RDB，接受重新登录的可用性代价，凭证和台账仍从 MySQL 恢复。
- [Refresh Token 重用检测依赖旧键保留] → 新旧键都设置 TTL，吊销时按 grant 索引批量失效并记录审计。
- [auth-service 集成登录与运营后台，权限面较大] → auth-admin 使用本系统 RBAC 细分菜单和角色，所有管理写接口启用权限校验、幂等和审计。
- [用户中心不可用时登录失败] → Feign 配置 Fallback、超时和限流，明确这是认证链路依赖；不复制用户主数据作为旁路。
- [单体扩展上限低于微服务] → 当前以统一部署、简单运维为目标；若未来需要拆分，必须另开变更，不在本变更内预留半套网关。
- [Access Token 在过期前无法即时吊销] → 采用短 TTL 默认 10 分钟，敏感踢下线即时撤销 Refresh Token、会话和 grant 台账；jti 黑名单留二期。

## Migration Plan

1. 准备 auth_db 与 Redis，人工执行 deploy/db/migration/auth_db/ 下的 SQL。
2. 按 docs/version-baseline.md 锁定依赖版本，配置 application-local.yml 与 application-test.yml，登记 docs/test-env.md。
3. 部署 auth-service，health 以 /actuator/health 校验，Redis 和 MySQL 连接成功后才接入流量。
4. 配置 Nginx 托管 frontend/auth-portal 和 frontend/auth-admin，反代 /api 与 /oauth2 到 auth-service，信任域名在 auth-service 与 docs/test-env.md 对齐。
5. 冒烟验证 OIDC 发现文档、登录、授权码换票、PKCE、刷新轮转、revoke、JWKS 验签、Client CRUD、踢下线和审计查询。
6. 回滚策略是下线 auth-service 并保留 auth_db 与 Redis 快照，不做不可逆数据变更；生产执行 SQL 前必须征询。

## Open Questions

- 无阻塞问题。auth-admin 首期使用同域会话、长期迁移到 public + PKCE 的路线已由设计文档预留，实施时按 tasks 勾选项决定是否纳入本变更。
- OIDC /connect/logout、Access Token jti 黑名单、DPoP、Argon2 升级和凭证初始化 API 均为二期，不在本次实施范围内。
