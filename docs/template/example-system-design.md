# example 系统 · 完整设计文档（基础模板）

> 状态：**设计已确认**（2026-09-24，逐项确认稿汇总；后续补配置契约、权限码接口表、横切默认值与自检/验收清单）
> 定位：**基础模板系统**——不承载真实业务，只作为多系统容器下一套业务系统的标准落地参照（工程骨架 + 横切能力示范）；后续新建系统以本文件与本系统代码为模板裁剪
> 范围：完整标准链路（common/framework/api/gateway/service + 管理端前端）、本系统自持 RBAC、幂等/限流/审计、对内 Feign 契约、MinIO 上传、接口权限码与配置契约、部署自检；**不带示例业务表**
> 配套：`rbac-design.md`（RBAC 通用 4 表，本库同构一套）、`gateway-design.md`（**`example-gateway` 设计唯一裁剪源**）、`user-center-design.md` / `unified-auth-center-design.md`（用户与认证权威，本系统只作接线参照）、`docs/version-baseline.md`（组件版本基线）
> 约束：`CLAUDE.md`（全链路对齐，无偏差豁免）；确认项汇总见 §8；网关能力/依赖/检查清单以 `gateway-design.md` 为准，本文只写 example 接线差异；**版本号只写在 `docs/version-baseline.md`**

---

## 1. 目标与边界

### 1.1 目标

作为新建业务系统的**可复制模板**，完整示范：

- CLAUDE.md §5.1 标准链路（前端 → Nginx → 网关 → 业务服务）与 §5.4/§5.6 模块与包结构；网关按 `gateway-design.md` 裁剪
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
- **纯网关鉴权**（对齐 `CLAUDE.md` §5.2 / `gateway-design.md`）：网关 JWT 验签（JWKS → 认证中心，含 `iss`/`aud`）+ 剥除/注入 `X-User-*`；**业务服务不装 Spring Security、不用 `@PreAuthorize` 拦接口**；细粒度权限码为业务软校验/按钮显隐
- **网关设计**：`example-gateway` **按 `gateway-design.md` 全文裁剪落地**（路由、验签装配、身份注入、CORS、放行、错误体、访问日志、超时、与统一认证中心约定 §4.10）；本文只登记 example 的服务路由与配置取值，不在此重复网关通用约定
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
example-gateway（薄边缘治理 · 设计见 gateway-design.md）
  │  JWT 验签（JWKS/iss/aud）+ X-User-* 注入 + CORS + 路由
  ▼
example-service（业务 + RBAC + 文件 + 审计；身份只用 X-User-*，不做用户鉴权）
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
└── example-gateway                    # 薄边缘治理（设计 = gateway-design.md 裁剪，见下表）
frontend/example-admin/                # 管理端（Vue3 + TS + Pinia + Element Plus）
```

| 模块 | 组件（版本**只**取 `docs/version-baseline.md`，先改表再改 pom；**禁止在本文复制版本号**） |
|------|------|
| `example-common` | Lombok + 注解类（jakarta.validation / jackson-annotations）；⛔ 禁 MP/Redis/数据源/Actuator/MQ/MinIO/JWT 库/Hutool |
| `example-framework` | starter-web / validation / aop / actuator / data-redis + **Redisson** + **MyBatis-Plus（boot3 starter）** + OpenFeign + **springdoc** + **micrometer-tracing** + **MinIO SDK 封装**（上传/预签名/删除策略统一封装于此，禁止业务直绑 SDK）；具体坐标与版本见基线 |
| `example-api` | spring-cloud-starter-openfeign + Lombok |
| `example-service` | example-common/framework/api + mysql-connector-j + MapStruct + nacos-discovery/config（SCA，版本见基线） |
| `example-gateway` | **按 `gateway-design.md` §3.2 依赖清单**：spring-cloud-starter-gateway + **spring-cloud-starter-loadbalancer（`lb://` 必需）** + nacos-discovery + oauth2-resource-server（JWKS，**不引 SAS**）+ actuator + Micrometer Tracing；只依赖 `example-common`，**不依赖** `example-framework` |

**`example-gateway` 落地裁剪（细则一律见 `gateway-design.md`，此处仅 example 取值）：**

