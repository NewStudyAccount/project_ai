# Design: add-unified-auth-center

## Context

- 仓库为空脚手架，约定为多系统容器（`frontend/<system>` + `backend/<system>`），链路 Nginx → 系统网关 → 业务服务，双重鉴权（网关 AuthN、服务 AuthZ）。
- Explore 已拍板：每系统独立网关与独立库；认证中心单独服务且**只做认证**、支持 SSO；**权限各系统自管**；认证**不挂网关**；子系统配权限需引用用户但**不存完整账号**。
- 后续拍板：认证中心 **单体部署**（一个 Spring Boot 进程，不再拆 user/sso/token 微服务）；采用 **自研轻量认证**（业务协议自研，密码学/JWT/BCrypt 用成熟库），**非** Keycloak/CAS 等外购 IdP，**非** 本期 OAuth2/OIDC 全量。
- 技术栈固定：Vue3 + Element Plus；Java 21 + Spring Boot/Security + MyBatis-Plus + Spring Cloud Gateway（仅业务系统）+ Nacos + MySQL + Redis + Nginx。
- 约束：禁止物理外键；Long 主键 14 位；统一 `{code,msg,data}`；生产密钥不入仓；跨服务禁止直读对方库。

## Goals / Non-Goals

**Goals:**

1. 固定认证中心职责与 SSO 形态（Redis 会话 + 各系统 JWT）。
2. 固定认证中心 **单体 + 自研轻量** 的搭建方式与内部分层。
3. 固定每系统独立网关/独立库，以及 JWT 验签的共享方式。
4. 固定本地 RBAC 表边界：权限在业务库，用户仅 `user_id` 引用。
5. 固定用户权威库与子系统轻量投影 `sys_user_ref` 的同步策略。
6. 给出项目基础目录与落地顺序，可直接进入脚手架与迁移。

**Non-Goals:**

- 不为认证中心建网关。
- 不把认证拆成多个微服务进程（user/sso/token 独立部署）。
- 不引入 Keycloak、CAS、Authing 等外购 IdP 产品。
- 不做子系统用户账号副本（无密码库）。
- 不做跨系统共享业务库或直读 auth 库。
- 本期不上 OAuth2/OIDC 全量、不上 MQ 用户变更广播、不做复杂数据范围引擎（role 上可留 `data_scope`）。
- 不实现具体业务系统功能。

## Decisions

### D0. 认证中心：单体部署 + 自研轻量协议

| 维度 | 决定 |
|------|------|
| 部署 | **单体**：唯一可运行进程 `auth-service`；可选 `auth-api` 仅为 Feign 契约 jar，不是服务 |
| 应用结构 | 一个 Spring Boot 应用内分层包：Credential / SsoSession / Token / UserQuery（+薄 Controller） |
| 实现方式 | **自研轻量认证**：账号模型、登录/登出/改密、sid+code SSO、令牌策略、用户只读 API 为自研 |
| 直接用库 | BCrypt（Spring Security）、JWT 签名验签（nimbus/java-jwt）、Redis 客户端、MyBatis-Plus |
| 明确否决 | 外购 IdP（Keycloak/CAS/Authing）；认证微服务化；手写密码学/JWT 规范；本期 Spring Authorization Server / OAuth2 全量 |

**选型对照：**

| 路线 | 结论 |
|------|------|
| A 全手搓 | 否——不自研加密原语 |
| **B 自研业务 + 安全库** | **是**——与单体、只认证、栈内 Spring Security 匹配 |
| C Spring Authorization Server | 否（本期）——无多方第三方客户端时过重；表结构可预留演进 |
| D Keycloak 等 IdP | 否——运维与边界与「单体自研、少组件」错位 |

**演进：** 若日后接入第三方应用，可在同一单体上加 OAuth2 授权码端点或抽 sso 模块，无需先上 IdP 产品。

### D1. 认证中心独立服务、只做认证、不经网关

| 项 | 决定 |
|----|------|
| 形态 | 独立单体 `auth-service`（见 D0），独占 `auth_db` |
| 入口 | Nginx 反代（如 `/auth` 或 `auth.` 域名）**直达** auth-service |
| 职责 | 凭证校验、SSO 会话、JWT 签发/刷新/吊销、用户只读查询、改密/停用（账号安全） |
| 禁止 | 业务 RBAC、菜单/角色管理、经系统网关路由 |

**否决：** 为 auth 再挂 gateway（无多服务路由收益）；把认证编译进某业务系统（无法 SSO 共享）。

### D2. 每系统独立网关 + 独立库；JWT 验签组件共享

