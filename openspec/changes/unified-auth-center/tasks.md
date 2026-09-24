## 1. 工程与契约准备

- [x] 1.1 创建 backend/auth 多模块工程：auth-common、auth-framework、auth-service，并按 docs/version-baseline.md 锁定 Maven 依赖版本。
- [x] 1.2 创建 frontend/auth-portal 与 frontend/auth-admin 的 Vue3 + TypeScript + Pinia + Element Plus 工程骨架，并配置 lint 与 type-check scripts。
- [x] 1.3 在 docs/error-code-ranges.md 为认证中心登记系统号，创建 AuthErrorCodeEnum 和统一返回体、分页、异常契约。
- [x] 1.4 创建 auth-service 的 application.yml、application-local.yml、application-test.yml，并在 docs/test-env.md 登记 IP/端口/账密/信任域名。
- [x] 1.5 配置 auth-common、auth-framework、auth-service 的包结构和依赖边界，确保需配置组件只在 framework 装配。

## 2. 数据库与持久化

- [x] 2.1 创建 deploy/db/migration/auth_db/ 的 auth_db 初始化 SQL，覆盖 6 张业务表、4 张 RBAC 表和全部审计字段。
- [x] 2.2 按 16 位 yyyyMMdd + 8 位日序列实现 sys_sequence 发号表和 framework 的 IdGenerator，禁止业务自建主键。
- [x] 2.3 创建 sys_credential、oauth_client、auth_grant、login_attempt、auth_audit_log 的 Entity、Mapper、Service，并对齐 snake_case 表字段、索引和逻辑删除约定。
- [x] 2.4 创建 sys_menu、sys_role、sys_user_role、sys_role_menu 的 Entity、Mapper、Service，按 rbac-design.md 的 4 表结构接入。
- [x] 2.5 验证 SQL 和索引命名符合 CLAUDE.md §6.5，单表索引不超过 5，查询不使用 SELECT *。

## 3. 基础框架能力

- [x] 3.1 在 auth-common 实现 Result、分页契约、BaseEnum、错误码、业务异常和通用工具，保持零配置纯基础。
- [x] 3.2 在 auth-framework 装配 MyBatis-Plus、HikariCP、Redis/Redisson、审计字段填充、逻辑删除和 16 位发号。
- [x] 3.3 在 auth-framework 装配全局 ObjectMapper，使出参 Long（含 id）序列化为 String。
- [x] 3.4 在 auth-framework 装配 Bean Validation、全局异常处理、统一错误码映射和 traceId 日志格式。
- [x] 3.5 在 auth-framework 装配 @Idempotent 和 @RateLimit，底层分别使用 Redis 占位和 Redisson RRateLimiter。
- [x] 3.6 在 auth-framework 装配 Micrometer Tracing、SLF4J/Logback、Actuator 和 springdoc，禁止业务自建日志旁路。
- [x] 3.7 在 auth-framework 装配 MapStruct converter 约定、OpenFeign 直连 URL、Fallback、连接/读取超时和禁止事务内调用的约束。

## 4. OIDC 与登录链路

- [x] 4.1 在 auth-service 集成 Spring Authorization Server，开放发现文档、/oauth2/authorize、/oauth2/token、/oauth2/jwks、/oauth2/userinfo、/oauth2/revoke。
- [x] 4.2 实现 oauth_client 表到 RegisteredClientRepository 的适配，支持 PUBLIC/CONFIDENTIAL、精确 redirect_uri、grant_types、scopes、PKCE 和 TTL。
- [x] 4.3 实现 Redis 版 OAuth2AuthorizationService，保存授权码/Access Token 元数据/授权记录，并使用 auth:{模块}:{业务标识} 键和 TTL。
- [x] 4.4 实现自定义 OAuth2RefreshTokenGenerator，允许 public + authorization_code 签发 Refresh Token，并保证每次刷新轮转。
- [x] 4.5 实现 OAuth2TokenCustomizer，签发 RS256 JWT Access Token/ID Token，Claims 限定 iss、sub、aud、exp、iat、jti、auth_time、preferred_username。
- [x] 4.6 实现 auth-portal 登录、SSO 会话创建/延续、formLogin 失败锁定提示和 state/nonce 校验，提示不泄露账号存在性。
- [ ] 4.7 验证 PKCE S256、redirect_uri 精确匹配、授权码一次性换票、Refresh Token 刷新和 JWKS 离线验签流程。

## 5. Token 管理与吊销

