# 测试环境信息

> 用途：记录**测试环境**的基础接入信息，便于开发、联调、Jenkins 发布。
> 范围：仅测试环境。生产环境另册，且更严格。
>
> **密钥分级（与 `CLAUDE.md` §6.7 一致）：**
> - **生产**密码、私钥、Token：**禁止**写入本文件或任何仓库文件。
> - **测试 / 开发 / 本地**账号密码：**允许**写入本文件，便于联调与建流水线。
> - 必须标明环境；禁止测试与生产混用同一账号。
> - 本文仅测试环境；建议只存放于内网仓库。

最后更新：2026-10-14　|　维护人：Codex（unified-auth-center）

> **端口规划与查询**见 [docs/port-registry.md](port-registry.md)（唯一端口登记处）；本文件负责组件接入与测试账密。

---

## 1. 服务器

| 角色 | 主机名 | 内网 IP          | 外网/跳板 | 端口 | 部署内容 | SSH 用户 | 备注 |
|------|--------|----------------|-----------|------|----------|----------|------|
| 应用服务器 | （填写） | 192.168.99.100 | （如有） | 22 | 业务 jar / Docker | （用户名） | 测试密码见 §6 |
| 前端/Nginx | （填写） | （填写）           | （如有） | 80/443 | 静态 + 反代 | （用户名） | |
| Jenkins | （填写） | 192.168.99.100 | （如有） | 8080 | CI/CD | — | 测试账号见 §6.1；生产 Jenkins 禁止写入 |
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

> **取值真相源：** 以各系统 `application-local.yml` 中 `blog.object-storage.providers[]` 配置为准同步本表；生产密钥走 Nacos / 密钥管理，**禁止入仓**。

| 类型 | 地址 | 项目/Bucket | 用户名（AccessKey） | 密码/密钥（SecretKey） | 备注 |
|------|------|-------------|---------------------|------------------------|------|
| OSS/MinIO | http://192.168.99.100:9000 | blog | Nj8dcWqsI4bG2mFH16DD | v9vEyjmfvWuRcq77H3nnZifqwvaPTTvftDqCqgOj（测试用可写；生产密钥禁止） | blog 系统（`blog.object-storage.providers[code=minio-local]`）；与 `application-local.yml` / `application-test.yml` 一致；已验证 put/get/delete。**公开读基址** `public-base-url=http://192.168.99.100:9000/blog`（桶 `blog` 需**公共读**，匿名 GET 200 / 匿名 PUT 失败；无生产密钥） |

> **注：** SK 加密主密钥体系已移除（变更 `blog-object-storage-remove-sk-encryption`）；dev/test 阶段 SK 明文落库 `sys_storage_provider.secret_key`；生产部署前须另开独立加密变更重引入加密层。

### 2.5 Elasticsearch

> 知识库域专用（`docs/knowledge-base-design/`）；版本基线见 `docs/version-baseline.md`；index 边界限定 `knowledge_chunk_v1`，不用于 blog 业务检索。

| 用途 | 主机 | 端口 | 版本 | 用户名 | 密码 | 备注 |
|------|------|------|------|--------|------|------|
| 知识库检索 | 192.168.99.100 | 9200 | **9.5.5**（基线锁定） | elastic | （已关闭 xpack security，无密码） | 单机单节点（discovery.type=single-node）；cluster=`knowledge-es`，node=`es-node-1`；ES_JAVA_OPTS=`-Xms2g -Xmx2g`；**ik 分词器 9.5.5（随 ES 严格对齐，test-env.md P0 满足）**；数据/日志/插件挂载 `/opt/elasticsearch/{data,logs,plugins}`；容器名 `elasticsearch`，加入 `1panel-network`；index 边界限定 `knowledge_chunk_v1` |
| ES 管理面板 | 192.168.99.100 | 5601 | **9.5.5**（与 ES 严格对齐） | （无认证） | （无密码） | Kibana 容器名 `kibana`，compose 在 `/opt/kibana/`；中文界面 `I18N_LOCALE=zh-CN`；通过 `http://elasticsearch:9200` 连同网 ES；与 ES 同 `1panel-network`；承载索引管理/Dev Tools/Discover |