| 项 | example 取值 |
|----|----------------|
| 路由 | `Path=/api/**` → `lb://example-service`；**不**路由 `/internal/**`（对内走 Feign） |
| JWKS / issuer / audience | `jwk-set-uri` / `allowed-issuer` 指向认证中心；`allowed-audience` = 本系统登记 client/aud（`docs/test-env.md`） |
| `public-paths` | 最小集（health 等）；禁止 `/internal/**`、禁止 `/api/**` 宽放行 |
| `local-pass-through` | 仅 `application-local.yml` 可 `true`；默认与 test **必须 `false`** |
| 错误体 / 访问日志 / 超时 | 按 `gateway-design.md` §4.7–§4.9 |

**Spring Cloud 能力映射**：注册/发现 = Nacos；配置 = Nacos + `application-local/-test.yml`；服务间调用 = OpenFeign + Fallback + 固定超时；边缘 = Gateway（细则 `gateway-design.md`：路由 / JWT 验签 / `X-User-*` 注入 / CORS）；链路 = Micrometer Tracing（traceId 进 MDC）；监控 = Actuator。

**包结构**（`CLAUDE.md` §5.6.2）：`com.qjj.example.{module}` → controller / dto / vo / service / bo / converter（MapStruct 唯一落点）/ mapper / entity / feign / config / constants / enums / util；调用链 Controller → Service → Mapper。

**`example-framework` 横切落点（实现约定，业务禁止另起一套）：**

| 能力 | 落点 | 默认值形态（可配，键名 kebab-case） |
|------|------|-------------------------------------|
| 发号 | `IdGenerator` 段式（`sys_sequence`，日切 `Asia/Shanghai`） | 段长 `500` |
| 审计填充 | `MetaObjectHandler` 写 `createBy/updateBy/createTime/updateTime` | `X-User-Id` → Long |
| 逻辑删除 | `@TableLogic`（`deleted` 0/1） | — |
| Long 序列化 | 全局 `ObjectMapper` 出参 Long→String | — |
| 分页互转 | MyBatis-Plus `Page` ↔ `{records,total,size,current}` | `current≥1`，`size` 上限 `200` |
| `@RateLimit` | Redisson `RRateLimiter` | 上传 `10/min/用户`；列表/预签名 `60/min/用户`（示例，随变更可调） |
| `@Idempotent` | Redis `SET NX` + 首次 `Result` 回放 | TTL `24h`；键 = `Idempotent-Key` 或 `X-Request-Id` |
| 上传策略 | MinIO 封装 | 单文件 ≤10MB；MIME 白名单：`image/png,image/jpeg,image/gif,application/pdf`；预签名 TTL `10min` |
| Feign | 连接/读取超时 + Fallback | 连接 `1s` / 读取 `3s`（示例） |
| 日志 | Logback + MDC `traceId`/`spanId` | 模式对齐 `CLAUDE.md` §6.11.1 |

**操作审计写入：** 关键写在 **Service 事务提交成功后**写 `example_audit_log`（或 AOP `@Audit`，二者只选一种并统一）；失败记 `result=FAIL` 且不吞业务异常；禁止记密码/完整令牌。

---

## 4. 核心能力（演示清单）

| 能力 | 演示落点 |
|------|----------|
| **RBAC 全套** | `rbac-design.md` §2 4 表同构入 `example_db`；菜单管理（树 CRUD）+ 角色管理（分页/角色-菜单授权/用户-角色分配）+ `/me/menus`（动态路由下发）+ `/me/permissions`（按钮显隐/软校验，**不用 `@PreAuthorize` 拦接口**，纯网关登录态）；**菜单树 CRUD 与角色分页同时承担「完整 CRUD + 分页」示范**（无示例业务表） |
| **写幂等** | RBAC 管理、文件删除等关键写接口 `@Idempotent`（`Idempotent-Key`，缺省 `X-Request-Id`）；重复请求返回**首次 `Result`**；TTL 见 §3 横切表 / §5.3 |
| **接口限流** | 文件上传、查询列表 `@RateLimit`（RRateLimiter）；超限 HTTP 429 + 系统码 `10003`；维度/阈值见 §3 / §5.3 |
| **操作审计** | `example_audit_log`：RBAC 与文件关键写记录操作人/时间/动作/结果（载体 = 审计表） |
| **上传** | MinIO：单文件 ≤10MB、类型白名单、UUID 文件名、元数据落库、预签名 URL、逻辑删除（`CLAUDE.md` §6.11） |
| **对内契约** | `example-api`：文件元数据查询 + ping 探针，Feign + Fallback + 固定超时（见 §5.1） |
| **发号** | 16 位定长（`yyyyMMdd` + 8 位日序列，`Asia/Shanghai` 日切），`sys_sequence` 段式发号 |
| **基础契约** | 统一 `{code,msg,data}`、错误码枚举、全局异常、分页 `{records,total,size,current}`、Long→String、MapStruct 转换 |

