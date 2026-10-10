# Design: scaffold-blog-system

## Context

多系统容器（`CLAUDE.md` §4）下新建业务系统 **`blog`**，落地形态与横切能力**以 `docs/template/example-system-design.md` 为准**（基础模板：完整标准链路 + 横切能力示范，不预置业务表）；网关以 `gateway-design.md` 为唯一裁剪源；对象存储以 **`object-storage-design.md`** 为唯一裁剪源；RBAC 4 表同构 `rbac-design.md` §2。本文件只提炼 **blog 命名落地**的实施决策与取值，字段级定义以设计稿为准、契约口径以 `CLAUDE.md` 为准，不另立口径。

现状：`backend/`、`frontend/` 容器已建；`openspec/specs/` 无既有能力；用户中心已有 `user-api` 可供选人只读；认证中心 JWKS 为网关验签指向方。`scaffold-example-system` 空壳可废弃，以本变更为准。

命名映射（example → blog）：

| 设计稿 | 本系统 |
|--------|--------|
| `example-common/framework/api/service/gateway` | `blog-common/framework/api/service/gateway` |
| `frontend/example-admin` | `frontend/blog-admin` |
| `example_db` | `blog_db` |
| `example_audit_log` | `blog_audit_log` |
| `com.qjj.example.*` | `com.qjj.blog.*` |
| `example:resource:action` | `blog:resource:action` |
| `ExampleMinioProperties` 等 | `BlogMinioProperties` 等 |

## Goals / Non-Goals

**Goals:**

- 落地 `backend/blog/` 五模块 + `frontend/blog-admin/`，忠实 `example-system-design.md` §2/§3 标准链路与模块划分（系统名 blog）
- 交付 `blog-rbac` / `blog-file` / `blog-audit` / `blog-internal-api` 四项能力（对齐设计稿 §4 演示清单）
- 建库 `blog_db`（7 表）+ 建表 SQL（`deploy/db/migration/blog_db/`，人工执行）
- 横切默认值形态与键名对齐设计稿 §3/§5.3（可配，不锁死参数值）
- 跑通 `CLAUDE.md` §3.2 自检：`npm run lint && npm run type-check`、`mvn -q compile`

**Non-Goals:**

- 同 proposal Non-goals（博客业务表、用户主数据/凭证、跨系统 RBAC、完整 OIDC、批量上传/Excel/分布式定时/多语言等）
- 不做缓存与查询性能优化；不做 MinIO 对象定时清理

## Decisions

| # | 决策 | 备选与否决理由 |
|---|------|----------------|
| 1 | **定位：按 example 模板落地业务系统 `blog`，不带业务表** | 对齐设计稿决策 1；CRUD+分页示范由 RBAC 管理页与文件/审计列表承担；博客文章等业务随后续变更 |
| 2 | **五模块 + `blog-gateway` + `blog-admin`** | 对齐设计稿决策 2 与 `CLAUDE.md` §5.1 硬链路；备选无 api / 无 gateway 否决 |
| 3 | **横切四项全选**：RBAC 全套、幂等+限流+审计、Feign 契约+Fallback、MinIO 上传 | 对齐设计稿决策 3 |
| 4 | **表 7 张**：`sys_sequence` + RBAC 4 + `blog_audit_log` + `sys_file` | 对齐设计稿决策 4；RBAC 按 `rbac-design.md` §2 无 `system_code` |
| 5 | **`blog-api` 演示文件元数据 + ping** | 对齐设计稿决策 5；真实可跑且不背对用户中心强依赖 |
| 6 | **上传生命周期：上传+查+删+预签名**（无批量） | 对齐设计稿决策 6 |
| 7 | **16 位发号** = `yyyyMMdd` + 8 位日序列（`Asia/Shanghai`）；`sys_sequence` 段式（段长默认 500） | `CLAUDE.md` §6.4.5；备选 Redis INCR 否决（正确性不押 Redis） |
| 8 | **纯网关鉴权**；权限码 `blog:resource:action` 仅软校验/按钮显隐；**不用** `@PreAuthorize` 拦接口 | 对齐设计稿决策 8/8b、`CLAUDE.md` §5.2（与 user 系统双鉴权表述不同，以设计稿与网关模板为准） |
| 9 | **`blog-gateway` 裁剪源 = `gateway-design.md`** | 路由 `Path=/api/**` → `lb://blog-service`；不路由 `/internal/**`；`local-pass-through` 仅 local 可 true |
| 9b | **对象存储裁剪源 = `object-storage-design.md`** | MinIO 封装/上传/预签名/逻辑删除/`sys_file`/配置键以该文为准；文件页交互对齐设计稿 §7.1 与 `assets/rbac-admin-proto.html`；本文只写 blog 取值 |
| 10 | **`sys_file` 删除 = `deleted` 逻辑删除**，不另设 status | 设计稿决策 11 / `object-storage-design.md` §4.3 |
| 11 | **审计表通用 `target_type`/`target_id`** | 设计稿决策 12 |
| 12 | **SQL 落 `deploy/db/migration/blog_db/` 人工执行** | 不引 Flyway/Liquibase（`CLAUDE.md` §2.2） |
| 13 | **配置三份 yml**；键名 kebab-case + `@ConfigurationProperties` | `CLAUDE.md` §6.10；连接信息 `docs/test-env.md` |
| 14 | **错误码系统号实施时登记** `docs/error-code-ranges.md`，再进 `BlogErrorCodeEnum` | 不预占；系统段 `10001–10004`/校验 `30001` 对齐固定表 |
| 15 | **版本一律取 `docs/version-baseline.md`**；前端主框架版本先登记再锁 lock | `CLAUDE.md` §2.1 |
| 16 | **壳阶段登录可 local 放行**；正式 OIDC RP（PKCE）随认证中心接线，遵守设计稿 §7 红线 | 不阻塞脚手架；生产前必须接线 |

