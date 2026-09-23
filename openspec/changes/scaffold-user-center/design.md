## Context

动机与范围见 `proposal.md`；行为契约见 `specs/user-account/spec.md`、`specs/user-query/spec.md`。前期设计稿（`docs/template/user-center-design.md`、`user-center-database.md`）为**参考模板**，本变更以其为基础，但按以下**偏差登记**执行（`CLAUDE.md` 为契约真相，模板不得覆盖）：

| 模板写法 | 本变更执行 | 依据 |
|---|---|---|
| 主键 14 位（yyMMdd + 当日序号） | **16 位** = 业务日期 `yyyyMMdd`（8 位）+ 当日序列（8 位），`Asia/Shanghai` 日切 | `CLAUDE.md` §6.4.5（已裁决） |
| 配置 `application-dev.yml` / `-test.yml` | `application.yml` / `application-local.yml` / `application-test.yml` | `CLAUDE.md` §6.10（已裁决） |
| 「迁移目录」字样 | SQL 落 `deploy/db/migration/user_db/`，**人工执行**；不引 Flyway/Liquibase | `CLAUDE.md` §2.2 / §6.5 |
| 模块清单未列网关 | **补 `user-gateway`**（薄边缘层） | `CLAUDE.md` §5.1 硬约束（前端→Nginx→网关→服务）与 §5.2（X-User-* 注入归属网关）；设计稿为省略项 |

约束回顾：Java 21 / Spring Boot 3.5.16 线（`docs/version-baseline.md`）；包名前缀 `com.qjj`；`user_db` 仅 `user-service` 直连；跨系统只走 `user-api`。

## Goals / Non-Goals

**Goals:**

- 可独立构建、独立部署的 `user` 系统（多系统容器下第一个落地系统，作为后续系统的脚手架参照）
- 完整基础框架链路一次到位：`common`/`framework` 分层、16 位发号、审计字段自动填充、Long 出参 String、统一返回/异常/分页、`@RateLimit`/`@Idempotent` 可用
- 对内契约 `user-api` 稳定可依赖（认证中心下一变更的输入）

**Non-Goals:**

- 凭证/登录联动编排（与认证中心建号一致流程）、动态菜单/权限模型、组织架构
- 缓存与查询性能优化（普通查询达标即可，`CLAUDE.md` §6.9）
- 前端 OIDC 真实接入（依赖认证中心，见 Decisions）

## Decisions

1. **模块与包结构**（对齐 `CLAUDE.md` §5.4/§5.6）：
   - `user-common`：Result/错误码枚举/业务异常/分页契约/BaseEnum/常量/脱敏等纯工具（零额外配置）
   - `user-framework`：MyBatis-Plus、Redis/Redisson、审计填充（MetaObjectHandler）、`@TableLogic`、Long 序列化、发号器、`@RateLimit`/`@Idempotent`、Swagger 装配
   - `user-api`：仅 DTO/VO + OpenFeign 接口 + Fallback（**无** Entity/Mapper/实现）
   - `user-service`：Entity/Mapper/Service/Controller（本域 4 表）；三份配置；可运行
   - `user-gateway`：薄边缘治理——路由到 `user-service`、JWT 验签（resource-server，JWKS 指向认证中心）、注入/覆盖 `X-User-*`、CORS 豁免（统一策略归网关，本期本系统内自配）
   - 包名：`com.qjj.user.{module}`；MapStruct 转换器只放 `converter/`；Controller→Service→Mapper
   - 备选：不建 `user-gateway`、由 `user-service` 自验 JWT——否决，违反 §5.1 硬链路与 §5.2 身份注入归属
   - **组件落位（已确认，版本一律取 `docs/version-baseline.md`）**：`user-common` = Lombok + 注解类（零配置）；`user-framework` = starter-web/validation/aop/actuator/data-redis + Redisson 3.52.0 + MyBatis-Plus 3.5.17 + OpenFeign + springdoc 2.9.1 + micrometer-tracing-bridge-brave；`user-api` = OpenFeign + Lombok（无 Entity）；`user-service` = mysql-connector-j + MapStruct + nacos-discovery/config（SCA 2025.0.0.0）；`user-gateway` = spring-cloud-starter-gateway + nacos-discovery + oauth2-resource-server + actuator。**不引入** MinIO / RocketMQ / SAS / Hutool / Druid