---

## 5. 接口与配置契约（`/api/v1`，统一 `{code,msg,data}`，`id` 出参 String）

### 5.1 对内契约（进 `example-api` Feign，仅服务间调用，不对公网暴露）

| 方法 | 路径 | 用途 | 契约要点 |
|------|------|------|----------|
| GET | `/internal/files/{id}` | 查文件元数据 | VO：`id` String、`bucket`、`objectKey`、`originalName`、`contentType`、`sizeBytes`；未命中 Fallback 返回约定空/降级 |
| GET | `/internal/ping` | 健康探针 | VO：`pong` + 可选时间戳；Fallback 返回降级标记，演示不抛裸异常 |
| GET | （用户中心）`user-api` 查用户列表/详情 | 角色分配「选人」只读 | 走用户中心 OpenFeign 契约；**禁止**直连其库表；须 Fallback |

内网前缀 `/internal/**`：**网关不路由**；服务间 Nacos 发现 + Feign。

### 5.2 管理 API（经 `example-gateway`，供 `example-admin` 调用）

权限标识：`example:resource:action`（全小写，如 `example:menu:list`）；前端按钮与后端软校验同一字符串。

| 方法 | 路径 | 权限码 | 用途 | 横切 |
|------|------|--------|------|------|
| GET | `/api/v1/menus/tree` | `example:menu:list` | 菜单树查询 | |
| POST | `/api/v1/menus` | `example:menu:create` | 新建菜单 | `@Idempotent` + 审计 |
| PUT | `/api/v1/menus/{id}` | `example:menu:update` | 更新菜单 | `@Idempotent` + 审计 |
| DELETE | `/api/v1/menus/{id}` | `example:menu:delete` | 删除菜单 | `@Idempotent` + 审计 |
| GET | `/api/v1/roles` | `example:role:list` | 角色分页 | `@RateLimit` |
| POST | `/api/v1/roles` | `example:role:create` | 新建角色 | `@Idempotent` + 审计 |
| PUT | `/api/v1/roles/{id}` | `example:role:update` | 更新角色 | `@Idempotent` + 审计 |
| DELETE | `/api/v1/roles/{id}` | `example:role:delete` | 删除角色 | `@Idempotent` + 审计 |
| PUT | `/api/v1/roles/{id}/menus` | `example:role:assign` | 角色-菜单授权 | `@Idempotent` + 审计 |
| POST | `/api/v1/users/{id}/roles` | `example:user:assign` | 用户-角色分配 | `@Idempotent` + 审计 |
| DELETE | `/api/v1/users/{id}/roles/{roleId}` | `example:user:assign` | 回收角色 | `@Idempotent` + 审计 |
| GET | `/api/v1/me/menus` | （登录即可） | 当前用户菜单树（动态路由） | |
| GET | `/api/v1/me/permissions` | （登录即可） | 当前用户权限码集合 | |
| GET | `/api/v1/users/options` | `example:user:pick` | 分配角色选人（经 `user-api`） | Feign + Fallback |
| POST | `/api/v1/files` | `example:file:upload` | 单文件上传 | `@RateLimit` + 审计 |
| GET | `/api/v1/files` | `example:file:list` | 文件分页 | `@RateLimit` |
| GET | `/api/v1/files/{id}` | `example:file:list` | 文件元数据 | |
| GET | `/api/v1/files/{id}/url` | `example:file:read` | 预签名 URL | `@RateLimit` |
| DELETE | `/api/v1/files/{id}` | `example:file:delete` | 逻辑删除 | `@Idempotent` + 审计 |
| GET | `/api/v1/audit-logs` | `example:audit:list` | 审计分页 | |

