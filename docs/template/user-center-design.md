# 用户中心 · 完整设计文档

> 状态：**设计已确认**（2026-09-24，逐项确认稿汇总；取代本目录旧版 `user-center-design.md` / `user-center-database.md` 的内容）
> 范围：用户主数据、账号生命周期、对内用户查询契约、**本系统自持 RBAC**
> 配套：`rbac-design.md`（RBAC 通用 4 表，本库同构一套）、`unified-auth-center-design.md`（认证中心，调用本系统）、`docs/version-baseline.md`（组件版本基线）
> 约束：`CLAUDE.md`（多系统容器、common/framework 分层、Entity 归属、§6.4 基础代码契约、密钥分级、配置 local/test）

---

## 1. 目标与边界

### 1.1 目标

作为全仓多系统的**用户账号权威**：

- 账号创建、启停/锁定、资料维护（16 位发号，`= OIDC sub`）
- 按 id / username 对内查询，供认证中心与业务系统使用
- 本系统（`user-admin`）自持 RBAC：菜单/角色/授权管理（表结构见 `rbac-design.md`）

### 1.2 边界（明确不做）

| 不在本系统 | 归属 |
|------------|------|
| 密码/凭证、登录验密、SSO、令牌、OAuth Client | 认证中心 |
| C 端注册/找回密码 | 不做（可二期另开变更） |
| 组织/部门模型（`dept_id` 仅预留列） | 随组织变更另行设计 |
| 跨系统角色/菜单同步、集中式 RBAC 服务 | 不做（各系统自持，见 `rbac-design.md`） |
| 与认证中心建号/凭证初始化编排 | 后续变更（最终一致） |
| 跨库分布式事务、共享 Entity | 禁止（`CLAUDE.md` §5.6.1） |

**禁止**存储 `password_hash` 或任何凭证密文字段。

---

## 2. 系统关系与调用

```text
  业务系统 SPA/后端          认证中心
        │                     │
        │ CRUD/查询用户        │ 登录：username → user_id + status
        │ 同步投影            │ userinfo：profile
        ▼                     ▼
  ┌──────────────────────────────────┐
  │  用户中心 backend/user            │
  │  user_db（8 表：业务 4 + RBAC 4）  │
  └──────────────────────────────────┘
```

| 调用方 | 接口 | 通道 | 失败策略 |
|--------|------|------|----------|
| 认证中心 | `by-username` / `profile`（登录解析、OIDC 声明） | `user-api` Feign，Nacos 内网直连 | 必须 Fallback；401/503 语义明确 |
| 业务系统 | `by-id` 等（投影） | 同上 | 同上 |
| `user-admin` | 管理 API（用户 + RBAC） | `user-gateway`（JWT 验签） | 正常错误码分流 |

- 跨系统**只**走 `user-api` 契约（DTO/VO + Feign + Fallback，无 Entity/Mapper/实现）；禁止事务内 Feign
- 用户中心**不**调用认证中心验密

---

## 3. 模块划分（Spring Cloud 方式，组件表已确认）

```
backend/user/                       # Maven 多模块，包名 com.qjj.user.*
├── user-common                     # Result/错误码/异常/分页契约/BaseEnum/常量/脱敏工具（零配置纯基础）
├── user-framework                  # 基础设施装配（见下表）
├── user-api                        # 对内契约：DTO/VO + Feign + Fallback（无 Entity）
├── user-service                    # 本域 Entity/Mapper/Service/Controller（可运行，三份 yml）
└── user-gateway                    # 薄边缘治理（见下表）
frontend/user-admin/                # 管理端（Vue3 + TS + Pinia + Element Plus）
```

