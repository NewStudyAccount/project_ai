# Design: scaffold-user-center

## Context

多系统容器（`CLAUDE.md` §4）下首个落地的共享基础系统。设计已逐项确认于 `docs/template/user-center-design.md`（2026-09-24 稿，含表结构与接口清单）与 `rbac-design.md`（RBAC 通用 4 表）；本文件只提炼**实施相关技术决策**，字段级定义以设计稿为准、契约口径以 `CLAUDE.md` 为准，不另立口径。

现状：`backend/`、`frontend/` 容器目录已建、工程内容为空；`openspec/specs/` 无既有能力；认证中心尚未落地（本系统先行，`user-api` 为其依赖起点）。

## Goals / Non-Goals

**Goals:**

- 落地 `backend/user/` 五模块（`user-common` / `user-framework` / `user-api` / `user-service` / `user-gateway`）与 `frontend/user-admin/`
- 交付 `user-account`（账号生命周期 + 管理审计）、`user-query`（对内查询契约）、`user-rbac`（本系统自持 RBAC）三项能力
- 建库 `user_db`（8 表）+ 建表 SQL（`deploy/db/migration/user_db/`，人工执行）
- 跑通 §3.2 自检：`npm run lint && npm run type-check`、`mvn -q compile`

**Non-Goals:**

- 同 proposal Non-goals（凭证、C 端、组织模型、跨系统 RBAC 同步、建号编排、分布式事务/共享 Entity、Excel/分布式定时/多语言）
- 不做缓存与查询性能优化、不做真实 OIDC 接线（壳阶段登录本地放行）

## Decisions

| # | 决策 | 备选与否决理由 |
|---|------|----------------|
| 1 | **16 位发号** = `yyyyMMdd`（8 位，`Asia/Shanghai` 日切）+ 当日序列（8 位）；`sys_user.id` = OIDC `sub` | 覆盖旧模板 14 位（`CLAUDE.md` §6.4.5 已裁决） |
| 2 | **段式 DB 发号**：`sys_sequence` 按业务日取号段（步长 500）缓存内存，重启丢段可接受；`IdGenerator` 直接 SQL 操作，不走业务 Entity | 备选 Redis INCR 否决：正确性不押 Redis 可用性 |
| 3 | **五模块 + `user-gateway`**（薄边缘治理：路由 / JWT 验签 / `X-User-*` 注入 / CORS） | 旧模板模块清单未列 gateway；按 `CLAUDE.md` §5.1 硬链路与 §5.2（`X-User-*` 归网关注入）补全；备选无网关直连否决 |
| 4 | **RBAC 各系统自持**，按 `rbac-design.md` §2 同构 4 表（无 `system_code`），建于 `user_db` | 数据自治 + 运行时本地鉴权零跨系统调用（已裁决） |
| 5 | **`sys_user_profile` 首期建**（与 `sys_user` 1:1，含 `extra_json` 轻量扩展） | 忠实设计稿"可选拆分"的首期建裁决 |
| 6 | **对内 Feign 服务间直连**（Nacos 发现）+ Fallback + 固定超时；管理 API 经 `user-gateway` 验签（JWKS 指向认证中心，不引 SAS） | 内网信任边界（`CLAUDE.md` §5.1）；对内接口仅服务间调用、不对公网暴露 |
| 7 | **表结构 8 张**：`sys_sequence` / `sys_user` / `sys_user_profile` / `user_audit_log` + RBAC 4 表；字段定义见 `docs/template/user-center-design.md` §6 与 `rbac-design.md` §2，实施按其落 SQL | 不在本文件复制字段表（防双源漂移） |
| 8 | **SQL 落 `deploy/db/migration/user_db/` 人工执行** | 不引 Flyway/Liquibase（`CLAUDE.md` §2.2） |
| 9 | **配置 local/test**：`application.yml` + `application-local.yml` + `application-test.yml`；连接信息登记 `docs/test-env.md` | `CLAUDE.md` §6.10 |
| 10 | **前端壳阶段登录本地放行**，真实 OIDC（redirect_uri / PKCE）随认证中心落地接线 | 不阻塞脚手架联调（设计稿决策 9） |
| 11 | **错误码**：实施时先在 `docs/error-code-ranges.md` 登记系统号，再进 `UserErrorCodeEnum` | `CLAUDE.md` §6.4.2；登记表当前为空 |
| 12 | **组件版本**一律取 `docs/version-baseline.md`（Boot 3.5.16 / SC 2025.0.3 / SCA 2025.0.0.0 / MP 3.5.17 / Redisson 3.52.0 / springdoc 2.9.1 / MapStruct 1.6.3）；前端主框架版本**先登记该表**再锁 `package-lock.json` | 禁止各系统各锁各的版本（`CLAUDE.md` §2.1） |

