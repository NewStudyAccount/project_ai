# example-mono 系统 · 完整设计文档（单体基础模板）

> 状态：**设计已确认**（2026-09-24，逐项确认稿汇总）
> 定位：**纯单体形态的基础模板**——与 `example-system-design.md`（微服务全链路版）**成对**；不承载真实业务，只作为「单体架构业务系统」的标准落地参照（工程骨架 + 横切能力示范）；后续以单体形态交付的系统以本文件与本系统代码为模板裁剪
> 形态：**前后端分离的单体服务**（对齐 `unified-auth-center-design.md` §2 已裁决口径）；三模块 + 一个管理端前端；**无网关、无 Nacos**
> 范围：能力清单同 `example-system-design.md`（本系统自持 RBAC、幂等/限流/审计、MinIO 上传）；**不带示例业务表**；跨系统示范 = Feign 直连消费 `user-api`
> 配套：`example-system-design.md`（微服务姊妹版）、`rbac-design.md`（RBAC 通用 4 表，本库同构一套）、`user-center-design.md`（`user-api` 被消费方）、`unified-auth-center-design.md`（单体形态先例与 JWKS 验签指向方）、`docs/version-baseline.md`
> 约束：`CLAUDE.md`（偏差登记见 §8）；确认项汇总见 §8

---

## 1. 目标与边界

### 1.1 目标

作为**单体架构业务系统**的可复制模板，完整示范：

- 单体形态下的标准落地：前后端分离、Nginx 直反、鉴权单层、无注册中心的服务间调用（Feign 直连 URL）
- `CLAUDE.md` §5.4/§5.6 模块与包结构、§6.4 基础代码契约（统一返回体、错误码、全局异常、分页、审计字段、16 位发号、Long→String）
- 横切能力：RBAC 全套（含动态路由下发）、写幂等 `@Idempotent`、接口限流 `@RateLimit`、操作审计、MinIO 上传

### 1.2 边界（明确不做）

| 不在本系统 | 归属 |
|------------|------|
| 示例业务表 / 真实业务域建模 | 不预置；业务表由各系统按自身变更设计 |
| 用户主数据 | 用户中心（经 `user-api` 消费，见 §5.1） |
| 密码/凭证、SSO、令牌、OAuth Client | 认证中心 |
| 跨系统角色/菜单同步、集中式 RBAC 服务 | 不做（各系统自持，见 `rbac-design.md`） |
| 网关、Nacos 注册/配置、微服务拆分 | 本模板**不引入**；确需拆分以 `example-system-design.md` 为参照另开变更 |
| `{domain}-api` 契约 jar 模块 | 不建（对内被动接口按 OpenAPI 文档调用，见 §5.2） |
| 批量上传、Excel 导入导出、分布式定时、多语言强制交付 | `CLAUDE.md` §2.2 |
| 生产密钥入仓 | Nacos / 密钥管理 / Jenkins Credentials |

---

## 2. 形态与架构（单体，已裁决）

- **前后端分离单体**：`example-mono-service` 一个可运行 Spring Boot 应用 + 一个 SPA；Nginx 托管静态并反代 `/api`
- **不引入**：网关（Nginx 直反 `example-mono-service`）、Nacos 注册/配置（纯 `application-local/-test.yml`）
- **鉴权单层**：服务内 Spring Security 验签（JWKS 指向认证中心），**以已验签令牌 `sub` 为准**；无网关注入 `X-User-*`，服务内**不信任**客户端可伪造的 `X-User-*` 头
- **CORS** 归 `example-mono-service` 自配（信任域名登记 `docs/test-env.md`）
- **对用户中心调用**：复用 `user-api` 契约构件，OpenFeign **直连 URL 模式**（配置 `user-center.base-url`）+ Fallback + 固定超时；禁止事务内调用
- **Redis/Redisson** 保留：`@RateLimit`（RRateLimiter）+ `@Idempotent` + 缓存（键命名 `CLAUDE.md` §6.9，全部带 TTL）
- **MinIO**：文件存储，客户端与上传策略统一经 `example-mono-framework` 封装（版本取 `docs/version-baseline.md`）