> ⚠️ **版本升级待执行（2026-10-28 用户裁决）**：版本基线已锁定 **ES 9.5.5**（见 `docs/version-baseline.md`），本表版本列按基线更新为 9.5.5；~~测试环境容器当前实际仍为 8.14.3~~ **已升级完成（2026-10-29）**：ES 服务端 9.5.5 运行中；analysis-ik 9.5.5 已于 2026-10-29 经 `elasticsearch-plugin install`（Infini 官方源）容器内安装并重启生效（`_cat/plugins` 含 analysis-ik、`_analyze` ik_smart 校验通过）。Kibana 9.5.5 升级仍待执行（当前未运行，不影响后端链路）。升级后校验：`GET http://192.168.99.100:9200` 返回 `version.number=9.5.5`、`_cat/plugins` 显示 `analysis-ik 9.5.5`。
> ES 8/9 默认 xpack 安全；测试环境已关闭（`xpack.security.enabled=false`），测试免密连接 `http://192.168.99.100:9200`；生产部署前须启用安全并设独立密码。ES 启动需要 `vm.max_map_count≥262144`，已通过 `/etc/sysctl.d/99-elasticsearch.conf` 持久化。
> **SiliconFlow 云 API（知识库 RAG）：** Embedding=`BAAI/bge-m3`（dim=1024）、LLM=**`Qwen/Qwen3-8B`**（2026-10-28 用户裁决锁定，便宜档；base_url `https://api.siliconflow.cn/v1`）；`api_key` 经管理端「AI 服务商」页录入或环境注入 `blog.ai.providers[].api-key`，**禁止入仓**。

### 2.5.1 Kibana（ES 管理面板）

| 用途 | 主机 | 端口 | 版本 | 用户名 | 密码 | 备注 |
|------|------|------|------|--------|------|------|
| ES 可视化管理 | 192.168.99.100 | 5601 | **9.5.5**（与 ES 严格对齐） | — | — | 容器名 `kibana`，compose 在 `/opt/kibana/`；与 ES 严格对齐；通过 `http://elasticsearch:9200` 连同网 ES；中文界面 `I18N_LOCALE=zh-CN`；关闭 xpack security 与遥测（容器现 8.14.3，随 §2.5 升级待执行） |

> 访问 `http://192.168.99.100:5601`。**生产部署前须启用 Kibana 自身认证**（与 ES 同步启用 xpack security）。

---

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
| Jenkins URL | `http://192.168.99.100:8080/` |
| 登录用户 | `qjj`（密码见 §6.1） |
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
| Jenkins | test | qjj | Qjj327846@!! | 管理台 `http://192.168.99.100:8080/`；同步见 `jenkins-pipeline-guide.md` |

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
| 2026-10-14 | 登记 Jenkins 测试环境地址与管理台账号 | jenkins-pipeline-guide |
| 2026-10-28 | 回填 ES §2.5 实际部署信息（8.14.3 + ik 8.14.3，单节点 9200，关闭 xpack security） | ES 容器部署 |
| 2026-10-28 | ES 版本基线锁定 **9.5.5**（用户裁决）：§2.5/§2.5.1 版本列更新为 9.5.5（ES/ik/Kibana 随版对齐）；容器现 8.14.3，升级待执行（见 §2.5 注记） | 用户裁决 |
| 2026-10-29 | ES 升级完成回填：ES 9.5.5 运行中，analysis-ik 9.5.5 已装并生效；Kibana 9.5.5 升级待执行 | blog-knowledge-base |
| 2026-10-29 | SiliconFlow LLM 型号锁定 `Qwen/Qwen3-8B`（用户裁决，便宜档），Embedding=`BAAI/bge-m3`（dim=1024）；api_key 经管理端录入不入仓 | blog-knowledge-base |