- 每业务系统：`{system}-gateway` + 若干 `*-service` + 自己的 MySQL 库。
- 网关只验 JWT（同一 `iss`/JWK）、透传 `uid`；细粒度授权在服务。
- **共享的是验签构件**（`common` 内 security/jwt 模块或极薄 starter），不是共享网关进程；避免 N 份过滤器漂移。

**否决：** 平台超级网关（与「系统独立」冲突）；各网关手写不同验签。

### D3. SSO：中心 Redis 会话 sid + 各系统 JWT

```
未登录访问系统 A → 跳 auth /sso/authorize?return=A
  → 有 sid：签发 A 用 JWT → 回跳
  → 无 sid：登录页 → 建 sid → 签发 JWT → 回跳
访问系统 B → 同上，有 sid 则免密
```

| 令牌 | 存储 | 有效期 |
|------|------|--------|
| SSO `sid` | Redis + Cookie（父域或 auth 域回跳） | 可配置，长于 Access |
| Access JWT | 客户端 | 30 分钟 |
| Refresh | Redis，可轮转 | 7 天 |

登出/改密：删 sid、吊销 refresh；Access 靠短 TTL 或黑名单。

**否决：** 本期完整 OIDC 授权码多方客户端（内部自有前端可用登录+回跳 code/token 换 JWT）；共享 Session 进各系统网关。

### D4. 权限完全本地于各业务系统

每系统库独立拥有：

- `sys_role`、`sys_permission`（menu/button/api）、`sys_user_role`、`sys_role_permission`
- `system_code` 作为系统标识写在角色/权限上（便于管理端过滤；库已按系统拆时冗余但防串）
- `sys_user_role` **只存 `user_id`**，逻辑指向认证中心用户；无物理 FK

鉴权：`JWT.uid` + 本地 `user_role` → `role_permission` → `permission_code`；网关不做权限码清单。

**否决：** 中心统一角色库（与「权限各系统自管」矛盾）；子系统存完整 `sys_user`。

### D5. 用户权威只在 `auth_db`；子系统仅引用 + 可选投影

| 数据 | 位置 | 写者 |
|------|------|------|
| 账号、密码哈希、status、档案 | `auth_db.sys_user` | 仅认证中心 |
| 组织部门（企业统一） | `auth_db.sys_dept`（建议） | 仅认证中心 |
| `user_id` 引用 | 各系统 `sys_user_role` | 各系统 |
| `sys_user_ref` 投影 | 各系统库（可选） | 各系统**只读同步**，无密码 |

**用户库是否独立：** 逻辑上独立（身份 BC）；**物理上与认证同库、仅 auth-service 读写**，不为 user 单拆服务/库。  
**拆出 `user_db`/`user-service` 的触发条件：** HR/复杂组织异动、多产品线复用员工中心、用户读压力独立。届时再拆，表结构按身份核字段预留即可。

**否决：** 多服务直读用户表；每系统完整用户副本。

### D6. 投影同步：三条触发，无 MQ

| 触发 | 行为 |
|------|------|
| 某用户 SSO/JWT 进入系统 | 该系统 `UPSERT sys_user_ref` |
| 赋权保存 / 批量查人 | 快照 upsert 对应 `user_id` |
| 成员列表缺行 | `batchGet` 中心 API 后补洞 |

不变式：

- `sys_user_role` 成功 **不依赖** ref upsert 成功。
- **禁止**用 `sys_user_ref.status` 做放行；停用以 token/登录为准。
- 选人搜索 **实时** 调认证中心，不靠本地全量投影。

**延后：** MQ `user.changed`、定时全量对账（系统 ≥3 或组织高频变更再上）。

### D7. 表结构要点（迁移输入）

