# 测试环境信息

> 用途：记录**测试环境**的基础接入信息，便于开发、联调、Jenkins 发布。
> 范围：仅测试环境。生产环境另册，且更严格。
>
> **密钥分级（与 `CLAUDE.md` §6.7 一致）：**
> - **生产**密码、私钥、Token：**禁止**写入本文件或任何仓库文件。
> - **测试 / 开发 / 本地**账号密码：**允许**写入本文件，便于联调与建流水线。
> - 必须标明环境；禁止测试与生产混用同一账号。
> - 本文仅测试环境；建议只存放于内网仓库。

最后更新：2026-09-24　|　维护人：Codex（unified-auth-center）

> **端口规划与查询**见 [docs/port-registry.md](port-registry.md)（唯一端口登记处）；本文件负责组件接入与测试账密。

---

## 1. 服务器

| 角色 | 主机名 | 内网 IP          | 外网/跳板 | 端口 | 部署内容 | SSH 用户 | 备注 |
|------|--------|----------------|-----------|------|----------|----------|------|
| 应用服务器 | （填写） | 192.168.99.100 | （如有） | 22 | 业务 jar / Docker | （用户名） | 测试密码见 §6 |
| 前端/Nginx | （填写） | （填写）           | （如有） | 80/443 | 静态 + 反代 | （用户名） | |
| Jenkins | （填写） | （填写）           | （如有） | 8080 | CI/CD | — | 测试账号可写 §6；生产 Jenkins 禁止写入 |
| 跳板机 | （填写） | （填写）           | （如有） | 22 | 运维入口 | （用户名） | |

### 用户中心（scaffold-user-center）

| 系统 | 环境 | 域名 | 应用端口 | 备注 |
|------|------|------|----------|------|
| user-admin | test/local | user-admin.example.local | 5173 | Nginx 托管前端并反代 /api 到 user-gateway |
| user-gateway | test/local | — | 8173 | 网关入口（鉴权唯一归属） |
| user-service | internal | — | 18173 | 仅服务间可达，网关不对外路由 /internal |

### 统一认证中心（unified-auth-center）

| 系统 | 环境 | 域名 | 应用端口 | 备注 |
|------|------|------|----------|------|
| auth-portal | test/local | auth-portal.example.local | 5174 | Nginx 托管登录页；本地 Vite 代理 `/api`、`/oauth2` 到 9080 |
| auth-admin | test/local | auth-admin.example.local | 5175 | Nginx 托管运营后台；本地 Vite 代理 `/api` 到 9080 |
| auth-service | test/local | auth.example.local | 9080 | OIDC 端点与 `/api/v1` 管理 API；信任 origin 见下方登记 |

信任域名（auth-service CORS）：`http://localhost:5173`、`http://localhost:5174`、`http://localhost:5175`、`http://127.0.0.1:5173`、`http://127.0.0.1:5174`、`http://127.0.0.1:5175`、`https://user-admin.example.local`、`https://auth-portal.example.local`、`https://auth-admin.example.local`。

**单点登录访问约定（强制）：** 浏览器请统一使用 **`http://localhost:5173|5174|5175`** 与 IdP **`http://localhost:9080`**。Cookie 以 host 为准（忽略端口），**禁止混用 `127.0.0.1`**——否则 SSO Cookie 不会带到 authorize，单点失效。issuer / 令牌 `iss` 为 `http://localhost:9080`。

OIDC RP（unify-login-facade）：

| client_id | 前端 | redirect_uri（精确白名单） | post_logout_redirect_uri |
|-----------|------|---------------------------|--------------------------|
| `user-admin-spa` | user-admin:5173 | `http://127.0.0.1:5173/callback`、`http://localhost:5173/callback`、`https://user-admin.example.local/callback` | 同源 `/logged-out` |
| `auth-admin-spa` | auth-admin:5175 | `http://127.0.0.1:5175/callback`、`http://localhost:5175/callback`、`https://auth-admin.example.local/callback` | 同源 `/logged-out` |
| `auth-portal-spa` | auth-portal:5174 | 既有种子；**portal 登录不走 code 流**（IdP Cookie） | `http://127.0.0.1:5174/` 等 |

`post_logout_redirect_uri` 允许列表配置在 `auth-service`（`auth.security.post-logout-redirect-uris`），不改 `oauth_client` 表。

---

## 2. 中间件与数据层

### 2.1 MySQL

| 用途 | 主机             | 端口 | 实例/库名 | 用户名   | 密码       | 字符集 | 备注 |
|------|----------------|------|-----------|-------|----------|--------|------|
| 测试主库 | 192.168.99.100 | 3306 | （库名） | root  | 123456   | utf8mb4 | 按系统/服务拆库时一行一个库 |

| 用途 | 主机 | 端口 | 实例/库名 | 用户名 | 密码 | 字符集 | 备注 |
|------|------|------|-----------|--------|------|--------|------|