| 模块 | 组件（版本一律取 `docs/version-baseline.md`，先改表再改 pom） |
|------|------|
| `user-common` | Lombok + 注解类（jakarta.validation / jackson-annotations）；⛔ 禁 MP/Redis/数据源/Actuator/MQ/MinIO/JWT/Hutool |
| `user-framework` | starter-web / validation / aop / actuator / data-redis + Redisson 3.52.0 + MyBatis-Plus 3.5.17（boot3 starter）+ OpenFeign + springdoc 2.9.1 + micrometer-tracing-bridge-brave |
| `user-api` | spring-cloud-starter-openfeign + Lombok |
| `user-service` | user-common/framework/api + mysql-connector-j + MapStruct + nacos-discovery/config（SCA 2025.0.0.0） |
| `user-gateway` | spring-cloud-starter-gateway + nacos-discovery + oauth2-resource-server（验签用 JWKS 公钥，**不引 SAS**）+ actuator |

**Spring Cloud 能力映射**：注册/发现 = Nacos；配置 = Nacos + `application-local/-test.yml`；服务间调用 = OpenFeign + Fallback + 固定超时；边缘 = Gateway（路由 / JWT 验签 / `X-User-*` 注入 / CORS）；链路 = Micrometer Tracing（traceId 进 MDC）；监控 = Actuator。业务域一域一服务（`user-service`）。

**包结构**（`CLAUDE.md` §5.6.2）：`com.qjj.user.{module}` → controller / dto / vo / service / bo / converter（MapStruct 唯一落点）/ mapper / entity / feign / config / constants / enums / util；调用链 Controller → Service → Mapper。

---

## 4. 核心能力

| 能力 | 说明 |
|------|------|
| 账号创建 | 16 位发号（`yyyyMMdd` + 8 位日序列）；`username` 全局唯一；初始状态「正常」 |
| 启停/锁定 | `status`：1 正常 / 0 停用 / 2 锁定；即时令牌吊销属认证中心 |
| 资料维护 | 姓名/昵称/头像/邮箱/手机/备注 + 扩展资料；手机号、邮箱出参脱敏 |
| 对内查询 | by-id / by-username / profile（`user-api` 契约，见 §5） |
| 本系统 RBAC | 菜单管理、角色管理、用户-角色分配（表结构见 `rbac-design.md`，同构建于 `user_db`） |
| 管理审计 | 账号与 RBAC 关键写操作记录操作审计（操作人/时间/动作/结果） |
| 发号 | 段式 DB 发号（见 §7 决策 2） |

---

## 5. 接口草案（`/api/v1`，统一 `{code,msg,data}`，`id` 出参 String）

### 5.1 对内契约（进 `user-api` Feign）

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/users/{id}` | 按 id 查基础信息 |
| GET | `/users/by-username/{username}` | 登录解析（返回 `id` + `status`） |
| GET | `/users/{id}/profile` | OIDC / 展示用资料（脱敏） |

### 5.2 管理 API（经 `user-gateway`，仅 `user-admin` 调用）

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/users` | 分页列表（`current`/`size`，按 username/状态筛选） |
| POST | `/users` | 创建账号 |
| PUT | `/users/{id}` | 更新资料 |
| POST | `/users/{id}/status` | 启停/锁定 |
| GET/POST/PUT/DELETE | `/menus/**` | 菜单管理（树 CRUD） |
| GET/POST/PUT/DELETE | `/roles/**` | 角色管理 + 角色-菜单授权 |
| POST/DELETE | `/users/{id}/roles` | 用户-角色分配/回收 |
| GET | `/me/menus` | 当前操作员本系统菜单树（动态路由下发） |
| GET | `/me/permissions` | 当前操作员本系统权限集（按钮显隐/本地鉴权） |

- 入参 Bean Validation；`orderBy`/`order` 白名单；关键写可 `@Idempotent`；管理接口 `@RateLimit` 按需
- 错误码：先在 `docs/error-code-ranges.md` 登记系统号，再进 `UserErrorCodeEnum`（`CLAUDE.md` §6.4.2）

---

## 6. 数据库设计（`user_db`，8 表，utf8mb4 / InnoDB / 禁物理外键）