**`auth_db`（仅 auth）：** `sys_sequence`、`sys_user`（含 `password_hash`）、`sys_dept`、登录/SSO 会话日志可选。  
**各业务库：** `sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、可选 `sys_user_ref`、业务表；ID 可用各库 `sys_sequence` 或统一发号服务（首期各库序列即可）。

通用规范（对齐 CLAUDE.md §6.4–6.5）：

- 主键 14 位：`yyMMdd(6)+seq(8)`，禁止自增
- 审计五件套：`create_time/update_time/create_by/update_by/deleted`
- 表名单数 `snake_case`；索引 `idx_表_字段` / `uk_表_字段`；单表 ≤5；无物理 FK
- 中间表删除优先物理删，避免 UK 与逻辑删除冲突
- 密码 BCrypt；Long 出参序列化为字符串

`sys_user_ref` 最小字段：`user_id` PK、`username`、`real_name`、`status`、`dept_id`/`dept_name`、`sync_time`。

### D8. 项目基础结构

```
project_ai/
├── frontend/
│   ├── auth-portal/          # 【必需】登录/SSO 入口 UI（Vue3+Element Plus）
│   └── <system>/
├── backend/
│   ├── auth/
│   │   ├── auth-api/         # 可选：Feign 契约 jar（非独立进程）
│   │   └── auth-service/     # 单体可运行进程；无 auth-gateway
│   └── <system>/
│       ├── <system>-common/  # 契约 + JWT 验签 + 发号
│       ├── <system>-gateway/
│       └── <system>-*-service/
├── deploy/nginx|compose/
├── docs/
└── openspec/
```

单体内部建议包结构（逻辑分层，非多模块微服务）：

```
auth-service/controller/   Auth · Sso · UserQuery · AccountSecurity
auth-service/service/      Credential · SsoSession · Token · UserQuery
auth-service/mapper|entity|config|constants|enums|util
```

落地顺序：common 契约/发号 → 单体 auth-service（登录令牌再 SSO）→ Nginx `/auth` → 业务系统 gateway 验签 starter → 首个系统 RBAC 与 user_ref → 其他系统复制模式。

### D10. auth-portal 前端（必需交付，与 auth-service 配对）

| 项 | 决定 |
|----|------|
| 路径 | `frontend/auth-portal` |
| 技术 | Vue3 + Vite + TypeScript + Element Plus（与仓库前端栈一致） |
| 职责 | 登录表单、错误提示、登出、SSO `client_id`/`return_url`、回跳 `code` 换令牌 |
| 非职责 | 业务工作台、完整账号管理后台（后置） |
| 令牌 | Access/Refresh 由前端按策略保存；sid 为服务端 HttpOnly Cookie；禁止把密码写入 localStorage |

**否决：** 只交付 HTTP API 无登录 UI；用业务系统登录页代替统一入口。

### D9. 登录与选人 API 边界

- 认证：`POST /auth/login`、`/refresh`、`/logout`、SSO authorize/token、`GET /auth/users`、`GET /auth/users/batch`、改密/停用。
- 系统 A：角色/权限 CRUD、`user_role` 绑定、`GET /menus`（menu/button）、方法级鉴权。
- Feign：业务 → 认证仅用户查询；**禁止**业务写 auth 库、禁止认证读业务 RBAC 表。

## Risks / Trade-offs

- [自研 SSO 协议不如 OIDC 标准化] → 仅服务自有前端；code 一次性+绑 client_id+return_url 白名单；预留日后 OAuth2 演进。
- [单体功能膨胀成 UPMS] → 禁止角色/菜单模块进 auth；改密/停用仅算账号安全。
- [多网关 JWT 配置漂移] → 验签收敛进 common/starter；密钥与 `iss` 走 Nacos 统一配置。
- [投影显示旧姓名/旧部门] → 可接受；补洞+登录 upsert；不用投影做鉴权。
- [中心宕机不能登录/选人] → 已登录靠短 JWT；授权本地仍可；与「身份权威在中心」一致。
- [SSO Cookie 域与前端跨域] → 优先同父域或 auth 独立域+回跳 code；避免把 Refresh 放进可被 XSS 读的位置。
- [各库各 sequence 导致 ID 语义仅库内单调] → 14 位设计本就按日发号；跨系统只需稳定 `user_id`（来自中心）。
- [改密/停用放 auth 是否算“业务”] → 划为账号安全 API；角色管理留在各系统，避免 auth 变 UPMS。

## Migration Plan

1. 初始化 `auth` 库表 + 测试种子管理员（test/local）。
2. 部署**单体** `auth-service` + Redis（sid/refresh）+ Nginx `/auth`（不经业务网关）。
3. 首个业务系统：common 验签 starter、gateway、RBAC 表、`sys_user_ref`、角色管理与选人。
4. 验证 SSO 进两系统、停用即失效、赋权不依赖投影。
5. 回滚：Nginx 摘除路由；库表版本化 down；密钥按环境隔离。

## Open Questions

1. SSO Cookie：公司统一父域 `.example.com` 还是 auth 独立域 + 回跳？（部署域名定了即可关掉。）
2. 首个业务系统 `system_code` 与中文名。
3. JWT claims 是否带 `real_name`/`dept_id`（减少首屏查人）还是仅 `uid`+`username`。
4. 账号管理 UI 是否并入未来 Admin 系统，或仅提供 API 由运维脚本建号。
5. JWT 算法首期 HS256 对称密钥，或直接上 RSA/EC + JWK（多系统验签更干净，配置略重）。