- [x] 5.1 实现 Redis Refresh Token 哈希键、SSO 会话键、user 到 grant/会话二级索引和全部 TTL 策略。
- [x] 5.2 实现 Refresh Token 轮转、旧键已轮转标记、重用检测和 grant 全链吊销。
- [x] 5.3 实现 Redis/MySQL 分层写入：Redis 管 Token 与会话，auth_grant 只记台账，不保存 Token 本体。
- [x] 5.4 实现 /oauth2/revoke 和管理侧 grant/用户级踢下线，即时失效 RT、SSO 会话和活跃台账。
- [x] 5.5 实现 Redis 持久化配置要求（AOF everysec + RDB）和缓存键 TTL 检查。

## 6. 用户中心集成与凭证

- [x] 6.1 引入 user-api 契约构件，配置 OpenFeign 直连 URL、Fallback、连接/读取超时和 user-center.base-url 配置。
- [x] 6.2 实现 by-username 登录解析，取得 user.id/status，并在用户不存在、停用、失败时统一返回不泄露账号存在性的提示。
- [x] 6.3 实现 sys_credential 的 BCrypt/DelegatingPasswordEncoder 密码校验，secret_ref 禁止明文，日志和响应禁止密码。
- [x] 6.4 实现 userinfo 的 user-api 资料组装，认证中心不保存用户资料表。
- [x] 6.5 实现 login_attempt 记录、失败阈值、账号/IP 限制和登录审计写入。

## 7. 管理 API 与 RBAC

- [x] 7.1 实现 /api/v1/clients/** 的 Client CRUD、启停和 secret 创建/重置，明文 secret 仅创建/重置响应一次。
- [x] 7.2 实现 Client 配置校验：reuse_refresh_tokens 必须为 0，access_token_ttl_sec 默认 600，refresh_token_ttl_sec 默认 604800，redirect_uris 精确白名单。
- [x] 7.3 实现 /api/v1/grants 查询、按 user/client/status 过滤和 /grants/{id}/revoke 吊销。
- [x] 7.4 实现 /api/v1/login-attempts 与 /api/v1/audit-logs 分页、过滤、排序白名单和统一返回体。
- [x] 7.5 实现 /menus/**、/roles/**、/users/{id}/roles 的菜单、角色、用户角色、角色菜单管理接口。
- [x] 7.6 实现 /me/menus 与 /me/permissions，按 RBAC 返回当前操作员菜单树和权限集。
- [x] 7.7 为管理写接口补齐 @Idempotent、@RateLimit、Service 事务、审计字段填充和 RBAC 权限校验。
- [x] 7.8 实现 AuthErrorCodeEnum，将系统号和业务号登记到 docs/error-code-ranges.md 后再接入全局异常处理。

## 8. 前端实现

- [x] 8.1 实现 auth-portal 的账密登录页、失败/锁定提示、SSO 会话 Cookie 和不泄露账号存在性的文案。
- [x] 8.2 实现 auth-admin 的 Client 列表、详情编辑、创建、启停和 secret 创建/重置页面。
- [x] 8.3 实现 auth-admin 的 grant/会话查询、踢下线、登录审计和安全审计查询页面。
- [x] 8.4 实现 auth-admin 的菜单、角色、用户角色、角色菜单管理页面和当前操作员菜单/权限展示。
- [x] 8.5 保证前端只调用 src/api，id 使用 string，统一解包 Result，排序和枚举值取后端契约。
- [x] 8.6 运行 npm run lint 与 npm run type-check，清理 console.log 和未使用代码。

## 9. 验证、部署与收尾

- [x] 9.1 运行 mvn -q compile，确认后端编译通过，修复所有编译错误。
- [ ] 9.2 在 local 配置下冒烟验证发现文档、登录、授权码换票、PKCE、刷新轮转、revoke、JWKS 验签和 userinfo。
- [ ] 9.3 在 local 配置下验证 Client CRUD、secret 重置、grant/会话踢下线、登录审计、安全审计和 RBAC 权限边界。
- [ ] 9.4 验证 Redis 丢失/恢复行为符合风险设计，RT/会话失效但凭证和台账可恢复。
- [x] 9.5 准备 Nginx 配置，托管 auth-portal/auth-admin 静态资源并反代 /api、/oauth2，按 docs/test-env.md 校对信任域名。
- [x] 9.6 整理部署步骤、health 检查、人工执行 SQL 的前置征询和回滚说明。
- [ ] 9.7 自查无生产密钥入仓、无完整 Token/密码日志、无调试残留，且所有任务完成状态已勾选。