| 认证中心测试库 | 192.168.99.100 | 3306 | auth_db | root | 123456 | utf8mb4 | SQL：deploy/db/migration/auth_db/ |


### 2.2 Redis

| 用途 | 主机             | 端口 | DB 序号 | 用户名  | 密码     | 备注 |
|------|----------------|------|-------|------|--------|------|
| 测试缓存 | 192.168.99.100 | 6379 | 2     | root | 123456 | |

| 认证中心缓存 | 192.168.99.100 | 6379 | 3 | root | 123456 | authorization / refresh-token / sso-session |

### 2.3 Nacos

| 用途 | 主机/地址          | 端口 | 控制台用户 | 命名空间 | 分组 | 密码    | 备注 |
|------|----------------|------|-------|----------|------|-------|------|
| 注册+配置 | 192.168.99.100 | 8848 | nacos | （如 test） | （如 DEFAULT_GROUP） | nacos | |

### 2.4 对象存储 / 镜像仓库（如有）

| 类型 | 地址             | 项目/Bucket | 用户名 | 密码/密钥 | 备注 |
|------|----------------|-------------|--------|-----------|------|
| OSS/MinIO | 192.168.99.100:9000 | file-bucket-test | minio-test | minio-test-secret（测试用可写；生产密钥禁止） | file-service 用；生产密钥走 Nacos/密钥管理，禁止入仓 |

---

## 3. 网关与域名

| 系统 | 测试域名 | 协议 | Nginx 服务器 | 反代目标 | 备注 |
|------|----------|------|--------------|----------|------|
| user-admin | user-admin.example.local | http | 192.168.99.100 | user-gateway 127.0.0.1:8173 | 信任域名：http://localhost:5173、http://127.0.0.1:5173、https://user-admin.example.local |

| 系统 | 测试域名 | 协议 | Nginx 服务器 | 反代目标（网关） | 备注 |
|------|----------|------|--------------|------------------|------|
| （system） | （如 test-admin.example.local） | https | （主机名/IP） | （网关 IP:端口） | 路径 `/api` → 网关 |

---

## 4. Jenkins 测试发布

| 项 | 值 |
|----|-----|
| Jenkins URL | （填写） |
| 常用 Job | 见 `docs/jenkins-pipeline-guide.md` §7 登记表 |
| `DEPLOY_ENV` | `test` |
| Spring profile | `test` |
| 健康检查 | `http://<服务>:<端口>/actuator/health` |

---

## 5. 连接方式（约定）

1. 公网不直接暴露数据库/Redis/Nacos；办公网或 VPN + 跳板访问。
2. 连接串拼装规则（测试密码可用上表；生产密码从密钥管理注入，**不写进仓库**）：
   - MySQL：`jdbc:mysql://<host>:<port>/<db>?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai`
   - Redis：`redis://<host>:<port>/<db>`
3. 本地开发用 `application-local.yml`（profile `local`；个人差异见 `docs/test-env.local.md`，已 gitignore）；测试环境用 `application-test.yml`，由 Jenkins/运维注入。

---

## 6. 测试账号密码汇总（允许写入） / 生产密钥（禁止写入）

### 6.1 测试 / 开发 / 本地（可写在本仓库）

| 资源 | 环境 | 用户名   | 密码     | 备注 |
|------|------|-------|--------|------|
| SSH | test | （用户名） | （填写）   | |
| MySQL | test | root  | 123456 | |
| Redis | test | root  | 123456 | |
| Nacos | test | nacos | nacos  | |

### 6.2 生产（禁止写入本仓库）

| 资源 | 存放位置（Nacos / 密钥管理 / Jenkins Credentials） | 谁有权限 |
|------|-----------------------------------------------------|----------|
| 生产 SSH / 数据库 / 中间件密码 | （条目名，**不写值**） | （角色） |
| 生产 Token / AccessKey | （条目名，**不写值**） | （角色） |

> 个人可选本地副本 `docs/test-env.local.md`（gitignore）用于存放不便共享的密钥，**非必须**。

**本地密文文件（已忽略，勿改名为可提交格式）：**

- 路径：`docs/test-env.local.md`（或 `docs/secrets/test-env.local.md`）
- 已在 `.gitignore` 排除；只存本机，不进 PR、不进截图外传。
- 模板见同目录 `docs/test-env.local.example.md`。

---

## 7. 变更记录

| 日期 | 变更 | 变更人 |
|------|------|--------|
| YYYY-MM-DD | 建立文档结构 | — |
| 2026-09-24 | 登记 user_db、user-gateway/user-service 端口、Nginx 信任域名 | scaffold-user-center |
| 2026-09-24 | 登记 auth-portal/auth-admin/auth-service 端口、测试域名与 CORS 信任域名 | unified-auth-center |
| 2026-09-24 | 登记 OIDC RP client、redirect/post-logout 白名单与 user-admin 信任域名 | unify-login-facade |

