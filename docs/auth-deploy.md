# 统一认证中心部署说明

> 对应 unified-auth-center 任务 9.6 / Migration Plan。生产执行 SQL、改依赖锁文件前必须按 `CLAUDE.md` §7 征询。

## 1. 组件

| 组件 | 说明 |
|------|------|
| `auth-service` | Spring Boot 单体，端口 `9080`，无网关、无 Nacos |
| `frontend/auth-portal` | 登录页静态资源 |
| `frontend/auth-admin` | 运营后台静态资源 |
| MySQL `auth_db` | 凭证、Client、台账、审计、RBAC |
| Redis（DB 3） | Token/会话/限流/幂等，见 `deploy/redis/auth-redis.md` |
| 用户中心 `user-service` | Feign 直连 `user.feign.user-center.base-url` |
| Nginx | 见 `deploy/nginx/auth-center.conf` |

## 2. 部署步骤

1. **准备数据层**（人工执行，生产前征询）
   - 创建库：`auth_db`（utf8mb4 / InnoDB）
   - 依次执行 `deploy/db/migration/auth_db/01_auth_tables.sql`、`02_auth_rbac.sql`
   - 确认 Redis AOF everysec + RDB 已开启
2. **配置**
   - `application-local.yml` / `application-test.yml` 对齐 `docs/test-env.md`（MySQL/Redis/`user-center.base-url`/CORS）
   - 生产敏感项走 Nacos/密钥管理/Jenkins Credentials，禁止入仓
3. **构建**
   ```bash
   cd backend/auth && mvn -q clean package -DskipTests
   cd frontend/auth-portal && npm ci && npm run build
   cd frontend/auth-admin && npm ci && npm run build
   ```
4. **启动**
   ```bash
   java -jar auth-service/target/auth-service-*.jar --spring.profiles.active=test
   ```
5. **健康检查**
   - `GET http://<host>:9080/actuator/health` 为 `UP`，且 MySQL/Redis 连接正常后再接入流量
6. **Nginx**
   - 使用/合并 `deploy/nginx/auth-center.conf`：托管 portal/admin 静态，反代 `/api/`、`/oauth2/` 到 `127.0.0.1:9080`
   - 信任域名与 `docs/test-env.md`、`auth.security.allowed-origins` 一致

## 3. 冒烟清单

- `GET /.well-known/openid-configuration`
- 账密登录（portal）→ SSO Cookie
- 授权码 + PKCE S256 换票、`redirect_uri` 精确匹配
- Refresh Token 刷新轮转；旧 RT 重用触发全链吊销
- `GET /oauth2/jwks` 离线验签；`GET /oauth2/userinfo`
- `POST /oauth2/revoke`；管理端 grant/用户踢下线
- Client CRUD / secret 重置（明文仅一次）
- 登录审计、安全审计、RBAC 菜单/权限

## 4. 回滚

- 下线 `auth-service` 与 Nginx 站点
- 保留 `auth_db` 与 Redis 快照，**不做**不可逆 migration
- 业务系统仍可用已缓存 JWKS 验签短 TTL Access Token；强制失效需等 AT 过期或走 revoke
