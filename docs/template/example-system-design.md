# example 系统 · 完整设计文档（基础模板）

> 状态：**设计已确认**（2026-09-24，逐项确认稿汇总）
> 定位：**基础模板系统**——不承载真实业务，只作为多系统容器下一套业务系统的标准落地参照（工程骨架 + 横切能力示范）；后续新建系统以本文件与本系统代码为模板裁剪
> 范围：完整标准链路（common/framework/api/gateway/service + 管理端前端）、本系统自持 RBAC、幂等/限流/审计、对内 Feign 契约、MinIO 上传；**不带示例业务表**
> 配套：`rbac-design.md`（RBAC 通用 4 表，本库同构一套）、`user-center-design.md` / `unified-auth-center-design.md`（用户与认证权威，本系统只作接线参照）、`docs/version-baseline.md`（组件版本基线）
> 约束：`CLAUDE.md`（全链路对齐，无偏差豁免）；确认项汇总见 §8

---

## 1. 目标与边界

### 1.1 目标

作为新建业务系统的**可复制模板**，完整示范：

- CLAUDE.md §5.1 标准链路（前端 → Nginx → 网关 → 业务服务）与 §5.4/§5.6 模块与包结构
- §6.4 基础代码契约（统一返回体、错误码、全局异常、分页、审计字段、16 位发号、Long→String）
- 横切能力：RBAC 全套（含动态路由下发）、写幂等 `@Idempotent`、接口限流 `@RateLimit`、操作审计、MinIO 上传、对内 Feign 契约 + Fallback

### 1.2 边界（明确不做）

| 不在本系统 | 归属 |
|------------|------|
| 示例业务表 / 真实业务域建模 | 不预置；业务表由各系统按自身变更设计 |
| 用户主数据 | 用户中心（`user-center-design.md`） |
| 密码/凭证、SSO、令牌、OAuth Client | 认证中心（`unified-auth-center-design.md`） |
| 跨系统角色/菜单同步、集中式 RBAC 服务 | 不做（各系统自持，见 `rbac-design.md`） |
| 批量上传、Excel 导入导出、分布式定时、多语言强制交付 | `CLAUDE.md` §2.2 |
| 生产密钥入仓 | Nacos / 密钥管理 / Jenkins Credentials |

---

## 2. 形态与架构（完整标准链路）

- **前后端分离 + Spring Cloud 全链路**：浏览器 → Nginx（静态 + 反代 `/api`）→ `example-gateway` → `example-service`；Nacos 注册/配置（SCA）
- **双重鉴权**：网关 JWT 验签（JWKS 指向认证中心）+ 注入 `X-User-*`；服务内 Spring Security `@PreAuthorize` 细粒度授权（`CLAUDE.md` §5.2）
- **CORS** 归网关唯一归属（信任域名登记 `docs/test-env.md`）
- **服务间调用**：OpenFeign + Fallback + 固定超时（对内契约见 §5.1）；禁止事务内 Feign
- **Redis/Redisson**：`@RateLimit`（RRateLimiter）+ `@Idempotent` + 缓存（键命名 §6.9，全部带 TTL）
- **MinIO**：文件存储，客户端与上传策略统一经 `example-framework` 封装（版本取 `docs/version-baseline.md`）

```text
浏览器（example-admin SPA）
  │ HTTPS
  ▼
Nginx（静态托管 + 反代 /api，TLS 终结）
  │
  ▼
example-gateway（JWT 验签 + X-User-* 注入 + CORS + 路由）
  │
  ▼
example-service（业务 + RBAC + 文件 + 审计；@PreAuthorize 本地鉴权）
  │            └── OpenFeign ──► 其他系统 {domain}-api（示范契约方向，本系统不依赖具体下游）
  ├── example_db（MySQL 分库，数据自治）
  ├── Redis（幂等/限流/缓存）
  ├── Nacos（注册/配置）
  └── MinIO（文件对象）
```

---

## 3. 模块划分（五模块 + 一个管理端前端）

```
backend/example/                       # Maven 多模块，包名 com.qjj.example.*
├── example-common                     # Result/错误码/异常/分页契约/BaseEnum/常量/工具（零配置纯基础）
├── example-framework                  # 基础设施装配（见下表）
├── example-api                        # 对内契约：DTO/VO + Feign + Fallback（无 Entity）
├── example-service                    # 本域 Entity/Mapper/Service/Controller（可运行，三份 yml）
└── example-gateway                    # 薄边缘治理（见下表）
frontend/example-admin/                # 管理端（Vue3 + TS + Pinia + Element Plus）
```

