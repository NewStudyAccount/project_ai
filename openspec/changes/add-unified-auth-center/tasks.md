# Tasks: add-unified-auth-center

## 1. 工程与数据层

- [x] 1.1 创建 `backend/auth` Maven 多模块：`auth-common`（Result/错误码/异常/分页契约，禁 MyBatis-Plus/Redis）→ `auth-framework`（MyBatis-Plus/Redis/Swagger/ID 与审计/Long 序列化）→ `auth-service`（可运行，三份 `application*.yml`）
- [x] 1.2 `auth_db` 迁移脚本（`deploy/db/migration/auth_db/`）：`sys_sequence`、`sys_credential`、`oauth_client`、`auth_session`、`auth_grant`、`auth_refresh_token`、`login_attempt`、`auth_audit_log`；审计字段与 14 位发号对齐 CLAUDE.md §6.4.5/§6.5
- [x] 1.3 SAS 协议存储表（如 `oauth2_authorization`）迁移；`oauth_client` → `RegisteredClientRepository` 适配
- [x] 1.4 配置三文件与 Nacos/本地 profile；连接信息与 `docs/test-env.md` 一致；生产密钥不入仓

## 2. 用户中心契约与凭证

- [x] 2.1 跨系统契约模块（如 `user-api`）：用户中心 Feign 接口 + Fallback + DTO（by-username / by-id / profile）；禁止 Entity、禁止 RestTemplate
- [x] 2.2 `sys_credential` 读写：初始化凭证 API（供用户中心建号）、BCrypt/Argon2 校验；事务外调 Feign
- [x] 2.3 登录解析 `username → user_id + status`，短 TTL 状态缓存；用户中心不可用时 503 语义

## 3. OIDC 协议内核（SAS）

- [x] 3.1 接入 Spring Authorization Server：`/oauth2/authorize`、`/oauth2/token`、`/oauth2/jwks`、`/oauth2/revoke`、`/oauth2/userinfo`、`/.well-known/openid-configuration`
- [x] 3.2 自定义 `OAuth2RefreshTokenGenerator`（路线 1：public + authorization_code 发 RT）；强制 PKCE S256；RT 轮转与重用检测吊销全链
- [x] 3.3 TokenCustomizer：Claims 最小集（`iss,sub,aud,exp,iat,jti,auth_time,preferred_username`），`sub` = 用户中心 `user.id`；userinfo 走用户中心 profile
- [x] 3.4 `redirect_uri` 精确白名单、`state`/`nonce` 校验；AT 默认 600s、RT 默认 7 天可配

## 4. 登录与 SSO

- [x] 4.1 账密登录流程：formLogin → 用户中心解析 → 本地验 `sys_credential` → 建 `auth_session` → 继续发码
- [x] 4.2 SSO 会话生命周期（过期/吊销/同域续权）；Cookie 强制 HTTPS、SameSite 按跨站 redirect 配置；令牌仅存哈希
- [x] 4.3 `login_attempt` 记录与失败限制；防撞库文案；`auth_audit_log` 记录 LOGIN/TOKEN_ISSUE/REFRESH/REVOKE/CLIENT_UPDATE（detail 禁密码/完整 token）

## 5. 客户端运营与踢下线

- [x] 5.1 Client CRUD / 启停 / 重置密钥（secret 仅创建/重置明文一次，库内存哈希）管理 API（`{code,msg,data}` + 分页）
- [x] 5.2 `auth_grant` + `auth_refresh_token` 台账；按用户/客户端踢下线（先 SAS 失效再更新台账）
- [x] 5.3 登录审计与安全审计分页查询 API（按时间/用户/IP/`client_id`/action 筛选）

## 6. 前端

- [x] 6.1 `frontend/auth-portal`：Vue3 + TS + Pinia + Element Plus 登录页（账密、失败/锁定提示；无注册/找回）；对接 SAS 登录流程
- [x] 6.2 `frontend/auth-admin` 骨架：**独立 public + PKCE（S256）OIDC Client** 登录（路线 1，AT/RT）；路由 meta `title/icon/hidden/requiresAuth`；axios 解包 `{code,msg,data}`，`id` 为 string
- [x] 6.3 auth-admin 页面：客户端列表/详情编辑、新建/重置密钥、令牌与会话（踢下线）、登录/安全审计查询
- [x] 6.4 前端自检：`npm run lint && npm run type-check && npm run test`

## 7. 安全与验证

- [x] 7.1 后端单测：PKCE 失败、code 重放、RT 重用吊销、凭证校验、redirect_uri 白名单；`mvn test`
- [x] 7.2 确认无生产密钥入仓、无密码/完整 token 进日志、Entity 仅在 `auth-service`、无 Controller→Mapper、无事务内 Feign
- [ ] 7.3 集成冒烟：SPA 授权码换票 → RS 验 JWT → 刷新轮转 → 踢下线后刷新失败；连接信息见 `docs/test-env.md`