**关键入参/出参（字段级示范，其余随表结构）：**

| 接口 | 入参要点 | 出参要点 |
|------|----------|----------|
| 角色分页 | `current`、`size`、`name?`、`orderBy`∈`{createTime,updateTime,id}`、`order`∈`{asc,desc}` | `records[]`（`id` String、`name`、`code`、`remark`）、`total`、`size`、`current` |
| 菜单树/新建 | 树节点：`parentId`、`name`、`type` 1/2/3/4、`path`、`component`、`permission?`、`sort` | 树 VO；`id` String |
| 文件上传 | multipart `file`；服务端校验大小/MIME | `FileVO`（同对内 VO + `deleted`） |
| 预签名 | `id` | `{ url, expireAt }`（短 TTL） |
| 审计分页 | `current`、`size`、`action?`、`beginTime?`、`endTime?` | 分页 VO，`detail` 已脱敏 |

**分页排序白名单（禁止拼进 SQL）：** 一律后端白名单字段；非法 `orderBy` 拒绝或回落默认 `create_time`。

**错误码：** 系统段 `1xxxx` / 校验 `3xxxx` 对齐 `docs/error-code-ranges.md` 与 `example-common`；业务段 `2xxxxx` 实施时先登记系统号再进 `ExampleErrorCodeEnum`（不预占）。

### 5.3 配置契约（`example-service` / 可运行模块）

三份 yml（`CLAUDE.md` §6.10）：`application.yml`（无主机/账密）/ `application-local.yml` / `application-test.yml`。  
连接信息真相源：`docs/test-env.md`；端口登记：`docs/port-registry.md`。

```yaml
# application.yml（结构示意；敏感与主机值不得写入公共文件）
server:
  port: 0            # 具体端口以 port-registry.md 登记为准
spring:
  application:
    name: example-service
  profiles:
    active: local
example:
  idgen:
    segment-size: 500
  idempotent:
    default-ttl: 24h
  rate-limit:
    upload: 10/min/user
    query: 60/min/user
  minio:
    bucket: example
    upload-max-bytes: 10485760
    allowed-content-types:
      - image/png
      - image/jpeg
      - image/gif
      - application/pdf
    presign-ttl: 10m
  feign:
    connect-timeout: 1s
    read-timeout: 3s
```

| 键 | local | test / 生产 |
|----|-------|-------------|
| `spring.profiles.active` | `local` | `test`（生产经 Nacos/密钥管理） |
| Nacos / Redis / MySQL / MinIO | `application-local.yml` | `application-test.yml`；**禁止**进公共 yml；生产走 Nacos |
| `example.minio.allowed-content-types` | 白名单 | 白名单，禁止空=全放行 |
| `example.idempotent.default-ttl` 等 | 可缩短联调 | 生产按策略 |

**绑定：** `@ConfigurationProperties`（如 `ExampleMinioProperties`）；禁止散落 `@Value`。

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