| 模块 | 组件（版本一律取 `docs/version-baseline.md`，先改表再改 pom） |
|------|------|
| `example-common` | Lombok + 注解类（jakarta.validation / jackson-annotations）；⛔ 禁 MP/Redis/数据源/Actuator/MQ/MinIO/JWT 库/Hutool |
| `example-framework` | starter-web / validation / aop / actuator / data-redis + Redisson 3.52.0 + MyBatis-Plus 3.5.17（boot3 starter）+ OpenFeign + springdoc 2.9.1 + micrometer-tracing-bridge-brave + **MinIO SDK 9.0.3 封装**（上传/预签名/删除策略统一封装于此，禁止业务直绑 SDK） |
| `example-api` | spring-cloud-starter-openfeign + Lombok |
| `example-service` | example-common/framework/api + mysql-connector-j + MapStruct + nacos-discovery/config（SCA 2025.0.0.0） |
| `example-gateway` | spring-cloud-starter-gateway + nacos-discovery + oauth2-resource-server（验签用 JWKS 公钥，**不引 SAS**）+ actuator |

**Spring Cloud 能力映射**：注册/发现 = Nacos；配置 = Nacos + `application-local/-test.yml`；服务间调用 = OpenFeign + Fallback + 固定超时；边缘 = Gateway（路由 / JWT 验签 / `X-User-*` 注入 / CORS）；链路 = Micrometer Tracing（traceId 进 MDC）；监控 = Actuator。

**包结构**（`CLAUDE.md` §5.6.2）：`com.qjj.example.{module}` → controller / dto / vo / service / bo / converter（MapStruct 唯一落点）/ mapper / entity / feign / config / constants / enums / util；调用链 Controller → Service → Mapper。

---

## 4. 核心能力（演示清单）

| 能力 | 演示落点 |
|------|----------|
| **RBAC 全套** | `rbac-design.md` §2 4 表同构入 `example_db`；菜单管理（树 CRUD）+ 角色管理（分页/角色-菜单授权/用户-角色分配）+ `/me/menus`（动态路由下发）+ `/me/permissions`（按钮显隐）+ `@PreAuthorize`；**菜单树 CRUD 与角色分页同时承担「完整 CRUD + 分页」示范**（无示例业务表） |
| **写幂等** | RBAC 管理、文件删除等关键写接口 `@Idempotent`（`Idempotent-Key`，缺省 `X-Request-Id`）；重复请求返回**首次 `Result`** |
| **接口限流** | 文件上传、查询列表 `@RateLimit`（RRateLimiter）；超限 HTTP 429 + 系统码 `10003` |
| **操作审计** | `example_audit_log`：RBAC 与文件关键写记录操作人/时间/动作/结果（载体 = 审计表） |
| **上传** | MinIO：单文件 ≤10MB、类型白名单、UUID 文件名、元数据落库、预签名 URL、逻辑删除（`CLAUDE.md` §6.11） |
| **对内契约** | `example-api`：文件元数据查询 + ping 探针，Feign + Fallback + 固定超时（见 §5.1） |
| **发号** | 16 位定长（`yyyyMMdd` + 8 位日序列，`Asia/Shanghai` 日切），`sys_sequence` 段式发号 |
| **基础契约** | 统一 `{code,msg,data}`、错误码枚举、全局异常、分页 `{records,total,size,current}`、Long→String、MapStruct 转换 |

---

## 5. 接口草案（`/api/v1`，统一 `{code,msg,data}`，`id` 出参 String）

