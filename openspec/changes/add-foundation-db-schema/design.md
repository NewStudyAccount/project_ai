# Design: add-foundation-db-schema

## Context

- 字段级设计已整理于 `docs/database-design.md`（表清单、关联字段、ER、索引）。
- 上游架构变更：`add-unified-auth-center`（单体自研认证、权限本地、用户权威在 auth）。
- 仓库约定：无物理外键；主键 14 位禁自增；审计五件套；表名单数 `snake_case`；`TINYINT` 状态；`utf8mb4`。
- 本变更只交付 **schema 契约与迁移**，不写业务代码。

## Goals / Non-Goals

**Goals:**

1. 固化 `auth_db` 与 `{system}_db` 基础表字段、类型、默认值、唯一键与索引。
2. 固化关联字段语义（尤其跨库 `user_id`）。
3. 可直接产出版本化 SQL 迁移（up/down）与 Entity 对齐检查清单。

**Non-Goals:**

- 不实现登录、发号服务逻辑、RBAC 管理接口、投影同步任务。
- 不建业务域表（订单、工单等）。
- 不引入物理外键、触发器、存储过程。
- 不写入生产账号/密钥；测试种子仅 test/local 且属后续 apply 可选项。

## Decisions

### D1. 两库拆分与表归属

| 库 | 表 | 写者 |
|----|-----|------|
| `auth_db` | `sys_sequence`, `sys_user`, `sys_dept`, `sys_login_log`, `sys_sso_client` | 仅 auth-service |
| `{system}_db` | `sys_sequence`, `sys_role`, `sys_permission`, `sys_user_role`, `sys_role_permission`, `sys_user_ref` | 本系统服务 |

**否决：** RBAC 进 auth 库（违背权限本地）；用户全量复制进业务库。

### D2. 主键与发号

- 所有主表 `id BIGINT`，14 位 `yyMMdd(6)+seq(8)`。
- 每库 `sys_sequence`：UK `(seq_name, seq_date)`，`current_val` 原子递增。
- **否决：** `AUTO_INCREMENT`；跨库全局发号服务（首期各库序列即可）。

### D3. 审计与删除策略

- 主表五件套：`create_time`, `update_time`, `create_by`, `update_by`, `deleted`。
- 业务主表逻辑删 `@TableLogic`；**`sys_user_role` / `sys_role_permission` 删除物理删**。
- **否决：** 中间表逻辑删除（UK 冲突）；缺审计字段的“轻量表”（`sys_user_ref` 仍保留时间戳便于对账）。

### D4. 关联字段（无物理 FK）

| 链 | 字段 |
|----|------|
| 用户→部门 | `sys_user.dept_id` → `sys_dept.id` |
| 部门树 | `sys_dept.parent_id`, `ancestors` |
| 用户↔角色 | `sys_user_role.user_id` → **auth.sys_user.id**；`role_id` → `sys_role.id` |
| 角色↔权限 | `sys_role_permission.role_id`, `permission_id` |
| 权限树 | `sys_permission.parent_id` |
| 投影 | `sys_user_ref.user_id` = 中心 id（PK） |
| 系统维度 | `system_code` 于 role/permission/关联表 |

### D5. 权限与投影模型

- `sys_permission.permission_type`：1 menu / 2 button / 3 api；一表三态。
- UK `(system_code, permission_code)`；角色 UK `(system_code, role_code)`。
- `sys_user_ref` 无 `password_hash`；`status` 仅展示。
- `data_scope` 在 `sys_role` 预留 `TINYINT`，本期不实现过滤逻辑。

**否决：** 菜单/按钮/API 三表；投影存密码或角色。

### D6. 迁移组织

- 版本化脚本：`V1__auth_db_foundation.sql`、`V2__system_rbac_foundation.sql`（或按系统命名）。
- up 建表+索引；down 按依赖逆序 DROP。
- 测试种子（管理员、示例 role/permission）独立脚本，不进生产 profile。

## Risks / Trade-offs

- [跨库无法强制 FK] → 文档+服务校验 `user_id` 来源；集成测试覆盖赋权。
- [关联表 UK + 误用逻辑删] → 迁移与实体约定物理删；code review 检查。
- [system_code 冗余不一致] → 写入时与角色同值；必要时应用校验。
- [一表三态权限查询复杂] → 视图或查询条件约束 type；索引以 code/parent 为主。
- [与 add-unified-auth-center 时序] → 本变更可先 apply 建表，服务变更随后依赖表结构。

## Migration Plan

1. 建 `auth_db` 并执行身份/发号表迁移。
2. 为首个系统建 `{system}_db`，执行 RBAC/投影/发号迁移。
3. 校验：UK/索引存在、无物理 FK、字段与 `docs/database-design.md` 一致。
4. 回滚：down 脚本删表；不影响已有业务表（新建库阶段）。

## Open Questions

1. 迁移工具：Flyway vs 仅 SQL 手册（Spring 多模块常用 Flyway）。
2. 首个 `{system}_db` 实例名与 `system_code` 字面量。
3. `sys_sso_client` 是否进本批 V1，或延后（Nacos 白名单优先）。