- 登录：**正式 OIDC RP（public + PKCE S256）**，唯一登录门面在 `auth-portal`；本系统管理端**禁止**再放账密表单。  
  **首期可不做完整 OIDC 接线**（见 §11），壳阶段仅允许脚手架联调用 `local-pass-through`（不得上生产）。**一旦接 OIDC，必须遵守**（来自 unify-login-facade 实测缺陷，详见 `fixbug/2026-09-25-unify-login-sso-gateway.md`）：
  - IdP / 各 RP **同一 host**（本地一律 `localhost`，禁止与 `127.0.0.1` 混用——Cookie 按 host 隔离）
  - **页面路由与验密接口不同名**（如 `GET /login` 仅 portal；若需 form-login 验密用 `POST /api/login`）
  - SPA 调 `/oauth2/token` 时，**AS 安全链必须开 CORS**（只配业务默认链不够）
  - 会话恢复 Filter 必须在 SAS authorize **之前**（挂在 `SecurityContextHolderFilter` 之后）
  - 路由 `uri: lb://{service}` 的网关必须显式依赖 **LoadBalancer**（否则 503）
  - 网关侧 JWKS / `iss` / `aud` / `X-User-*` 按 `gateway-design.md`
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
| 8 | **登录鉴权：OIDC RP（PKCE）+ 网关 JWKS 验签**；唯一登录门面在 auth-portal | 对齐 `unify-login-facade` 实测结论：禁止各系统自带账密页；壳阶段本地放行仅限脚手架联调且不得上生产（详见 §7 与 `fixbug/2026-09-25-unify-login-sso-gateway.md`） |
| 8b | **`example-gateway` 设计裁剪源 = `gateway-design.md`** | 引入通用网关模板（验签/iss/aud/JWKS/身份注入/CORS/错误体/检查清单）；本文只写 example 路由与配置取值，不重复通用约定；纯网关鉴权，不用 `@PreAuthorize` 拦接口（对齐 `CLAUDE.md` §5.2） |
| 12b | **横切默认值仅作「形态示范」**（发号段长、限流/幂等 TTL、MIME 白名单、预签名 TTL 等） | 落地可按变更调整；**结构与键名**须与 §3/§5.3 一致；版本号不进本文 |
| 9 | **配置 local/test** 三份 yml；SQL 落 `deploy/db/migration/example_db/` **人工执行** | §6.10 / §2.2（不引 Flyway/Liquibase） |
| 10 | **错误码系统号实施时登记** `docs/error-code-ranges.md`，不预占 | 当前登记表为空（2026-09-24） |
| 11 | **`sys_file` 删除语义统一走 `deleted`，不另设 `status` 列** | 定稿确认（2026-09-24）；避免「状态删除」与逻辑删除双轨（备选「另设 status 列」否决） |
| 12 | **`example_audit_log` 采用通用 `target_type` / `target_id` 形态**（仿 `auth_audit_log`） | 定稿确认（2026-09-24）；审计对象覆盖 MENU/ROLE/FILE 多类型（备选 `user_audit_log` 的 `target_user_id` 形态否决） |

---

## 9. 安全约定

- 上传：类型白名单（应用层 + `content_type` 双校验）、单文件 ≤10MB、UUID 文件名、预签名 URL 短 TTL；逻辑删除后对象清理走运维脚本（不建定时，§2.2）
- `permission` 标识（首段 = `example`）与前端按钮同一字符串，仅作**软校验/按钮显隐**；**接口硬拦截只保证网关登录态**（纯网关鉴权，`gateway-design.md` / `CLAUDE.md` §5.2）；分页排序白名单；`#{}` 参数化 SQL
- 网关 JWT 验签 + `X-User-*` 注入；服务内以注入值为准，不信可伪造客户端头；**禁止**未验签手拆 JWT（`gateway-design.md` §4.2.5）
- 网关错误体、访问日志脱敏、`local-pass-through`、`public-paths`、`iss`/`aud`、JWKS 缓存均按 `gateway-design.md` 执行
- **OIDC/SSO 落地红线**（缺陷根因归纳）：Cookie host 与 issuer 一致；`GET` 登录页与 `POST` 验密路径分离；token/logout 跨域响应带 CORS；SSO 过滤器早于 authorize；统一登出须吊销 RT **且**删除授权对象；网关 `lb://` 必须带 LoadBalancer（详见 `gateway-design.md` §9 与 fixbug）
- 审计与日志禁密码/完整令牌；手机号脱敏工具在 `example-common`；生产密钥禁止入仓（连接信息见 `docs/test-env.md`）

---

## 10. 部署、自检与验收

### 10.1 部署步骤