模块组件落点（需配置/中间件组件只进 `user-framework`，`CLAUDE.md` §2.1 / §5.4）：

- `user-common`：Lombok + 注解类（jakarta.validation / jackson-annotations）；⛔ 禁 MP/Redis/数据源/Actuator/MQ/MinIO/JWT 库/Hutool
- `user-framework`：starter-web / validation / aop / actuator / data-redis + Redisson + MyBatis-Plus（boot3 starter）+ OpenFeign + springdoc + micrometer-tracing-bridge-brave；统一装配审计字段填充、`@TableLogic`、Long 序列化、发号、`@RateLimit` / `@Idempotent`
- `user-api`：spring-cloud-starter-openfeign + Lombok（DTO/VO + Feign + Fallback，无 Entity/Mapper/实现）
- `user-service`：user-common/framework/api + mysql-connector-j + MapStruct + nacos-discovery/config
- `user-gateway`：spring-cloud-starter-gateway + nacos-discovery + oauth2-resource-server + actuator

包结构对齐 `CLAUDE.md` §5.6.2（`com.qjj.user.{module}` → controller/dto/vo/service/bo/converter/mapper/entity/feign/config/constants/enums/util）；调用链 Controller → Service → Mapper；MapStruct 只在 `converter/`。

鉴权对齐 `CLAUDE.md` §5.2 双重鉴权：网关 JWT 验签（JWKS）+ 注入 `X-User-*`；服务内 `@PreAuthorize`（`permission` 首段 = `user`）；服务内以网关注入值为准，不信客户端头。

接口清单对齐设计稿 §5（对内 3 个 + 管理 10 组），数据模型对齐设计稿 §6（8 表）与 `CLAUDE.md` §6.5（命名/索引/禁 `SELECT *`）。

## Risks / Trade-offs

- [段式发号重启丢号段] 只损失连续性，不损唯一性 → 接受；步长 500 内浪费可忽略
- [壳阶段登录放行存在越权窗口] 仅限 local/test 联调 → 代码留 OIDC 接线注释；生产部署前必须接认证中心（部署清单强制项）
- [对内查询接口暴露面] `by-id` / `by-username` / `profile` 含用户数据 → 仅服务间内网调用（网关不路由 `/internal` 或等价前缀），手机号/邮箱出参脱敏（`CLAUDE.md` §6.7）
- [RBAC 与用户数据同库] 4 表按通用模板建于 `user_db`，将来拆分成本 → 各系统本就自持一套，本库一套属本系统自用；表名 `sys_*` 不跨库混用
- [手工执行 SQL 漂移] 无迁移框架 → SQL 入仓（`deploy/db/migration/user_db/`）+ 生产执行前征询（`CLAUDE.md` §7）

## Migration Plan

1. 测试环境建库 `user_db`（utf8mb4），人工执行建表 SQL（8 表；生产执行前征询）
2. 部署 `user-gateway`、`user-service`（Nacos 注册，profile=`test`），健康检查 `/actuator/health`
3. Nginx 反代 `/api` → `user-gateway`；托管 `user-admin` 静态（信任域名登记 `docs/test-env.md`）
4. 冒烟：创建用户 → by-username 查询 → 启停 → 列表分页 → 菜单/角色 CRUD + 授权 → `/me/menus`；错误路径（重复 username / 未认证 401 / 无权限 403）
5. 回滚：下线服务即可（建表可逆 DROP，已征询）

## Open Questions

- 对内接口是否统一挂 `/internal` 前缀由网关拒路由，还是仅靠 Nacos 内网不暴露（实施时定，写入 `tasks.md` 对应任务的验收）