```text
浏览器（example-mono-admin SPA）
  │ HTTPS
  ▼
Nginx（静态托管 + 反代 /api，TLS 终结；/internal 不反代）
  │
  ▼
example-mono-service（单体：鉴权单层 + RBAC + 文件 + 审计）
  │            └── OpenFeign 直连 URL ──► 用户中心（user-api 契约：选人/投影，Fallback 可降级）
  ├── example_mono_db（MySQL 独立库，数据自治）
  ├── Redis（幂等/限流/缓存）
  └── MinIO（文件对象）
```

---

## 3. 模块划分（三模块 + 一个管理端前端）

```
backend/example-mono/                  # Maven 多模块，包名 com.qjj.examplemono.*
├── example-mono-common                # Result/错误码/异常/分页契约/BaseEnum/常量/工具（零配置纯基础）
├── example-mono-framework             # 基础设施装配（见下表）
└── example-mono-service               # 可运行单体：鉴权 + RBAC + 文件 + 审计（三份 yml）
     ↑ 依赖 user-api（跨系统契约构件，来自用户中心）
frontend/example-mono-admin/           # 管理端（Vue3 + TS + Pinia + Element Plus）
```

| 模块 | 组件（版本一律取 `docs/version-baseline.md`，先改表再改 pom） |
|------|------|
| `example-mono-common` | Lombok + 注解类（jakarta.validation / jackson-annotations）；⛔ 禁 MP/Redis/数据源/Actuator/MQ/MinIO/JWT 库/Hutool |
| `example-mono-framework` | starter-web / validation / aop / actuator / data-redis + Redisson 3.52.0 + MyBatis-Plus 3.5.17（boot3 starter）+ **`oauth2-resource-server`（验签用 JWKS 公钥，不引 SAS）** + OpenFeign + springdoc 2.9.1 + micrometer-tracing-bridge-brave + **MinIO SDK 9.0.3 封装**（上传/预签名/删除策略统一封装于此，禁止业务直绑 SDK） |
| `example-mono-service` | example-mono-common/framework + `user-api` + mysql-connector-j + MapStruct；⛔ 无 Nacos/网关 |

**包结构**（`CLAUDE.md` §5.6.2）：`com.qjj.examplemono.{module}` → controller / dto / vo / service / bo / converter（MapStruct 唯一落点）/ mapper / entity / feign / config / constants / enums / util；调用链 Controller → Service → Mapper。

---

## 4. 核心能力（演示清单）

| 能力 | 演示落点 |
|------|----------|
| **RBAC 全套** | `rbac-design.md` §2 4 表同构入 `example_mono_db`；菜单管理（树 CRUD）+ 角色管理（分页/角色-菜单授权/用户-角色分配）+ `/me/menus`（动态路由下发）+ `/me/permissions`（按钮显隐）+ `@PreAuthorize`；**菜单树 CRUD 与角色分页同时承担「完整 CRUD + 分页」示范**（无示例业务表） |
| **写幂等** | RBAC 管理、文件删除等关键写接口 `@Idempotent`（`Idempotent-Key`，缺省 `X-Request-Id`）；重复请求返回**首次 `Result`** |
| **接口限流** | 文件上传、查询列表 `@RateLimit`（RRateLimiter）；超限 HTTP 429 + 系统码 `10003` |
| **操作审计** | `example_mono_audit_log`：RBAC 与文件关键写记录操作人/时间/动作/结果（载体 = 审计表） |
| **上传** | MinIO：单文件 ≤10MB、类型白名单、UUID 文件名、元数据落库、预签名 URL、逻辑删除（`CLAUDE.md` §6.11） |
| **跨系统消费** | Feign 直连 URL 消费 `user-api`（角色分配"选人"回显 / 用户投影）+ Fallback + 固定超时；示范**单体（无服务发现）怎么调其他系统**（见 §5.1） |
| **对内被动接口** | `/internal/files/{id}` + `/internal/ping`（OpenAPI 文档化；**无契约 jar**，供其他系统按文档调用，见 §5.2） |
| **发号** | 16 位定长（`yyyyMMdd` + 8 位日序列，`Asia/Shanghai` 日切），`sys_sequence` 段式发号 |
| **基础契约** | 统一 `{code,msg,data}`、错误码枚举、全局异常、分页 `{records,total,size,current}`、Long→String、MapStruct 转换 |

---

## 5. 接口与调用草案

### 5.1 跨系统消费（Feign 直连 `user-api`，本系统为调用方）