### 5.1 对内契约（进 `example-api` Feign，仅服务间调用，不对公网暴露）

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/internal/files/{id}` | 查文件元数据（VO：对象键/原名/类型/大小等） |
| GET | `/internal/ping` | 健康探针（演示 Fallback 降级返回） |

### 5.2 管理 API（经 `example-gateway`，供 `example-admin` 调用）

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

- 入参 Bean Validation；`orderBy`/`order` 白名单；RESTful 命名对齐 `CLAUDE.md` §6.2
- 错误码：实施时先在 `docs/error-code-ranges.md` 登记系统号（当前登记表为空，不预占），再进 `ExampleErrorCodeEnum`

---

## 6. 数据库设计（`example_db`，7 表，utf8mb4 / InnoDB / 禁物理外键）

### 6.1 通用约定

主键 `id` = 16 位发号（`yyyyMMdd` + 8 位当日序列，`BIGINT`，出参 String；**发号唯一域 = 本库**）；审计 5 字段全表必备；字符串 `NOT NULL` + 默认值；状态 `TINYINT`、时间 `DATETIME`；索引 `uk_/idx_表名_字段`，单表 ≤ 5；禁止 `SELECT *`。

### 6.2 支撑表（3 张业务面 + 4 张 RBAC，**无示例业务表**）

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

**`example_audit_log` — 操作审计**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| action | VARCHAR(64) | N | — | `MENU_CREATE` / `ROLE_UPDATE` / `ROLE_ASSIGN` / `FILE_UPLOAD` / `FILE_DELETE`… |
| actor_user_id | BIGINT | Y | NULL | 操作者（= 已验签令牌 `sub` / 网关注入 `X-User-Id`） |
| target_type | VARCHAR(32) | N | `''` | `MENU` / `ROLE` / `FILE`… |
| target_id | VARCHAR(64) | N | `''` | |
| detail | VARCHAR(512) | N | `''` | 变更摘要；⛔ 禁密码/完整令牌 |
| ip | VARCHAR(64) | N | `''` | |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **索引**：`idx_example_audit_log_action`、`idx_example_audit_log_create_time`（2/5）

**RBAC 表（4 张）**：按 **`rbac-design.md` §2** 同构建于本库（`sys_menu` / `sys_role` / `sys_user_role` / `sys_role_menu`，**无 `system_code`**）；本文件不复制字段定义。角色分配的"选人"可经用户中心 `user-api` 查本系统用户（跨系统只读契约）。

### 6.3 逻辑关系

```text
（用户中心）sys_user.id ──（逻辑）──► sys_user_role.user_id / example_audit_log.actor_user_id
sys_file 1 ──── * example_audit_log（target_type=FILE）
本库 RBAC：sys_user_role / sys_role_menu / sys_menu 树（rbac-design.md §2.5）
```

---

## 7. 前端（`frontend/example-admin`，按 `CLAUDE.md` §5.5 结构）

| 页面 | 演示点 |
|------|--------|
| 布局壳 | 登录后取 `/me/menus` **动态生成路由**（禁硬编码角色）；`/me/permissions` 控制按钮显隐 |
| 菜单管理 | 树 CRUD（type 1/2/3/4 全类型），示范复杂表单与树组件 |
| 角色管理 | 分页列表 + 角色-菜单授权 + 用户-角色分配 |
| 文件管理 | 上传（大小/类型前端提示）、列表、预签名 URL 预览/下载、逻辑删除 |
| 审计日志 | 分页查询（按 action/时间筛选） |

- 登录：壳阶段先本地放行联调，真实 OIDC（redirect_uri / PKCE）随认证中心落地接线（同 user-center 决策 9）
- 请求只经 `src/api`；`id` 一律 `string`；axios 统一解包 `{code,msg,data}` 并按 `CLAUDE.md` §5.3 分流
- 新增前端工程落地时，**先**在 `docs/version-baseline.md` 登记主框架版本（Vue3 / Vite / Element Plus / Pinia），再锁 `package-lock.json`

---

## 8. 关键技术决策（2026-09-24 逐项确认汇总；对 `CLAUDE.md` **无偏差豁免**）

| # | 决策 | 说明 / 备选否决理由 |
|---|------|------|
| 1 | **定位：纯基础模板，不带示例业务表** | 已裁决（备选「单表 CRUD / 主子两表」否决——业务表由各系统自设计，模板不预置假业务）；CRUD + 分页示范由 RBAC 管理页承担 |
| 2 | **完整标准链路：五模块 + 管理端前端** | 已裁决（`common`/`framework`/`example-api`/`gateway`/`service`）；备选「无 api 模块」「仿 auth 单体」否决——业务系统模板须忠实 §5.1 硬链路 |
| 3 | **横切演示四项全选**：RBAC 全套、幂等+限流+审计、Feign 契约 + Fallback、MinIO 上传 | 已裁决（上传含 MinIO，接受模板连带中间件变重） |
| 4 | **表清单 7 张**：`sys_sequence` + RBAC 4 + `example_audit_log` + `sys_file` | 已裁决（备选「审计只走日志」「不留文件记录」否决——审计与逻辑删除契约需要落点） |
| 5 | **`example-api` 演示文件元数据契约 + ping** | 已裁决（备选「仅 ping 空壳」「查用户中心投影」否决——内容真实可跑，且不背对用户中心的依赖） |
| 6 | **上传演示：上传 + 查 + 删 + 预签名 URL** | 已裁决（备选「仅上传+查询」「含批量上传」否决——单文件生命周期完整即可，批量留各系统按需） |
| 7 | **幂等/限流落点**：关键写 `@Idempotent`；上传/列表/预签名 `@RateLimit` | 已裁决；参数与 TTL 由 `framework` 固定（§6.11） |
| 8 | **登录鉴权同 user-center 决策 9**：壳阶段放行，网关 JWKS 验签随认证中心接线 | 模板交付时以接线注释标注 |
| 9 | **配置 local/test** 三份 yml；SQL 落 `deploy/db/migration/example_db/` **人工执行** | §6.10 / §2.2（不引 Flyway/Liquibase） |
| 10 | **错误码系统号实施时登记** `docs/error-code-ranges.md`，不预占 | 当前登记表为空（2026-09-24） |
| 11 | **`sys_file` 删除语义统一走 `deleted`，不另设 `status` 列** | 定稿确认（2026-09-24）；避免「状态删除」与逻辑删除双轨（备选「另设 status 列」否决） |
| 12 | **`example_audit_log` 采用通用 `target_type` / `target_id` 形态**（仿 `auth_audit_log`） | 定稿确认（2026-09-24）；审计对象覆盖 MENU/ROLE/FILE 多类型（备选 `user_audit_log` 的 `target_user_id` 形态否决） |

---

## 9. 安全约定

- 上传：类型白名单（应用层 + `content_type` 双校验）、单文件 ≤10MB、UUID 文件名、预签名 URL 短 TTL；逻辑删除后对象清理走运维脚本（不建定时，§2.2）
- `permission` 标识校验（首段 = `example`）、`@PreAuthorize` 与前端按钮同一字符串；分页排序白名单；`#{}` 参数化 SQL
- 网关 JWT 验签 + `X-User-*` 注入；服务内以注入值为准，不信可伪造客户端头（§5.2）
- 审计与日志禁密码/完整令牌；手机号脱敏工具在 `example-common`；生产密钥禁止入仓（连接信息见 `docs/test-env.md`）