1. 测试环境建库 `example_db`（utf8mb4），人工执行 `deploy/db/migration/example_db/` SQL（7 表；**生产执行前须按 `CLAUDE.md` 第 7 节协作红线征询**）
2. 准备 Redis、MinIO（建桶，白名单 MIME）、Nacos；部署 `example-gateway`、`example-service`（profile=`test`），健康检查 `/actuator/health`
3. Nginx：托管 `example-admin` 静态（history 路由 fallback 到 `index.html`）；反代 `/api` → `example-gateway`（信任域名登记 `docs/test-env.md`；配置入 `deploy/nginx/` 或等价路径）
4. 冒烟：菜单/角色 CRUD + 授权 → `/me/menus` 动态路由 → 上传/预签名/逻辑删除 → 幂等重复提交返回首次 Result → 限流 429（`10003`）→ 对内 Feign `ping`/`files/{id}` + Fallback 降级；**网关 `lb://` 到 service 任一 `/api/**` 非 503**（LoadBalancer 依赖齐全）；并按 `gateway-design.md` §10「联调期」勾选网关项（`X-User-*`、伪造头剥除、401/403 错误体、`iss`/`aud`、`local-pass-through=false`）
5. 回滚：下线服务 + 保留库与 MinIO 快照（无破坏性数据变更）

### 10.2 提交前自检（按 `CLAUDE.md` §3.2）

```bash
cd frontend/example-admin && npm run lint && npm run type-check
cd backend/example && mvn -q compile
```

- [ ] 编译 / lint / type-check 通过（命令缺失则先在脚手架补齐，不得跳过）
- [ ] 无 `console.log` / `System.out.println` 调试残留
- [ ] 无生产密钥；测试账密仅 `docs/test-env.md` / local|test yml
- [ ] 依赖版本只来自 `docs/version-baseline.md`，正文与 pom 无双源版本
- [ ] 网关检查清单（`gateway-design.md` §10 工程期）已勾选
- [ ] 改动范围与缺陷/已批准变更对应，无顺手重构（`CLAUDE.md` §7.8）

### 10.3 验收清单（模板能力）

- [ ] 五模块可独立 `mvn` 构建；gateway 可路由到 service
- [ ] RBAC 菜单/角色/授权 + `/me/*` 动态路由与按钮显隐
- [ ] 幂等重复提交返回首次 `Result`；限流 429 + `10003`
- [ ] 上传 → 列表 → 预签名 → 逻辑删除；超限/非白名单 MIME 拒绝
- [ ] 对内 Feign + Fallback；审计关键写有记录
- [ ] 16 位发号与 Long→String 出参；错误体 `{code,msg,data}`

---

## 11. 非目标与演进

**首期不做：** 示例业务表、批量上传、**完整 OIDC/SSO 接线**（壳阶段可联调用 local 放行；接线清单见 §7，随认证中心定型落地）、缓存与查询性能优化、组织模型与 `data_scope=2` 强制、Excel、MinIO 对象定时清理、跨系统角色同步。

**预留 / 后续：** 各系统在本模板上增补业务表与 `{domain}-api` 契约；`user-admin`/本系统 OIDC 接线随认证中心定型；前端主框架版本随首个前端工程落地登记 `docs/version-baseline.md`；正式接 OIDC 时按 §7 与 `gateway-design.md` 勾选验收。

---

## 12. 相关文档

| 文档 | 内容 |
|------|------|
| `CLAUDE.md` | 项目全局契约（本系统无偏差豁免） |
| `gateway-design.md` | **通用网关设计**（`example-gateway` 唯一裁剪源：路由/JWKS/`X-User-*`/CORS/错误体/清单） |
| `rbac-design.md` | RBAC 通用 4 表与模型约定 |
| `admin-ui-design.md` | 管理端 UI/布局约定（`example-admin` 可参照） |
| `user-center-design.md` | 用户中心（账号权威；角色分配"选人"可经其 `user-api`） |
| `unified-auth-center-design.md` | 认证中心（网关 JWKS 验签指向方） |
| `docs/template/fixbug/2026-09-25-unify-login-sso-gateway.md` | 网关/SSO 实测缺陷与避坑 |
| `docs/version-baseline.md` | 组件版本基线（唯一取用处） |
| `docs/port-registry.md` | 端口登记（example 网关/服务端口在此规划） |
| `docs/error-code-ranges.md` | 错误码系统号登记处 |
| `docs/test-env.md` | 环境 IP/端口/账密/信任域名 |
| `docs/jenkins-pipeline-guide.md` | 流水线（发布与质量门，不归本文展开） |