### 6.1 通用约定

主键 `id` = 16 位发号（`BIGINT`，出参 String；**发号唯一域 = 本库**）；审计 5 字段全表必备；字符串 `NOT NULL` + 默认值；状态 `TINYINT`、时间 `DATETIME`；索引 `uk_/idx_表名_字段`，单表 ≤ 5；禁止 `SELECT *`。

### 6.2 业务表（4 张）

**`sys_sequence` — 按日发号（基础设施表）**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键 = 业务日数字形态 `yyyyMMdd`（天然唯一，无鸡生蛋） |
| seq_date | DATETIME | N | — | 业务日（`Asia/Shanghai` 日切） |
| current_val | BIGINT | N | 0 | 当日已发到的最大值（段式：每次 +500） |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一**：`uk_sys_sequence_seq_date (seq_date)`；由 `IdGenerator` 直接 SQL 操作，不走业务 Entity 装配

**`sys_user` — 用户主数据（账号权威）**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号；**= OIDC `sub`** |
| username | VARCHAR(64) | N | — | 登录名，全局唯一 |
| real_name | VARCHAR(64) | N | `''` | 姓名 |
| nickname | VARCHAR(64) | N | `''` | 昵称 |
| email | VARCHAR(128) | N | `''` | 出参脱敏 |
| phone | VARCHAR(32) | N | `''` | 出参脱敏 |
| avatar | VARCHAR(255) | N | `''` | 头像 URL/对象键 |
| status | TINYINT | N | 1 | 1 正常 / 0 停用 / 2 锁定 |
| dept_id | BIGINT | Y | NULL | 预留组织，本期不用 |
| last_login_time | DATETIME | Y | NULL | 最近登录（认证侧可选回写） |
| remark | VARCHAR(255) | N | `''` | 备注 |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一**：`uk_sys_user_username (username)`；**索引**：`idx_sys_user_status`、`idx_sys_user_dept_id`（3/5）
- ⛔ 禁止字段：`password` / `password_hash` / 任何凭证密文

**`sys_user_profile` — 资料扩展**（已裁决：首期建，与 `sys_user` 1:1）

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| user_id | BIGINT | N | — | = `sys_user.id` |
| gender | TINYINT | N | 0 | 0 未知 / 1 男 / 2 女 |
| birthday | DATETIME | Y | NULL | |
| address | VARCHAR(255) | N | `''` | |
| extra_json | VARCHAR(1024) | N | `''` | 轻量扩展（避免 TEXT 滥用） |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一**：`uk_sys_user_profile_user_id (user_id)`

**`user_audit_log` — 管理审计**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| action | VARCHAR(64) | N | — | `CREATE` / `UPDATE` / `STATUS_CHANGE` / `ROLE_ASSIGN` / `MENU_UPDATE`… |
| actor_user_id | BIGINT | Y | NULL | 操作者（= 令牌 `sub`） |
| target_user_id | BIGINT | Y | NULL | 被操作用户 |
| detail | VARCHAR(512) | N | `''` | 变更摘要；⛔ 禁密码/完整令牌 |
| ip | VARCHAR(64) | N | `''` | |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **索引**：`idx_user_audit_log_target_user_id`、`idx_user_audit_log_create_time`（2/5）

### 6.3 RBAC 表（4 张）

按 **`rbac-design.md` §2** 同构建于本库（`sys_menu` / `sys_role` / `sys_user_role` / `sys_role_menu`，**无 `system_code`**，各系统自持一套）；本文件不复制字段定义。角色分配的"选人"可经 `user-api` 查本系统用户。

### 6.4 逻辑关系

```text
sys_user 1 ──── 0..1 sys_user_profile
sys_user 1 ──── * user_audit_log
（逻辑）sys_user.id ──► 各系统库 sys_user_role.user_id（含本库）
本库 RBAC：sys_user_role / sys_role_menu / sys_menu 树（见 rbac-design.md §2.5）
```