| 调用 | 用途 | 说明 |
|------|------|------|
| GET `/users/{id}` | 角色分配"选人"回显、审计操作者/对象展示（用户投影） | `user-api` 契约（`user-center-design.md` §5.1） |
| GET `/users/{id}/profile` | 展示用资料（脱敏） | 同上 |

- 配置 `user-center.base-url`（登记 `docs/test-env.md`）；**必须 Fallback**（降级：选人回显失败可跳过，不阻断授权写）；固定连接/读取超时；**禁止事务内调用**
- 备选「双向示范（另建 `{domain}-api` 契约 jar）」已否决——单体对内暴露按 §5.2 走 OpenAPI 文档，不建契约构件

### 5.2 对内被动接口（`/internal/**`，仅内网服务间调用）

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/internal/files/{id}` | 查文件元数据（VO：对象键/原名/类型/大小等） |
| GET | `/internal/ping` | 健康探针 |

- **Nginx 不反代 `/internal`**（仅内网信任边界直连服务端口）；契约以 OpenAPI（springdoc）为唯一文档源，调用方按文档接入

### 5.3 管理 API（`/api/v1`，经 Nginx；服务内鉴权后访问）

| 方法 | 路径 | 用途 | 横切标注 |
|------|------|------|----------|
| GET/POST/PUT/DELETE | `/menus/**` | 菜单管理（树 CRUD） | 关键写 `@Idempotent` |
| GET/POST/PUT/DELETE | `/roles/**` | 角色管理 + 角色-菜单授权 | 关键写 `@Idempotent` |
| POST/DELETE | `/users/{id}/roles` | 用户-角色分配/回收 | `@Idempotent` |
| GET | `/me/menus` | 当前操作员菜单树（动态路由下发） | |
| GET | `/me/permissions` | 当前操作员权限集（按钮显隐/本地鉴权） | |
| POST | `/files` | 单文件上传（multipart ≤10MB） | `@RateLimit` |
| GET | `/files` | 文件分页列表 | `@RateLimit` |
| GET | `/files/{id}` | 文件元数据 | |
| GET | `/files/{id}/url` | 预签名 URL（短 TTL） | `@RateLimit` |
| DELETE | `/files/{id}` | 逻辑删除 | `@Idempotent` |
| GET | `/audit-logs` | 操作审计查询 | |

- 统一 `{code,msg,data}`、`id` 出参 String；入参 Bean Validation；`orderBy`/`order` 白名单；RESTful 命名对齐 `CLAUDE.md` §6.2
- 错误码：实施时先在 `docs/error-code-ranges.md` 登记系统号，再进 `ExampleMonoErrorCodeEnum`

---

## 6. 数据库设计（`example_mono_db`，7 表，utf8mb4 / InnoDB / 禁物理外键）

### 6.1 通用约定

主键 `id` = 16 位发号（`yyyyMMdd` + 8 位当日序列，`BIGINT`，出参 String；**发号唯一域 = 本库**）；审计 5 字段全表必备；字符串 `NOT NULL` + 默认值；状态 `TINYINT`、时间 `DATETIME`；索引 `uk_/idx_表名_字段`，单表 ≤ 5；禁止 `SELECT *`。

### 6.2 支撑表（3 张业务面 + 4 张 RBAC，**无示例业务表**）

表清单与 `example-system-design.md` §6 **同构**（含两项已定稿口径），仅库名与审计表名随系统名：

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

**`sys_file` — 上传文件记录**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| bucket | VARCHAR(64) | N | `''` | MinIO 桶名 |
| object_key | VARCHAR(255) | N | — | 对象键（**UUID 文件名**） |
| original_name | VARCHAR(255) | N | `''` | 上传时原始文件名 |
| content_type | VARCHAR(128) | N | `''` | MIME 类型（上传白名单校验） |
| size_bytes | BIGINT | N | 0 | 文件大小（字节，单文件 ≤10MB） |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | **逻辑删除即文件删除语义**（MinIO 对象清理不建定时，`CLAUDE.md` §2.2/§6.11） |

- **唯一**：`uk_sys_file_object_key (object_key)`；**索引**：`idx_sys_file_create_time`（2/5）

**`example_mono_audit_log` — 操作审计**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| action | VARCHAR(64) | N | — | `MENU_CREATE` / `ROLE_UPDATE` / `ROLE_ASSIGN` / `FILE_UPLOAD` / `FILE_DELETE`… |
| actor_user_id | BIGINT | Y | NULL | 操作者（= 已验签令牌 `sub`） |
| target_type | VARCHAR(32) | N | `''` | `MENU` / `ROLE` / `FILE`… |
| target_id | VARCHAR(64) | N | `''` | |
| detail | VARCHAR(512) | N | `''` | 变更摘要；⛔ 禁密码/完整令牌 |
| ip | VARCHAR(64) | N | `''` | |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **索引**：`idx_example_mono_audit_log_action`、`idx_example_mono_audit_log_create_time`（2/5）

**RBAC 表（4 张）**：按 **`rbac-design.md` §2** 同构建于本库（`sys_menu` / `sys_role` / `sys_user_role` / `sys_role_menu`，**无 `system_code`**）；本文件不复制字段定义。角色分配的"选人"经 **Feign 直连**消费用户中心 `user-api`（§5.1，Fallback 可降级）。

### 6.3 逻辑关系

```text
（用户中心）sys_user.id ──（逻辑）──► sys_user_role.user_id / example_mono_audit_log.actor_user_id
sys_file 1 ──── * example_mono_audit_log（target_type=FILE）
本库 RBAC：sys_user_role / sys_role_menu / sys_menu 树（rbac-design.md §2.5）
```

---

## 7. 前端（`frontend/example-mono-admin`，按 `CLAUDE.md` §5.5 结构）

| 页面 | 演示点 |
|------|--------|
| 布局壳 | 登录后取 `/me/menus` **动态生成路由**（禁硬编码角色）；`/me/permissions` 控制按钮显隐 |
| 菜单管理 | 树 CRUD（type 1/2/3/4 全类型），示范复杂表单与树组件 |
| 角色管理 | 分页列表 + 角色-菜单授权 + 用户-角色分配（选人经本系统代理调 `user-api`，失败降级） |
| 文件管理 | 上传（大小/类型前端提示）、列表、预签名 URL 预览/下载、逻辑删除 |
| 审计日志 | 分页查询（按 action/时间筛选） |

- 登录：壳阶段先本地放行联调，真实 OIDC 随认证中心落地接线（public + PKCE，对齐 `unified-auth-center-design.md` 路线 1）
- 请求只经 `src/api`；`id` 一律 `string`；axios 统一解包 `{code,msg,data}` 并按 `CLAUDE.md` §5.3 分流
- 新增前端工程落地时，**先**在 `docs/version-baseline.md` 登记主框架版本（Vue3 / Vite / Element Plus / Pinia），再锁 `package-lock.json`

---

## 8. 关键技术决策与偏差登记（2026-09-24 逐项确认汇总）

| # | 决策 | 说明 / 备选否决理由 |
|---|------|------|
| 1 | **定位：纯单体形态模板，与 example 成对**（微服务版 / 单体版） | 已裁决；不承载真实业务、不带示例业务表（继承 `example-system-design.md` 决策 1） |
| 2 | **命名 `example-mono`**：目录 `backend/example-mono/` + `frontend/example-mono-admin/`；包名 `com.qjj.examplemono.*`；权限首段 `example-mono:` | 已裁决 |
| 3 | **三模块** `common` / `framework` / `service` | 对齐 `unified-auth-center-design.md` 决策 2 同款裁决（备选两模块/单模块否决——common/framework 分层契约保留，便于日后拆微服务平移） |
| 4 | **跨系统示范 = Feign 直连 URL 消费 `user-api`** | 已裁决（备选「仅被动 REST」缺单体调外示范、「双向示范含 `{domain}-api` jar」令模板变重，均否决） |
| 5 | **能力清单同 example**：RBAC 全套、幂等+限流+审计、MinIO 上传（上传/查/删/预签名）、7 张表 | 继承 `example-system-design.md` 决策 3/4/5/6/7；含两项定稿口径——`sys_file` 删除走 `deleted` 不设 `status`（决策 11）、审计用 `target_type/target_id`（决策 12） |
| 6 | **形态偏差：无网关、无 Nacos，Nginx 直反** | **偏差**：`CLAUDE.md` §5.1 网关链路对本模板豁免（对齐 auth-center 决策 1 同款）；要拆微服务时以 `example-system-design.md` 为参照另开变更 |
| 7 | **鉴权单层**：以已验签令牌 `sub` 为准，不信任客户端 `X-User-*` | **偏差**：§5.2 双重鉴权对本模板收窄为单层（无网关注入源；比双重鉴权更严格，对齐 auth-center 口径） |
| 8 | **CORS 归服务自配** | **偏差**：`CLAUDE.md` §5.2「CORS 唯一归属网关」对本模板豁免（无网关；信任域名登记 `docs/test-env.md`） |
| 9 | **对内被动接口无契约 jar**：`/internal/**` 走 OpenAPI 文档，Nginx 不反代 | 内网信任边界（对齐 `user-center-design.md` §8 口径） |
| 10 | **配置 local/test** 三份 yml（无 Nacos）；SQL 落 `deploy/db/migration/example_mono_db/` **人工执行** | `CLAUDE.md` §6.10 / §2.2 |
| 11 | **错误码系统号实施时登记** `docs/error-code-ranges.md`，不预占 | 当前登记表为空（2026-09-24） |

---

## 9. 安全约定

- 验签单层：服务内 JWT 验签（JWKS 指向认证中心）；`user_id` 以令牌 `sub` 为准，**拒信**可伪造客户端头
- `/internal/**` 仅内网信任边界（Nginx 不反代）；`user-center.base-url` 仅内网地址（登记 `docs/test-env.md`）
- 上传：类型白名单（应用层 + `content_type` 双校验）、单文件 ≤10MB、UUID 文件名、预签名 URL 短 TTL；逻辑删除后对象清理走运维脚本（不建定时，§2.2）
- `permission` 标识校验（首段 = `example-mono`）、`@PreAuthorize` 与前端按钮同一字符串；分页排序白名单；`#{}` 参数化 SQL
- 审计与日志禁密码/完整令牌；手机号脱敏工具在 `example-mono-common`；生产密钥禁止入仓（连接信息见 `docs/test-env.md`）

---

## 10. 部署与迁移

1. 测试环境建库 `example_mono_db`（utf8mb4），人工执行 `deploy/db/migration/example_mono_db/` SQL（7 表；生产执行前按 §7 征询）
2. 准备 Redis、MinIO（建桶）；部署 `example-mono-service`（profile=`test`），健康检查 `/actuator/health`
3. Nginx：托管 `example-mono-admin` 静态；反代 `/api` → `example-mono-service`（**`/internal` 不反代**；信任域名登记 `docs/test-env.md`）
4. 冒烟：菜单/角色 CRUD + 授权 → `/me/menus` 动态路由 → 上传/预签名/逻辑删除 → 幂等重复提交返回首次 Result → 限流 429（`10003`）→ Feign 调 `user-api` 选人 + Fallback 降级
5. 回滚：下线应用 + 保留库与 MinIO 快照（无破坏性数据变更）

---

## 11. 非目标与演进

**首期不做：** 示例业务表、网关/Nacos/微服务拆分、`{domain}-api` 契约 jar、批量上传、真实 OIDC 接线、缓存与查询性能优化、组织模型与 `data_scope=2` 强制、Excel、MinIO 对象定时清理、跨系统角色同步、多实例会话亲和（Nginx 层负载属部署事项）。

**预留 / 后续：** 各系统在本模板上增补业务表；OIDC 接线随认证中心定型；若日后需要拆微服务，以 `example-system-design.md`（微服务全链路版）为参照另开变更平移 common/framework 分层；前端主框架版本随首个前端工程落地登记 `docs/version-baseline.md`。

---

## 12. 相关文档

| 文档 | 内容 |
|------|------|
| `CLAUDE.md` | 项目全局契约（本模板偏差登记见 §8） |
| `example-system-design.md` | 微服务全链路姊妹版模板 |
| `rbac-design.md` | RBAC 通用 4 表与模型约定 |
| `user-center-design.md` | 用户中心（`user-api` 被本系统 Feign 直连消费） |
| `unified-auth-center-design.md` | 认证中心（单体形态先例；JWKS 验签指向方） |
| `docs/version-baseline.md` | 组件版本基线（唯一取用处） |
| `docs/error-code-ranges.md` | 错误码系统号登记处 |
| `docs/test-env.md` | 环境 IP/端口/账密/信任域名/`user-center.base-url` |
