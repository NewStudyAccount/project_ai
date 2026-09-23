# RBAC 权限模型 · 通用设计

> 状态：已确认（2026-09-24）
> 定位：**通用表结构与模型约定**。各子系统在**自己的数据库**内建一套同构 4 表、**自己管理自己的**菜单/角色/授权（非中心化 RBAC 服务）。
> 配套：`CLAUDE.md`（§5.2 权限标识与菜单下发、§6.4.5 审计与发号、§6.5 库表规范）；各系统设计文档**引用本文件**，不复制字段定义。

---

## 1. 模型与边界

### 1.1 模型

- 标准 **RBAC**：用户 → 角色 → 菜单 / 按钮 / 接口权限
- **各子系统自持一套**：本库建表、本系统管理（每服务数据自治，`CLAUDE.md` §5.1）
- 跨系统只有 `user_id` 一个逻辑引用：= 用户中心 `sys_user.id`（16 位发号，无物理外键）
- **权限标识**：`system:resource:action`（全小写，首段 = 本系统名，如 `user:user:list`）；
  前端路由/按钮与后端 `@PreAuthorize` 使用**同一字符串**（`CLAUDE.md` §5.2）
- **菜单与动态路由由后端下发，前端动态生成**（已裁决）；前端禁止硬编码角色名
- **数据范围** `data_scope`（已锁定列与枚举）：1 全部 / 2 本部门（预留，随组织模型生效）/ 3 仅本人

### 1.2 边界（明确不做）

| 不在本模型 | 归属 |
|------------|------|
| 用户主数据（账号、资料、启停权威） | 用户中心 |
| 密码/凭证、会话、令牌、OAuth Client | 认证中心 |
| 跨系统角色/菜单同步、集中式 RBAC 服务 | 不做（各系统自持） |
| 组织/部门模型（`data_scope=2` 的强制语义） | 随组织模型变更另行设计 |

---

## 2. 表结构（通用 4 表）

**全局列约定**（与 `CLAUDE.md` §6.4.5 / §6.5 一致）：主键 `id` = 16 位发号（`yyyyMMdd` + 8 位当日序列，`BIGINT`，出参 String；**发号唯一域 = 本库**）；全表含审计 5 字段（`create_time` / `update_time` / `create_by` / `update_by` / `deleted`）；字符串 `NOT NULL` + 默认值；禁止物理外键；索引命名 `uk_/idx_表名_字段`，单表 ≤ 5。

### 2.1 `sys_menu` — 菜单/权限资源树

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| parent_id | BIGINT | N | 0 | 父节点，0 = 根 |
| type | TINYINT | N | — | 1 目录 / 2 菜单 / 3 按钮 / 4 接口（无 UI 纯权限点，不下发前端） |
| name | VARCHAR(64) | N | `''` | 显示名 |
| permission | VARCHAR(128) | N | `''` | 权限标识 `system:resource:action`；目录留空；前后端/`@PreAuthorize` 同一字符串 |
| path | VARCHAR(255) | N | `''` | 路由 path（菜单用） |
| component | VARCHAR(255) | N | `''` | 前端组件标识（菜单用） |
| icon | VARCHAR(64) | N | `''` | 图标 |
| hidden | TINYINT | N | 0 | 路由 `hidden` |
| requires_auth | TINYINT | N | 1 | 路由 `requiresAuth` |
| sort | INT | N | 0 | 同级排序 |
| status | TINYINT | N | 1 | 1 启用 / 0 停用 |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | 审计 |
| deleted | TINYINT | N | 0 | 0 未删 / 1 已删 |

- **索引**：`idx_sys_menu_parent_id (parent_id)`（1/5）
- **无 `system_code` 列**（已裁决）：本表只放本系统的菜单/权限节点
- 约束：`permission` 首段 MUST = 本系统名（应用层校验）；`permission` 在本系统内唯一由应用层校验（目录节点 `permission=''` 多行存在，不建列级唯一索引）

### 2.2 `sys_role` — 角色

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| role_code | VARCHAR(64) | N | — | 角色编码，本系统内唯一 |
| role_name | VARCHAR(64) | N | `''` | 显示名 |
| data_scope | TINYINT | N | 1 | 数据范围：1 全部 / 2 本部门（预留）/ 3 仅本人 |
| sort | INT | N | 0 | 排序 |
| status | TINYINT | N | 1 | 1 启用 / 0 停用 |
| remark | VARCHAR(255) | N | `''` | 备注 |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | 审计 |
| deleted | TINYINT | N | 0 | |

- **唯一**：`uk_sys_role_role_code (role_code)`；**索引**：`idx_sys_role_status (status)`

### 2.3 `sys_user_role` — 用户-角色

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| user_id | BIGINT | N | — | = 用户中心 `sys_user.id`（逻辑引用，无 FK） |
| role_id | BIGINT | N | — | = 本库 `sys_role.id` |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | 审计 |
| deleted | TINYINT | N | 0 | |

- **唯一**：`uk_sys_user_role_user_role (user_id, role_id)`；**索引**：`idx_sys_user_role_role_id (role_id)`

### 2.4 `sys_role_menu` — 角色-菜单/权限授权

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| role_id | BIGINT | N | — | = 本库 `sys_role.id` |
| menu_id | BIGINT | N | — | = 本库 `sys_menu.id` |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | 审计 |
| deleted | TINYINT | N | 0 | |

- **唯一**：`uk_sys_role_menu_role_menu (role_id, menu_id)`；**索引**：`idx_sys_role_menu_menu_id (menu_id)`

### 2.5 逻辑关系

```text
（用户中心）sys_user.id ──（逻辑）──► sys_user_role.user_id

sys_role 1 ──── * sys_user_role
sys_menu 1 ──── * sys_role_menu * ──── 1 sys_role
sys_menu 自关联（parent_id 树）
```

---

## 3. 运行时行为

| 能力 | 数据来源 | 用途 |
|------|----------|------|
| 动态路由/菜单下发 | `sys_menu` type 1/2 子树（含 `path/component/icon/hidden/requires_auth`，按 `sort` 排序） | 前端登录后取本系统菜单树，动态生成路由 |
| 权限判定 | `sys_menu` type 3/4 的 `permission` 集合（经 用户-角色-菜单 联查） | 后端 `@PreAuthorize` 判定；前端按钮显隐 |

- 查询均为**本库本地**联查（不跨系统调用）；服务侧可加短 TTL 缓存
- 角色分配时的"选人"可经用户中心 `user-api` 查询用户（跨系统只读契约）
- 运行时 `user_id` 以网关注入的 `X-User-Id`（或已验签令牌 `sub`）为准

---

## 4. 各系统引入方式（通用性落地）

1. **建表**：按本文件 §2 在本系统库内建同构 4 表（表名保持 `sys_menu` 等不变，各库一套）；SQL 随各系统变更提供、人工执行
2. **管理端**：各系统管理前端提供「菜单管理」「角色管理」页（含用户-角色分配）
3. **权限标识**：`permission` 首段写本系统名；本系统内唯一（应用层校验）
4. **审计**：关键写操作（菜单/角色/授权变更）按 `CLAUDE.md` §5.2「操作审计」约定记录