---

## 7. 关键技术决策（含模板偏差登记）

| # | 决策 | 说明 / 备选否决理由 |
|---|------|------|
| 1 | **16 位发号** = `yyyyMMdd`（8 位）+ 当日序列（8 位），`Asia/Shanghai` 日切 | 覆盖旧模板「14 位 yyMMdd+序号」（`CLAUDE.md` §6.4.5 已裁决）；`BIGINT` 不受影响 |
| 2 | **段式 DB 发号**：`sys_sequence` 按业务日取号段（步长 500）缓存内存，重启丢段可接受 | 备选 Redis INCR 否决：正确性不押 Redis 可用性 |
| 3 | **配置 local/test**：`application.yml` + `application-local.yml` + `application-test.yml` | 覆盖旧模板 dev/test（§6.10 已裁决） |
| 4 | **补 `user-gateway`**（薄边缘层） | 旧模板模块清单未列；按 §5.1 硬链路（前端→Nginx→网关→服务）与 §5.2（`X-User-*` 归网关注入）补全 |
| 5 | **RBAC 各系统自持**，4 表通用模板（无 `system_code`） | 已裁决；数据自治 + 运行时本地鉴权零跨系统调用 |
| 6 | **`sys_user_profile` 首期建** | 已裁决（忠实模板"可选拆分"） |
| 7 | 对内 Feign **内网直连**（Nacos 发现）+ Fallback + 固定超时；管理 API 经网关验签 | 服务间为内网信任边界（§5.1） |
| 8 | SQL 落 `deploy/db/migration/user_db/`，**人工执行** | 不引 Flyway/Liquibase（§2.2） |
| 9 | 前端壳阶段登录暂放行，真实 OIDC 随认证中心落地接线 | 见 §10 演进 |

---

## 8. 安全约定

- 无任何凭证字段（凭证在认证中心 `sys_credential`）；手机号/邮箱出参脱敏（§6.7）
- 管理 API：网关 JWT 验签（JWKS 指向认证中心）+ 注入 `X-User-*`；服务内以注入值为准
- 对内查询接口仅服务间调用（内网信任边界），不对公网暴露
- 审计禁密码/完整令牌；生产密钥禁止入仓；连接信息见 `docs/test-env.md`
- `permission` 标识校验（首段 = `user`）、分页排序白名单、`#{}` 参数化 SQL

---

## 9. 部署与迁移

1. 测试环境建库 `user_db`（utf8mb4），人工执行 `deploy/db/migration/user_db/` SQL（8 表；生产执行前按 §7 征询）
2. 部署 `user-gateway`、`user-service`（Nacos 注册，profile=`test`），健康检查 `/actuator/health`
3. 冒烟：创建用户 → by-username 查询 → 启停 → 列表分页 → 菜单/角色 CRUD；错误路径（重复 username / 未认证 401）
4. 回滚：下线服务即可（建表可逆 DROP，已征询）

---

## 10. 非目标与演进

**首期不做：** 凭证初始化编排（与认证中心建号一致流程）、组织模型与 `data_scope=2` 强制、OIDC 真实接入（随认证中心）、缓存与查询性能优化、跨系统角色同步。

**预留 / 后续：** `dept_id` 组织模型；`extra_json` 扩展资料；建号联动（调认证中心"初始化凭证"，最终一致）；`user-admin` OIDC 接线（redirect_uri / PKCE 随认证中心定型）。

---

## 11. 相关文档

| 文档 | 内容 |
|------|------|
| `CLAUDE.md` | 项目全局契约 |
| `rbac-design.md` | RBAC 通用 4 表与模型约定 |
| `unified-auth-center-design.md` | 认证中心（调用本系统） |
| `docs/version-baseline.md` | 组件版本基线（唯一取用处） |
| `docs/test-env.md` | 环境 IP/端口/账密 |