---

## 10. 部署与迁移

1. 测试环境建库 `example_db`（utf8mb4），人工执行 `deploy/db/migration/example_db/` SQL（7 表；生产执行前按 §7 征询）
2. 准备 Redis、MinIO（建桶）、Nacos；部署 `example-gateway`、`example-service`（profile=`test`），健康检查 `/actuator/health`
3. Nginx：托管 `example-admin` 静态；反代 `/api` → `example-gateway`（信任域名登记 `docs/test-env.md`）
4. 冒烟：菜单/角色 CRUD + 授权 → `/me/menus` 动态路由 → 上传/预签名/逻辑删除 → 幂等重复提交返回首次 Result → 限流 429（`10003`）→ 对内 Feign `ping`/`files/{id}` + Fallback 降级
5. 回滚：下线服务 + 保留库与 MinIO 快照（无破坏性数据变更）

---

## 11. 非目标与演进

**首期不做：** 示例业务表、批量上传、真实 OIDC 接线、缓存与查询性能优化、组织模型与 `data_scope=2` 强制、Excel、MinIO 对象定时清理、跨系统角色同步。

**预留 / 后续：** 各系统在本模板上增补业务表与 `{domain}-api` 契约；`user-admin`/本系统 OIDC 接线随认证中心定型；前端主框架版本随首个前端工程落地登记 `docs/version-baseline.md`。

---

## 12. 相关文档

| 文档 | 内容 |
|------|------|
| `CLAUDE.md` | 项目全局契约（本系统无偏差豁免） |
| `rbac-design.md` | RBAC 通用 4 表与模型约定 |
| `user-center-design.md` | 用户中心（账号权威；角色分配"选人"可经其 `user-api`） |
| `unified-auth-center-design.md` | 认证中心（网关 JWKS 验签指向方） |
| `docs/version-baseline.md` | 组件版本基线（唯一取用处） |
| `docs/error-code-ranges.md` | 错误码系统号登记处 |
| `docs/test-env.md` | 环境 IP/端口/账密/信任域名 |