2. **发号实现**（本变更向 `CLAUDE.md` §6.4.5 登记实现细节）：`user-framework` 提供 `IdGenerator`，**段式 DB 发号**——`sys_sequence`（`uk_sys_sequence_seq_date`）按业务日取号段（步长 500）缓存于内存，日切换 `seq_date` 重新计数；组装 `yyyyMMdd` + 8 位零填充序列 = 16 位。备选 Redis INCR：性能更优但把正确性押在 Redis 可用性上，否决（幂等/限流已在 Redis，发号保持 DB 独立正确性）。重启丢号段属可接受浪费。
3. **对内查询通道**：`user-api` Feign 经 Nacos 服务发现**内网直连** `user-service`（不经网关，服务间调用为内网信任边界，§5.1「内网调用」）；必须 Fallback + 连接/读取超时（`user-framework`/网关固定值）；禁止事务内调用。管理写接口走 `user-gateway`（JWT 验签 + `X-User-*`）。
4. **接口契约**：对内 3 个（by-id / by-username / profile）进 `user-api` Feign；管理 4 个（分页列表 / 创建 / 更新 / 启停锁定）为 `user-service` REST（`/api/v1/users...`），仅 `user-admin` 调用。全部统一 `{code,msg,data}`，`id` 出参 String；手机号/邮箱出参脱敏（工具在 `user-common`）。
5. **数据模型**：4 表按模板结构执行（含偏差登记）：`sys_sequence` / `sys_user` / `sys_user_profile` / `user_audit_log`；审计 5 字段 + `deleted`；禁止凭证字段；`username` 全局唯一（`uk_sys_user_username`）；索引命名 `idx_/uk_` + 表名 + 字段，单表 ≤ 5。
6. **错误码**：写第一个业务错误码前先在 `docs/error-code-ranges.md` 登记系统号（按登记顺序分配）；业务码进 `UserErrorCodeEnum`；系统段/校验段用固定码值表。
7. **前端 `user-admin`**：按 `CLAUDE.md` §5.5 壳结构（api/views/components/stores/router/types/utils）；Element Plus + `<script setup>`；路由 meta（title/icon/hidden/requiresAuth）；id 一律 string。**登录接线**：预留 token/pkce 工具位，壳阶段守卫放行 + 本地联调；真实 OIDC 接入在 `scaffold-auth-center` 落地后按其发码端点接线（见 Open Questions）。
8. **配置**：三份 yml（无主机账密进 `application.yml`）；连接信息与 `test-env.md` 一致；敏感项测试值仅入 `test-env.md`/local 配置。

## Risks / Trade-offs

- [壳阶段前端无真实登录，鉴权链路未端到端验证] → 网关 JWT 验签先行实现（可对 mock 令牌联调）；OIDC 端到端在 auth 变更的验收里补
- [段式发号重启浪费号段] → 步长 500、8 位日序列容量 1e8，浪费无害
- [模板偏差被执行者误用] → 偏差登记表置于本文件首节；实施只按 `tasks.md`
- [`user-api` 契约演进影响下游] → 字段最小集；破坏性变更升版本前缀（§6.2）

## Migration Plan

1. 测试环境建库 `user_db`（utf8mb4），执行 `deploy/db/migration/user_db/` 下 SQL（人工，含 4 表与索引）
2. 部署 `user-gateway`、`user-service`（Nacos 注册，profile=`test`），健康检查 `/actuator/health`
3. 冒烟：创建用户 → by-username 查询 → 启停 → 列表分页；错误路径（重复 username / 未认证 401）
4. 回滚：下线服务即可（无破坏性数据变更；建表 SQL 可逆为 DROP，执行前已按 §7 征询）

## Open Questions

- `user-admin` 的测试域名/路径前缀在 `docs/test-env.md` §3 登记时定（不阻塞本变更）
- OIDC 接入细节（redirect_uri 注册、PKCE 接线）随 `scaffold-auth-center` 定型后补接