模块组件落点（需配置/中间件只进 `blog-framework`，`CLAUDE.md` §2.1/§5.4）：

- `blog-common`：Result/错误码/异常/分页契约/BaseEnum/常量/脱敏工具；⛔ 禁 MP/Redis/数据源/Actuator/MQ/MinIO/JWT/Hutool
- `blog-framework`：web/validation/aop/actuator/data-redis + Redisson + MP + OpenFeign + springdoc + micrometer-tracing + **MinIO SDK 封装**（契约 `object-storage-design.md` §3，禁业务直绑 SDK）；发号/审计填充/`@TableLogic`/Long 序列化/分页互转/`@RateLimit`/`@Idempotent`/上传策略
- `blog-api`：openfeign + Lombok（DTO/VO + Feign + Fallback，无 Entity）
- `blog-service`：blog-common/framework/api + mysql-connector-j + MapStruct + nacos
- `blog-gateway`：gateway + **loadbalancer** + nacos-discovery + oauth2-resource-server（JWKS）+ actuator + tracing；只依赖 `blog-common`

包结构 `com.qjj.blog.{module}` 对齐 `CLAUDE.md` §5.6.2；调用链 Controller → Service → Mapper；MapStruct 只在 `converter/`。

接口与配置契约对齐设计稿 §5（对内 2 个 + 管理 API 表、字段级示范、限流/幂等落点）与 §5.3 配置键（`blog.idgen` / `blog.idempotent` / `blog.rate-limit` / `blog.minio` / `blog.feign`）。数据模型对齐设计稿 §6（7 表字段）与 `CLAUDE.md` §6.5。

## Risks / Trade-offs

- [模板连带 MinIO/Redis 较重] → 接受（设计稿决策 3 已裁决）；版本经基线表统一
- [纯网关鉴权下细粒度权限码可被绕过] → 仅软校验/按钮显隐；硬拦截只保证登录态（已裁决）；敏感写另靠幂等与审计
- [壳阶段 local-pass-through 越权窗口] → 仅限 local 联调；生产 `false`；OIDC 接线清单强制项
- [手工 SQL 漂移] → SQL 入仓 + 生产执行前征询（`CLAUDE.md` §7）
- [对内接口暴露面] → `/internal/**` 网关不路由，仅 Nacos+Feign；Fallback 不抛裸异常

## Migration Plan

1. 建库 `blog_db`（utf8mb4），人工执行 7 表 SQL（生产前征询）
2. 准备 Redis、MinIO（桶 + MIME 白名单）、Nacos；部署 `blog-gateway`、`blog-service`（profile=`test`），`/actuator/health`
3. Nginx 托管 `blog-admin` 静态 + 反代 `/api` → `blog-gateway`（信任域名 `docs/test-env.md`）
4. 冒烟（设计稿 §10.1）：RBAC CRUD+授权 → `/me/menus` → 上传/预签名/逻辑删除 → 幂等首次 Result → 限流 429/`10003` → Feign ping/files + Fallback；网关 `lb://` 非 503；网关检查清单（`gateway-design.md` §10）
5. 回滚：下线服务，保留库与 MinIO 快照

## Open Questions

- 菜单删除存在子节点时：拒绝 vs 级联逻辑删除（实施定一种，验收跟实现；不预设）
- 业务段系统号具体值：实施时在 `docs/error-code-ranges.md` 分配（当前 01 已用）
