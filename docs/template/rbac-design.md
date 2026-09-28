# RBAC 权限模型 · 通用设计

> 状态：已确认（2026-09-24）；续写基础框架/基础数据，并吸收 RuoYi RBAC 可复用模式（§1.3、授权树、路由元）
> 定位：**通用表结构、模型、基础数据与 RBAC 框架契约**。各子系统在**自己的数据库**内建一套同构 4 表、**自己管理自己的**菜单/角色/授权（非中心化 RBAC 服务）。
> 配套：`CLAUDE.md`（§5.2 权限标识与菜单下发、§6.4.5 审计与发号、§6.5 库表规范）；各系统设计文档**引用本文件**，不复制字段定义。
> **设计参考：** [RuoYi（若依）](https://github.com/yangzongzhuan/RuoYi) / RuoYi-Vue 的菜单-角色-用户授权模型与动态路由下发（见 §1.3 对照取舍）。

---

## 1. 模型与边界

### 1.1 模型

- 标准 **RBAC**：用户 → 角色 → 菜单 / 按钮 / 接口权限
- **各子系统自持一套**：本库建表、本系统管理（每服务数据自治，`CLAUDE.md` §5.1）
- 跨系统只有 `user_id` 一个逻辑引用：= 用户中心 `sys_user.id`（16 位发号，无物理外键）
- **权限标识**：`system:resource:action`（全小写，首段 = 本系统名，如 `user:user:list`）；
  前端路由/按钮与后端下发的 permission 使用**同一字符串**（`CLAUDE.md` §5.2）
- **菜单与动态路由由后端下发，前端动态生成**（已裁决）；前端禁止硬编码角色名
- **数据范围** `data_scope`（已锁定列与枚举）：1 全部 / 2 本部门（预留，随组织模型生效）/ 3 仅本人
- **鉴权边界**（对齐 `CLAUDE.md` §5.2 纯网关鉴权，**已裁决**）：
  - **业务服务**：不挂 Spring Security、**不用 `@PreAuthorize` 拦接口**；登录态与身份以网关注入 `X-User-*`（或已验签 JWT `sub`）为准
  - **权限标识用途**：动态菜单/路由下发 + 前端按钮显隐 +（可选）服务内程序化 `hasPermission` 软校验；**不是**业务接口的硬拦截点
  - **例外：认证中心 `auth-service`**（登录/OIDC 发行方 + 管理端）可保留 Security 与 `@PreAuthorize` 细粒度拦截，不套用到其它业务系统

### 1.2 边界（明确不做）

| 不在本模型 | 归属 |
|------------|------|
| 用户主数据（账号、资料、启停权威） | 用户中心 |
| 密码/凭证、会话、令牌、OAuth Client | 认证中心 |
| 跨系统角色/菜单同步、集中式 RBAC 服务 | 不做（各系统自持） |
| 组织/部门模型（`data_scope=2` 的强制语义） | 随组织模型变更另行设计 |

### 1.3 设计参考（RuoYi）对照与取舍

> 以 RuoYi-Vue 的 **sys_menu / sys_role / sys_user_role / sys_role_menu** + 动态 `getRouters` 为蓝本，裁剪到本项目「分库自持 + 纯网关」约束。

| RuoYi 能力 | 本设计 | 取舍 |
|------------|--------|------|
| 菜单树 `parent_id` + 目录/菜单/按钮 | **采用**（本设计 type 1/2/3，另加 4 接口点） | 保留 type=4 作无 UI 权限点 |
| 权限串 `system:user:list`（`perms`） | **采用**（`permission`，格式 `system:resource:action`） | 与 `CLAUDE.md` §5.2 一致 |
| 后端下发路由，前端 `getRouters` 动态注册 | **采用**（`GET /me/menus`） | 不用固定 `/getRouters` 路径 |
| 按钮权限 `hasPermi` / `v-hasPermi` | **采用语义**（`/me/permissions` + 前端指令/工具） | 业务服务不 `@PreAuthorize` |
| 角色菜单树 **父子联动勾选**（`menu_check_strictly`） | **采用**（见 §9 授权树） | 缺省联动，可按角色关闭 |
| `visible` + `status` 双开关 | **采用语义**（`hidden` + `status`） | 字段名按本库约定 |
| `query` / `is_frame` / `is_cache` 路由元 | **采用（可选列）** | 新系统建议落列；老库可省略 |
| 超管 `*:*:*` 通配 | **不采用通配串**；用 `role_code=admin` 全量（§3） | 与现有实现一致，避免通配解析 |
| `sys_dept` / 部门数据权限 5 级 | **不落表**；`data_scope` 枚举预留 | 组织模型另案（§1.2） |
| `@DataScope` SQL 拼接过滤 | **不进框架**；业务 Service 自行过滤 | 防 ORM 侵入与全表扫 |
| 用户表在 RBAC 内 | **不做**；仅 `user_id` 逻辑引用 | 用户中心权威 |
| `@PreAuthorize("@ss.hasPermi")` 全量拦截 | **仅 auth 例外**；业务纯网关 | `CLAUDE.md` §5.2 |
| 菜单管理 UI（树表单、图标选择） | **交互参考** | 落地在各系统管理端 |

---

## 2. 表结构（通用 4 表）

**全局列约定**：主键 `id` 类型 `BIGINT`，出参 String；全表含审计 5 字段（`create_time` / `update_time` / `create_by` / `update_by` / `deleted`）；字符串 `NOT NULL` + 默认值；禁止物理外键；索引命名 `uk_/idx_表名_字段`，单表 ≤ 5。

**id 规范（RBAC 专用，已重设计）：** 见 **§2.0**。`sys_menu` 用**层级短号**（非 16 位发号）；角色/关联表用**独立短号段**。与 `CLAUDE.md` §6.4.5 通用 16 位发号的差异以本节为准（仅限 RBAC 四表）。

### 2.0 id 规范（RBAC 四表）

#### 2.0.1 设计问题（重设计动机）

| 旧问题 | 说明 |
|--------|------|
| 目录 `id` 与「根」混用 | 根语义用 `id=0` 或目录 id 全 0，无法区分多根、无法作外键 |
| 菜单 id 无层级 | 16 位流水号/发号看不出树深度，排查与种子对齐成本高 |
| 种子 id 过长 | `20260925000000xx` 难读、难与前端/文档对照 |

#### 2.0.2 `sys_menu.id` — 按树深度层级编码

**两条硬规则：**

1. **`id = 0` 非法**：任何菜单/目录/按钮行的主键不得为 0。  
2. **根 = `parent_id = 0`**：仅表示「无父（挂在根）」，**不是**某一行的 id。

**深度 `d`**：从根往下数，根的直接子女 `d = 1`（一级目录/一级菜单），孙子 `d = 2`，依次类推。

**编码公式：**

```text
id = d × 1000 + n
其中 n = 本层全局序号，1 ≤ n ≤ 999（同一深度全表连续，不按父节点切段）
```

| 深度 | id 区间 | 口语 | 谁用 |
|------|---------|------|------|
| d=1 | **1000–1999** | 一级 `100x` | type=1 目录 / 一级菜单 |
| d=2 | **2000–2999** | 二级 `200x` | type=2 二级菜单 |
| d=3 | **3000–3999** | 三级 `300x` | **仅 type=2 三级菜单** |
| d=4 | **4000–4999** | 四级 `400x` | type=2 四级菜单（尽量避免） |
| — | **8000–8999** | 按钮 `800x` | **type=3 按钮**（§2.0.5，不占深度段） |
| — | **5000–5999** | 接口 `500x` | **type=4 接口点**（§2.0.5） |

- **菜单深度上限 d≤4**；更深 → 业务错「菜单层级过深」，先改树结构  
- **300x 只给三级菜单**，按钮禁止占用（避免与三级菜单冲突）

**示例树：**

```text
parent_id=0（根，无行）
├── 1001  系统管理              (d=1, type=1)
│   ├── 2001  菜单管理          (d=2, type=2)
│   │   ├── 8001 新建菜单       (type=3, 按钮段 800x)
│   │   ├── 8002 编辑菜单
│   │   └── 8003 删除菜单
│   └── 2002  角色管理          (d=2, type=2)
│       ├── 8004 新建角色
│       └── …
└── 1002  用户中心              (d=1, type=1)
    ├── 2003  用户管理          (d=2, type=2)
    │   ├── 8008 创建用户
    │   └── …
    └── 2004  组织架构          (d=2, type=2)
        └── 3001 部门管理       (d=3, type=2 三级菜单，id=300x)
```

**运行时分配（`createMenu`，不走 framework 16 位发号）：**

1. type=1/2：由 `parent_id` 解析深度 `d`（`parent_id=0` → `d=1`）；`d>4` 拒绝  
2. type=1/2：`id = d×1000 + 本层 n`（`max+1`）；type=3/4 见 §2.0.5 专用段  
3. 段满 → 业务错「本层/本段 id 已用尽」  
4. **禁止**客户端传 `id`；**禁止**数据库自增；**禁止**改历史行 id

**约束：**

- 种子与运行时共用同一编码；id 在**本库**唯一（跨系统库允许相同数值）  
- 出参仍为 String（`CLAUDE.md` §6.4.7）  
- **type=1/2（目录/菜单）**：id **只看树深度**（100x–400x）  
- **type=3/4（按钮/接口点）**：id **不占深度段**，用 **800x / 500x**，见 **§2.0.5**

#### 2.0.5 按钮 / 接口点 id（type=3/4）

**动机：** 三级菜单已是 `300x`，按钮若也进 `300x` 会**与三级菜单抢号**。按钮是权限叶子，必须与「菜单深度序列」分离，用 **type 专用段**。

| type | id 段 | 口语 | 与菜单段关系 | 挂载约束 |
|------|-------|------|--------------|----------|
| 3 按钮 | **8000–8999** | `800x` | 独立，不占 100x–400x | `parent_id` **必须**为 `type=2` 菜单 |
| 4 接口点 | **5000–5999** | `500x` | 独立；菜单最深 400x，不冲突 | 同上；**不**下发 `/me/menus` |

**硬规则：**

1. **300x 仅三级菜单（type=2）**；按钮禁止占用 300x/400x/100x/200x。  
2. 按钮/接口点 **禁止**挂根或目录；父必须是菜单（目录→菜单→按钮）。  
3. **归属看 `parent_id`**；同段内全局 `max+1`，不要求从 id 反解父。  
4. type=1/2 **不得**占用 8000–8999 / 5000–5999。

**示例：**

```text
1001 系统管理                    (type=1)
└── 2001 菜单管理                (type=2, 二级菜单 200x)
    ├── 8001 新建菜单            (type=3, 按钮 800x)
    ├── 8002 编辑菜单
    └── 8003 删除菜单

1002 用户中心
└── 2004 组织架构                (type=2)
    ├── 3001 部门管理            (type=2, 三级菜单 300x)  ← 与按钮段隔离
    └── 8010 启停部门            (type=3, 按钮 800x)
```

**分配（`createMenu`）：**

```text
if type == 2 or type == 1:
  校验 depth ≤ 4；id = depth×1000 + 本层 n
if type == 3:
  要求 parent.type == 2；段 [8000,9000)；id = max+1
if type == 4:
  要求 parent.type == 2；段 [5000,6000)；id = max+1
```

**方案取舍（按钮 id）：**

| 方案 | 结论 |
|------|------|
| 按钮进 300x | **否**：与三级菜单冲突 |
| 按钮随深度 200x/300x/400x | **否**：与菜单段混用 |
| 父号派生（每菜单 10 坑） | **否**：按钮数量不可控 |
| **按钮 800x / 接口 500x + 父必须为菜单** | **是** |

#### 2.0.3 `sys_role` / 关联表 id

| 表 | 段 | 说明 |
|----|----|------|
| `sys_role.id` | **9001–9999** | 非树；角色短号。种子：`9001=admin`，`9002+=` 可选角色 |
| `sys_user_role.id` | **7001–7999** | 代理键；唯一业务键仍是 `(user_id, role_id)` |
| `sys_role_menu.id` | **6001–6999** | 代理键；唯一业务键仍是 `(role_id, menu_id)` |

- 关联表插入优先用**业务唯一键**判重；代理 id 仅主键占位  
- 运行时新建角色/关联：在本段取 `max+1`（或关联表仍可走 framework 发号，但种子必须落在上表段内以便对照）  
- **`user_id` 不在本规范内**：继续用户中心 16 位发号

#### 2.0.4 与通用发号的关系

| 对象 | id 来源 |
|------|---------|
| `sys_menu` | **层级编码**（§2.0.2），应用层分配 |
| `sys_role` / 两关联表 | **段内短号**（§2.0.3） |
| 用户 `sys_user.id` 等其它实体 | 仍 `framework` 16 位（`CLAUDE.md` §6.4.5） |

### 2.1 `sys_menu` — 菜单/权限资源树

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，**层级编码**（§2.0.2，非 16 位发号） |
| parent_id | BIGINT | N | 0 | 父节点；**0 = 根**（无父），禁止用 0 作某行的 id |
| type | TINYINT | N | — | 1 目录 / 2 菜单 / 3 按钮 / 4 接口（无 UI 纯权限点，不下发前端） |
| name | VARCHAR(64) | N | `''` | 显示名 |
| permission | VARCHAR(128) | N | `''` | 权限标识 `system:resource:action`；目录留空；前端按钮/路由与后端下发同一字符串 |
| path | VARCHAR(255) | N | `''` | 路由 path（菜单用）；外链时可为完整 URL（`is_frame=1`） |
| component | VARCHAR(255) | N | `''` | 前端组件标识（菜单用，对齐 RuoYi `component`） |
| query | VARCHAR(255) | N | `''` | 路由 query，如 `a=1&b=2`（RuoYi `query`；可选列） |
| is_frame | TINYINT | N | 0 | 0 否 / 1 外链（RuoYi `is_frame`；path 为外链，不进本地路由表） |
| is_cache | TINYINT | N | 0 | 0 不缓存 / 1 keep-alive（RuoYi `is_cache`） |
| icon | VARCHAR(64) | N | `''` | 图标（RuoYi `icon`） |
| hidden | TINYINT | N | 0 | 0 显示 / 1 隐藏（= RuoYi `visible` 取反；菜单仍可能被授权） |
| requires_auth | TINYINT | N | 1 | 路由 `requiresAuth` |
| sort | INT | N | 0 | 同级排序（RuoYi `order_num`） |
| status | TINYINT | N | 1 | 1 启用 / 0 停用（停用不进 `/me/*`） |
| remark | VARCHAR(255) | N | `''` | 备注（RuoYi `remark`；可选列） |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | 审计 |
| deleted | TINYINT | N | 0 | 0 未删 / 1 已删 |

- **索引**：`idx_sys_menu_parent_id (parent_id)`（1/5）
- **无 `system_code` 列**（已裁决）：本表只放本系统的菜单/权限节点
- 约束：`permission` 首段 MUST = 本系统名（应用层校验）；`permission` 在本系统内唯一由应用层校验（目录节点 `permission=''` 多行存在，不建列级唯一索引）
- **兼容**：`query` / `is_frame` / `is_cache` / `remark` 为 RuoYi 对齐的**推荐扩展列**；已建库可不回填（省略列，VO 对应字段可空/默认），新系统建议一次建全
- **type 对照 RuoYi**：本设计 1≈`M` 目录、2≈`C` 菜单、3≈`F` 按钮；4 为扩展「接口点」

### 2.2 `sys_role` — 角色

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，**9001–9999 短号**（§2.0.3） |
| role_code | VARCHAR(64) | N | — | 角色编码（RuoYi `role_key`），本系统内唯一；`admin` 语义见 §3 |
| role_name | VARCHAR(64) | N | `''` | 显示名 |
| data_scope | TINYINT | N | 1 | 数据范围（RuoYi 兼容演进见 §11.1）：1 全部 / 2 本部门（预留）/ 3 仅本人 |
| menu_check_strictly | TINYINT | N | 1 | 1 父子联动勾选 / 0 父子独立（RuoYi `menu_check_strictly`） |
| sort | INT | N | 0 | 排序（RuoYi `role_sort`） |
| status | TINYINT | N | 1 | 1 启用 / 0 停用 |
| remark | VARCHAR(255) | N | `''` | 备注 |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | 审计 |
| deleted | TINYINT | N | 0 | |

- **唯一**：`uk_sys_role_role_code (role_code)`；**索引**：`idx_sys_role_status (status)`
- **兼容**：`menu_check_strictly` 为推荐扩展列；老库缺省按「联动=1」行为处理（应用层默认）

### 2.3 `sys_user_role` — 用户-角色

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，**7001–7999 短号**（§2.0.3） |
| user_id | BIGINT | N | — | = 用户中心 `sys_user.id`（逻辑引用，无 FK；仍为用户中心 16 位发号） |
| role_id | BIGINT | N | — | = 本库 `sys_role.id` |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | 审计 |
| deleted | TINYINT | N | 0 | |

- **唯一**：`uk_sys_user_role_user_role (user_id, role_id)`；**索引**：`idx_sys_user_role_role_id (role_id)`

### 2.4 `sys_role_menu` — 角色-菜单/权限授权

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，**6001–6999 短号**（§2.0.3） |
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

### 2.6 推荐扩展列（RuoYi 对齐；新系统一次建全）

老库可选补列（生产前征询）：

```sql
-- sys_menu
ALTER TABLE sys_menu
  ADD COLUMN query VARCHAR(255) NOT NULL DEFAULT '' COMMENT '路由 query',
  ADD COLUMN is_frame TINYINT NOT NULL DEFAULT 0 COMMENT '0否 1外链',
  ADD COLUMN is_cache TINYINT NOT NULL DEFAULT 0 COMMENT '0不缓存 1keep-alive',
  ADD COLUMN remark VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注';

-- sys_role
ALTER TABLE sys_role
  ADD COLUMN menu_check_strictly TINYINT NOT NULL DEFAULT 1 COMMENT '1父子联动 0独立';
```

---

## 3. 运行时行为

| 能力 | 数据来源 | 用途 |
|------|----------|------|
| 动态路由/菜单下发 | `sys_menu` type 1/2 子树（含 `path/component/query/is_frame/is_cache/icon/hidden/requires_auth`，按 `sort` 排序；仅 `status=1`） | 前端登录后取本系统菜单树，动态生成路由（RuoYi `getRouters` 等价） |
| 权限集下发 | `sys_menu` type 3/4 的 `permission` 集合（经 用户-角色-菜单 联查） | 前端按钮显隐；业务服务可选程序化 `hasPermission` |
| 硬拦截（登录态） | 网关 JWT/会话 + 路由放行策略 | 业务接口鉴权唯一归属（纯网关） |
| 硬拦截（细粒度） | **仅 auth-service 例外**：`@PreAuthorize("hasAuthority('…')")` | 认证中心管理端；业务服务**不使用** |

**菜单树下发形态（对齐 RuoYi `RouterVo` 语义，字段名按本设计）：**

```text
MenuVO {
  id, parentId, type, name, path, component, query, isFrame, isCache,
  icon, hidden, requiresAuth, sort, status, permission, children[]
}
```

- type=1 目录：只承载 `children` 与导航（path 可为布局段或空）
- type=2 菜单：`path` + `component` 注册为路由；`isFrame=1` 时前端外开，不 `addRoute`
- type=3/4：不进 `/me/menus` 树（权限集另发）

**管理员全量授权（各系统必实现）：**

- 操作员在**本系统**拥有启用角色 **`role_code = 'admin'`** 时，`/me/menus`、`/me/permissions` 及登录权限装载 = **本系统全部 `status=1` 菜单/权限**，**不必**依赖 `sys_role_menu` 逐条绑定。
- 新建 `sys_menu` 后 admin 立即可见/可用；非 admin 仍走 用户-角色-菜单 联查。
- 范围仅限**本库/本系统**；跨系统各自有一套 admin，互不影响。

- 查询均为**本库本地**联查（不跨系统调用）；服务侧可加短 TTL 缓存
- 角色分配时的"选人"可经用户中心 `user-api` 查询用户（跨系统只读契约）
- 运行时 `user_id` 以网关注入的 `X-User-Id`、已验签 JWT `sub` 为准（`AuthContext`/`UserContext` 需同时认 JWT，见 fixbug）

---

## 4. 各系统引入方式（通用性落地）

1. **建表**：按本文件 §2 在本系统库内建同构 4 表（表名保持 `sys_menu` 等不变，各库一套）；SQL 随各系统变更提供、人工执行
2. **基线数据**：按 **§5 基础数据设计** 落 L2 骨架 + L3 `admin`（及可选 L3b）+ L4 域节点（模板 `deploy/db/seed/rbac-framework-template.sql`）
3. **管理端**：各系统管理前端提供「菜单管理」「角色管理」页（含用户-角色分配）
4. **权限标识**：`permission` 首段写本系统名；本系统内唯一（应用层校验）
5. **审计**：关键写操作（菜单/角色/授权变更）按 `CLAUDE.md` §5.2「操作审计」约定记录

---

## 5. 基础数据设计（种子 / 基线）

> 目标：新系统导入后即可**自举 RBAC**（管菜单/角色/授权），再挂业务域节点。  
> 模板：[`deploy/db/seed/rbac-framework-template.sql`](../../deploy/db/seed/rbac-framework-template.sql)  
> **id：** 一律按 **§2.0** 层级/段内短号（非 16 位发号）；固定号便于 `INSERT IGNORE` 重复执行。

### 5.1 目标与原则

| 原则 | 说明 |
|------|------|
| **可重复执行** | 一律 `INSERT IGNORE` + 幂等 `UPDATE`（修 component 等旧行）；禁止裸 `INSERT` |
| **固定 id** | 种子节点用 §2.0 层级/段内短号，不走 16 位发号，保证跨环境可对齐 |
| **先自举后业务** | 先落 L2/L3，能登录管理菜单/角色，再追加 L4 域节点 |
| **与前端对齐** | `component` / `permission` 与 `componentByPath`、`XxxPermissionConstants` 字符串一致 |
| **环境边界** | 测试/开发可执行；**生产执行前必须征询**（`CLAUDE.md` 第 7 节） |
| **data_scope 数值** | 必须 `TINYINT` **1/2/3**，禁止 `'ALL'` 等字符串（与 §2.2 列定义一致） |

### 5.2 分层清单

| 层 | 内容 | 每系统必有 | 载体 |
|----|------|:----------:|------|
| **L1 结构** | 4 表 DDL（§2） | 是 | `deploy/db/migration/{db}/` |
| **L2 自举骨架** | 「系统管理」目录 + 菜单管理 + 角色管理 + 按钮 | 是 | 种子模板 §5.4 |
| **L3 角色** | `role_code='admin'`（全量授权，§3） | 是 | 种子模板 §5.5 |
| **L3b 可选角色** | `operator` / `viewer` 等模板角色 | 否 | 按系统启用 |
| **L4 域节点** | 用户、Client、审计等业务菜单/按钮 | 按系统 | 各系统 seed |
| **L5 绑定** | `sys_role_menu` / `sys_user_role` | 建议 | 种子或运行时 |

### 5.3 固定 ID 分配规范

> 与 **§2.0** 同一套编码；种子写死号段，运行时 `createMenu` 在同层 `max+1` 续号。

**`sys_menu`（层级短号）**

| 类型/深度 | 号段 | 种子建议布局 |
|-----------|------|----------------|
| type=1/2 d=1 | 1001–1999 | `1001` 系统管理；`1002+` 业务域目录 |
| type=1/2 d=2 | 2001–2999 | `2001` 菜单管理、`2002` 角色管理；`2003+` 域菜单 |
| type=1/2 d=3 | 3001–3999 | **仅三级菜单**（非按钮） |
| type=1/2 d=4 | 4001–4999 | 尽量避免 |
| type=3 按钮 | **8001–8999** | menu/role CRUD 等操作钮 |
| type=4 接口点 | **5001–5999** | 无 UI 权限点 |

**框架骨架预留（各系统复制时保持不变）：**

| id | parent_id | type | name |
|----|-----------|------|------|
| 1001 | 0 | 1 | 系统管理 |
| 2001 | 1001 | 2 | 菜单管理 |
| 2002 | 1001 | 2 | 角色管理 |
| 8001–8003 | 2001 | 3 | 新建/编辑/删除菜单 |
| 8004–8007 | 2002 | 3 | 新建/编辑/删除角色、角色授权 |

**其它表短号：**

| 表 | 号段 | 种子 |
|----|------|------|
| `sys_role` | 9001–9999 | `9001=admin`；`9002=operator`（可选）；`9003=viewer`（可选） |
| `sys_user_role` | 7001–7999 | `7001` = 冒烟用户 → admin |
| `sys_role_menu` | 6001–6999 | admin 显式绑定从 `6001` 起连号 |

**冲突规则**：
- 同一库内 id **全局唯一**（菜单层级段、角色 9xxx、关联 6xxx/7xxx 不交叉）
- 菜单 id **不得**占用其它段；角色/关联 **不得**占用 1000–5999
- 运行时新建菜单按 §2.0.2 `max+1`；新建角色按 9xxx `max+1`
- 跨系统库各自一套，**允许**数值相同
- **禁止**把根写成 `id=0`；根只用 `parent_id=0`

### 5.4 L2 自举骨架（`{sys}` = 本系统前缀，全小写）

| id | parent_id | type | name | permission | path | component | icon | sort |
|---:|----------:|:----:|------|------------|------|-----------|------|-----:|
| **1001** | 0 | 1 目录 | 系统管理 | `''` | `''` | `''` | `setting` | 90 |
| **2001** | 1001 | 2 菜单 | 菜单管理 | `{sys}:menu:list` | `/menus` | `views/menu/MenuList.vue` | `''` | 1 |
| **2002** | 1001 | 2 菜单 | 角色管理 | `{sys}:role:list` | `/roles` | `views/role/RoleList.vue` | `''` | 2 |
| **8001** | 2001 | 3 按钮 | 新建菜单 | `{sys}:menu:create` | `''` | `''` | `''` | 1 |
| **8002** | 2001 | 3 按钮 | 编辑菜单 | `{sys}:menu:update` | `''` | `''` | `''` | 2 |
| **8003** | 2001 | 3 按钮 | 删除菜单 | `{sys}:menu:delete` | `''` | `''` | `''` | 3 |
| **8004** | 2002 | 3 按钮 | 新建角色 | `{sys}:role:create` | `''` | `''` | `''` | 4 |
| **8005** | 2002 | 3 按钮 | 编辑角色 | `{sys}:role:update` | `''` | `''` | `''` | 5 |
| **8006** | 2002 | 3 按钮 | 删除角色 | `{sys}:role:delete` | `''` | `''` | `''` | 6 |
| **8007** | 2002 | 3 按钮 | 角色授权 | `{sys}:role:assign` | `''` | `''` | `''` | 7 |

- **根**：`1001` 的 `parent_id=0`（不是 id=0）
- 其余 `hidden=0`、`requires_auth=1`、`status=1`
- **component** 必须与本系统前端 `componentByPath` 键一致（见 fixbug §7）；type 3 按钮不填 path/component
- type 4 接口点按需由各系统业务补，**不进 L2**；id 用 **500x**（§2.0.5），挂在 type=2 菜单下
- 目录 `sort=90`：「系统管理」沉底，业务域目录用更小 sort 靠前

### 5.5 L3 / L3b 角色基线

| id | role_code | role_name | data_scope | sort | status | 绑定建议 |
|---:|-----------|-----------|:----------:|-----:|:------:|----------|
| **9001** | `admin` | 管理员 | **1** | 1 | 1 | 用户中心冒烟账号 `admin`（`user_id=2026092400000001`，见冒烟 seed） |
| **9002** | `operator` | 运营（可选） | 1 或 3 | 2 | 1 | 按域绑 L4 业务菜单+写按钮，不含 L2 删除类 |
| **9003** | `viewer` | 只读（可选） | 3 | 3 | 1 | 只绑 type=2 菜单 list 权限，不绑 type=3 写按钮 |

- **`admin` 必有**：运行时全量授权（§3）；**建议**同步插 `sys_role_menu` 绑当前全量节点（授权页展示、联查兜底），运行时不依赖该表
- **`operator` / `viewer` 可选**：模板注释保留；各系统按域裁剪，不强制预置
- `role_code` 系统内唯一；**禁止**改掉 `admin` 语义（全量判定硬编码该 code，见 §3）

### 5.6 L4 域节点扩展约定

| 约定 | 说明 |
|------|------|
| id | 按树深度用 §2.0.2（d=1 → `100x` 且不占用 `1001`；d=2 → `200x` 续号…） |
| 目录 | 每业务域一个 type=1 目录（如「用户中心」），`parent_id=0`，id 如 `1002` |
| 菜单 | type=2：`permission`=`{sys}:{res}:list`，path 唯一，component 填前端组件键 |
| 按钮 | type=3：id 固定 **800x**；必须挂 type=2 菜单；action 建议 `list/create/update/delete/status/assign` |
| 接口点 | type=4：id 固定 **500x**；必须挂 type=2 菜单；无 UI；不下发 `/me/menus` |
| sort | 同级有序；域目录用 1、2、3…（小于系统管理的 90） |

**L4 示例（user 系统）：**

| id | parent_id | type | name | permission | path | component |
|---:|----------:|:----:|------|------------|------|-----------|
| 1002 | 0 | 1 | 用户中心 | `''` | `''` | `''` |
| 2003 | 1002 | 2 | 用户管理 | `user:user:list` | `/users` | `views/user/UserList.vue` |
| 2004 | 1002 | 2 | 审计日志 | `user:audit:list` | `/audits` | `views/audit/AuditList.vue` |
| 8008 | 2003 | 3 | 创建用户 | `user:user:create` | — | — |
| 8009 | 2003 | 3 | 更新用户 | `user:user:update` | — | — |
| 8010 | 2003 | 3 | 启停用户 | `user:user:status` | — | — |

### 5.7 关联绑定数据

**`sys_role_menu`（L5）**

| 规则 | 说明 |
|------|------|
| admin 显式绑定 | 覆盖当前全部已授权节点；`id` 从 **6001** 起连号 |
| 覆盖语义 | 与运行时 `PUT /roles/{id}/menus` 一致：新 seed 批次可先删该角色旧绑定再插（或依赖 `uk` + IGNORE 只增） |
| 非 admin | 只绑授权页勾选的节点；种子非必须 |

**`sys_user_role`（L5）**

| 规则 | 说明 |
|------|------|
| 冒烟绑定 | `id=7001`，`user_id=2026092400000001`（用户中心 admin）→ `role_id=9001` |
| 生产绑定 | **不**预置业务人员；上线后由管理页分配 |
| 唯一键 | `uk_sys_user_role_user_role`；重复插入靠 `INSERT IGNORE` |

### 5.8 种子 SQL 工程规范

| 项 | 约定 |
|----|------|
| **文件名** | `{YYYY-MM-DD}-{db}-{purpose}.sql` 或模板名 `rbac-framework-template.sql` |
| **存放** | `deploy/db/seed/`；建表在 `deploy/db/migration/{db}/` |
| **头部注释** | 用途、对齐文档、替换清单（`{DB}/{SYS}/{USER_ID}`）、环境边界 |
| **幂等** | `INSERT IGNORE`；改旧行用**按 id 的** `UPDATE`（如回填 component） |
| **替换** | 模板占位符替换后执行；禁止留 `{SYS}` 未替换 |
| **顺序** | migration（L1）→ seed（L2/L3/L4/L5）→ 冒烟账号（用户中心，另文件） |
| **升级** | 新增节点按 **§2.0.2 本层续号** + 新 seed 文件；修改已有节点写 `UPDATE`；**禁止**改历史 id |
| **生产** | 执行前征询；生产一般只导 L1+L2+L3，业务人员与域节点走管理页 |
| **审计** | 执行后在 `deploy/db/migration/{db}/execution-record.md` 登记（环境/日期/结果） |

### 5.9 与代码 / 前端对齐清单

| 数据 | 对齐对象 | 不一致后果 |
|------|----------|------------|
| `permission` | `{system}-common` 的 `XxxPermissionConstants` | 常量与授权脱节，按钮误显隐 |
| `component` | 前端 `router` 的 `componentByPath` 键 | 有菜单无页面（fallback Dashboard） |
| `path` | 前端路由 path（去前导 `/` 注册） | 404 / 重复注册 |
| `role_code='admin'` | `RbacService.ROLE_ADMIN` | 全量授权失效 |
| `data_scope` | `RoleRequest`/`RoleVO` 枚举 1/2/3 | 校验失败或范围语义错 |
| `sys_menu.id` / `parent_id` | §2.0.2 层级编码；根 `parent_id=0` | 树断裂、id 撞段 |

### 5.10 自举验收清单（导种子后）

1. `sys_menu` L2 十条齐全，type=2 的 `component` 非空且命中前端 map  
2. `sys_role` 存在启用 `admin`，`data_scope=1`  
3. 冒烟账号绑上 `admin`（`sys_user_role`）  
4. 登录 → `/me/menus` 含「系统管理/菜单管理/角色管理」；`/me/permissions` 含 `{sys}:menu:*`、`{sys}:role:*`  
5. 管理页可新建菜单/角色并授权；admin 新建后立即可见（全量）  
6. 非 admin 角色仅见已绑节点（联查路径通）  
7. 重复执行 seed 无报错、无重复行（幂等）

### 5.11 实例参考

| 库 | 种子 |
|----|------|
| `auth_db` | `2026-09-24-smoke-admin-auth-portal.sql`（L2 已含 menus/roles + 域：client/grant/audit；早期 `data_scope` 若为字符串需按 §5.1 改为 1） |
| `user_db` | `2026-09-25-user-db-rbac-framework.sql`（L2 + 用户域 + admin 绑定） |
| 新系统 | 复制 `rbac-framework-template.sql`，按 §5.3–§5.8 替换执行 |

---

## 6. 鉴权与权限装载链路

### 6.1 职责分层（对齐 `CLAUDE.md` §5.2）

```text
浏览器
  → Nginx（静态 + 反代 /api）
  → 网关：JWT 验签 / 登录态拦截 / 路由放行 / 注入 X-User-Id、X-User-Name
  → 业务服务：UserContext/AuthContext 取身份（X-User-* 或 JWT sub）
       ├─ /me/menus、/me/permissions：本库 RBAC 联查后下发
       ├─ 管理写接口：@Idempotent + 审计；权限靠网关登录态（业务服务不 @PreAuthorize）
       └─ 例外 auth-service：验签/会话恢复时从 RBAC 装载 authorities，@PreAuthorize 硬拦截
```

### 6.2 身份解析（必做，否则 `/me/*` 空）

| 来源 | 解析要点 | 落点 |
|------|----------|------|
| 网关注入 `X-User-Id` / `X-User-Name` | 服务侧**覆盖**客户端同名头后读取 | `UserContext` / `AuthContext` |
| 已验签 JWT | `sub` → userId；`preferred_username` → userName | Context 过滤器需同时认 JWT |
| 网关 Bearer | **必须**从 `Authorization: Bearer` 解析 `sub` 注入 `X-User-*`；勿只认本地 `X-Dev-*` | 网关过滤器 |

- **禁止**信任客户端伪造的 `X-User-Id`；网关必须剥除客户端同名头后再注入
- `userId=0` / 未解析 → `/me/*` 返回空数组属预期；排查先看 Context 是否拿到真实 id（见 §12）

### 6.3 权限装载（仅 auth-service 例外）

| 场景 | 做法 |
|------|------|
| JWT 验签后 | `RbacJwtAuthenticationConverter`：按 `sub` 调 `permissionsForUser` 装入 authorities |
| SSO 会话恢复 | `SsoSessionAuthFilter` 同样从 RBAC 装载 authorities |
| 约束 | **JWT Claims 最小集不含权限码**；禁止假设 token 内嵌权限 |
| 业务服务 | 不装载 authorities、不 `@PreAuthorize`（纯网关） |

### 6.4 admin 全量与联查（运行时统一）

```text
permissionsForUser(userId) / myMenuList()
  if userId 无效 → 空
  if 用户拥有启用角色 role_code='admin' → 本库全部 status=1 菜单/权限
  else → sys_user_role → sys_role_menu → sys_menu（去重）
```

- `/me/menus`：结果过滤 `type ∈ {1,2}`，按 `sort` 组树
- `/me/permissions`：非空 `permission` 去重集合（含 type 3/4；type 1/2 若有 permission 也纳入）
- 查询**仅本库**；可短 TTL 缓存（§11）

---

## 7. 标准 API 契约

> 路径前缀 `/api/v1`；统一 `{code,msg,data}`；`id` 出参 **String**（入参 String，服务内转 Long）。
> 业务系统**不**挂 `@PreAuthorize`；关键写统一 `@Idempotent`（可叠加 `@RateLimit`）。
> auth-service 可在同构接口上额外标注 `@PreAuthorize("hasAuthority('auth:…')")`（例外）。

### 7.1 菜单管理

| 方法 | 路径 | 用途 | 横切 |
|------|------|------|------|
| GET | `/menus` | 全量菜单树（管理页） | |
| POST | `/menus` | 新建节点 | `@Idempotent` |
| PUT | `/menus/{id}` | 更新节点 | `@Idempotent` |
| DELETE | `/menus/{id}` | 删除叶子节点 | `@Idempotent` |

### 7.2 角色与授权

| 方法 | 路径 | 用途 | 横切 |
|------|------|------|------|
| GET | `/roles` | 角色列表（含已绑 `menuIds`） | |
| POST | `/roles` | 新建角色 | `@Idempotent` |
| PUT | `/roles/{id}` | 更新角色 | `@Idempotent` |
| DELETE | `/roles/{id}` | 删除角色（级联清关联） | `@Idempotent` |
| PUT | `/roles/{id}/menus` | 角色-菜单**全量覆盖**授权 | `@Idempotent` |
| POST | `/users/{id}/roles` | 用户-角色分配（增量） | `@Idempotent` |
| DELETE | `/users/{id}/roles` | 用户-角色回收（可选，按系统） | `@Idempotent` |

### 7.3 当前操作员

| 方法 | 路径 | 用途 | 横切 |
|------|------|------|------|
| GET | `/me/menus` | 动态路由菜单树（type 1/2） | 登录态；无需权限码 |
| GET | `/me/permissions` | 权限标识集合 | 登录态；无需权限码 |

### 7.4 DTO / VO 契约

**MenuRequest**（入参，Bean Validation）

| 字段 | 类型 | 约束 |
|------|------|------|
| parentId | Long/String | 默认 `0` |
| type | Integer | `1..4` 必填 |
| name | String | 必填，≤64 |
| permission | String | ≤128；非空时首段 = 本系统名且系统内唯一 |
| path / component / icon | String | ≤255 / ≤255 / ≤64，默认 `''` |
| query | String | ≤255，默认 `''`（可选） |
| isFrame / isCache | Integer | 默认 0 |
| hidden / requiresAuth / sort / status | Integer | 默认 0 / 1 / 0 / 1 |
| remark | String | ≤255，默认 `''`（可选） |

**MenuVO**（出参，对齐 RuoYi RouterVo 语义）

| 字段 | 说明 |
|------|------|
| id / parentId | String |
| type / name / permission / path / component / icon | 同表 |
| query / isFrame / isCache | 路由元（可选列；无则空/0） |
| hidden / requiresAuth / sort / status | Integer |
| children | `MenuVO[]`，树结构 |

**RoleRequest**：`roleCode`（必填 ≤64）、`roleName`、`dataScope`（默认 1）、`menuCheckStrictly`（默认 1）、`sort`、`status`、`remark`  
**RoleVO**：上表字段 + `menuIds: string[]`（当前已绑菜单，便于授权页**回显**；含半选父节点时见 §9）

**授权体**：`{ "menuIds": string[] }` / `{ "roleIds": string[] }`（全量覆盖或增量分配，见 §7.2）  
**授权树查询**（可选，RuoYi 对齐）：`GET /roles/{id}/menus/tree` → `{ menuIds, halfCheckedIds? }`，供树组件回显

---

## 8. 服务端落点与代码契约

### 8.1 模块与包

| 落点 | 内容 |
|------|------|
| `{system}-service` | Entity（`SysMenu/SysRole/SysUserRole/SysRoleMenu`）、Mapper、`RbacService(+Impl)`、DTO/VO、Controller |
| `{system}-common` | 权限标识常量（如 `UserPermissionConstants`）；**不**放 Entity/Mapper |
| `{system}-framework` | `UserContext`/`AuthContext`、发号 `IdGenerator`、`@Idempotent`/`@RateLimit` |
| 转换 | MapStruct 可选；简单 VO 手写 map 亦可（保持 converter 唯一约定） |

- 调用链：`Controller → RbacService → Mapper`；禁止 Controller 直调 Mapper
- 事务：`@Transactional` 只在 Service（授权覆盖写、级联删除）

### 8.2 RbacService 能力面（通用）

| 方法 | 语义 |
|------|------|
| `listMenuTree()` | 管理页全量树 |
| `createMenu` / `updateMenu` / `deleteMenu` | 树 CRUD + 校验（§9）；新建 id 按 **§2.0.2** 层级 `max+1` 分配 |
| `listRoles` / `createRole` / `updateRole` / `deleteRole` | 角色 CRUD + 级联 |
| `assignRoleMenus(roleId, menuIds)` | **先删后插**全量覆盖 |
| `assignUserRoles` / `removeUserRoles` | 分配/回收（可合并为覆盖语义，二选一并写清） |
| `myMenus` / `myPermissions` | 按当前操作员（admin 全量或联查） |
| `permissionsForUser(userId)` | 供 auth-service 装载 authorities（业务系统可不暴露） |
| `hasPermission(perm)` | 可选软校验（不替代网关鉴权）；语义 ≈ RuoYi `PermissionService.hasPermi` |

### 8.3 审计

关键写（菜单/角色/授权变更）记录操作审计：操作人 / 时间 / `action` / 目标 / 结果；  
`action` 建议：`MENU_CREATE|MENU_UPDATE|MENU_DELETE|ROLE_CREATE|ROLE_UPDATE|ROLE_DELETE|ROLE_MENU|ROLE_ASSIGN`；  
载体（审计表或结构化日志）随各系统变更裁决（`CLAUDE.md` §5.2）。

---

## 9. 业务校验规则

| 规则 | 说明 | 失败语义 |
|------|------|----------|
| `permission` 首段 = 本系统名 | 非空时校验（如 `user:` / `auth:`） | 业务码「权限标识非法」 |
| `permission` 系统内唯一 | 目录允许 `''` 多行；非空唯一由应用层查重 | 「权限标识已存在」 |
| `role_code` 系统内唯一 | `uk_sys_role_role_code` + 应用层友好错误 | 「角色编码已存在」 |
| 删除菜单仅叶子 | 存在 `parent_id=id` 子节点则拒绝 | 「存在子节点，不可删除」 |
| 删除菜单清授权 | 同步删 `sys_role_menu.menu_id` | — |
| 删除角色级联 | 删 `sys_user_role.role_id` + `sys_role_menu.role_id` | — |
| 角色授权覆盖 | `PUT /roles/{id}/menus` 先删该角色旧绑定再插 | — |
| **授权树父子联动** | `menu_check_strictly=1`（默认）：勾父必含全部子；勾叶须含祖先链（见下） | 「授权树不完整」 |
| `data_scope` 枚举 | 仅 1/2/3（演进见 §11.1）；非法值拒绝 | 「数据范围非法」 |
| `admin` 角色编码 | 建议系统内保留语义；删除/改码需显式业务确认 | 可拒绝删除或警告 |
| type 约束 | 1 目录：path/component 可空；2 菜单：建议 path+component；3/4：permission 建议必填 | 校验码 `3xxxx` 或业务码 |
| `is_frame=1` | `path` 须为合法绝对 URL；不校验 `component` | 「外链地址非法」 |

**授权树语义（RuoYi `menu_check_strictly`）：**

| 模式 | 行为 |
|------|------|
| `menu_check_strictly=1` 联动（默认） | 树组件勾选父节点 = 勾选其下全部子节点；取消父 = 取消子。保存的 `menuIds` 为**全部勾选节点**（含父）；服务端可按「叶子+祖先链」归一化，避免只存叶导致 `/me/menus` 断树 |
| `menu_check_strictly=0` 独立 | 父子勾选互不影响；允许只授按钮不授菜单（慎用） |

- **回显**：已绑 `menuIds` 映射到树的 checked；仅祖先被绑而子未绑时，祖先为 half-checked（前端处理，或接口返回 `halfCheckedIds`）
- **`/me/menus` 组树**：在授权节点集合上取祖先闭包（缺父则挂根或丢弃孤儿，与 §6.4 一致），禁止只下发孤叶子节点

- 入参一律 Bean Validation；业务规则在 Service；禁止吞异常
- 错误码在各系统 `XxxErrorCodeEnum` 分配（`2xxxxx` 先登记 `docs/error-code-ranges.md`）

---

## 10. 前端动态路由与按钮权限

### 10.1 登录后装载

```text
OIDC/登录成功 → 持 AT 调 GET /me/menus + /me/permissions
  → Pinia auth store 缓存 menus / permissions
  → router.beforeEach：installDynamicRoutes(menus)
  → 按钮 v-if / v-hasPermi：permissions.includes('sys:res:act')
```

### 10.2 组件映射与路由 meta（RuoYi 对齐）

- `sys_menu.component` 与前端 map **键字符串完全一致**（如 `views/menu/MenuList.vue`）
- type=2 且 `path` 非空且 `isFrame=0` 才 `addRoute`；`isFrame=1` 走外链（`window.open` / `<a>`）
- route meta 映射：

| 菜单字段 | route meta |
|----------|------------|
| name | `title` |
| icon | `icon` |
| hidden | `hidden` |
| requiresAuth | `requiresAuth` |
| isCache | `noCache`（取反） |
| query | `query` |
| permission | `permission`（可选） |

- 映射缺失时 fallback 到 Dashboard，属数据问题（§12），不是路由框架故障

### 10.3 按钮权限指令（RuoYi `v-hasPermi` 语义）

```ts
// 权限集来自 /me/permissions；admin 后端已下发全量，前端无需特判角色名
function hasPermi(p: string) {
  return authStore.permissions.includes(p)
}
// 模板：v-if="hasPermi('user:user:create')" 或全局指令 v-hasPermi="['user:user:create']"
```

- **禁止**硬编码角色名判断；只认 permission 字符串
- 权限字符串与 `XxxPermissionConstants` 同源；改动菜单 seed 后同步常量与前端
- `/me/*` 需登录态；401 → 登录门面；业务失败 HTTP 200 + `code` 分流（`CLAUDE.md` §5.3）

---

## 11. data_scope 与缓存

### 11.1 data_scope（列已锁定，强制语义预留）

**当前锁定（写入/校验用）：**

| 值 | 语义 | 当前 |
|----|------|------|
| 1 | 全部 | 默认；可落查询 |
| 2 | 本部门 | **预留**；随组织模型生效 |
| 3 | 仅本人 | 可选：按 `create_by`/`owner_id` 过滤 |

**与 RuoYi 对齐的演进（组织模型落地时再扩，勿提前改枚举）：**

| RuoYi | 语义 | 本设计映射 |
|-------|------|------------|
| 1 全部 | 全部数据 | = 1 |
| 2 自定义 | 按 `sys_role_dept` 圈定 | 新增角色-部门表时引入 |
| 3 本部门 | `dept_id =` 本人部门 | ≈ 现 2 |
| 4 本部门及以下 | 含子部门 | 扩展 2 或新增 |
| 5 仅本人 | 按创建人 | = 3 |

- 角色可带多条时：取**最宽**（min）或显式规则随业务变更锁定
- 本框架只保证**存取与枚举**；查询过滤在各业务 Service 扩展，**不**引入 RuoYi `@DataScope` 式 SQL 拦截器（§1.3）

### 11.2 缓存（可选）

| 对象 | 建议 |
|------|------|
| `permissionsForUser` / `myPermissions` | 短 TTL（如 60s）+ 菜单/角色变更后按 userId 失效 |
| 键名 | `{服务名}:rbac:perm:{userId}`（`CLAUDE.md` §6.9） |
| 禁止 | 无 TTL 永久缓存；跨系统共享权限缓存 |

---

## 12. 避坑清单（接入必读）

1. **AuthContext/UserContext 必须同时认 JWT `sub` 与网关 `X-User-*`**，否则 `/me/*` 按 userId=0 查空。
2. **网关必须从 Bearer JWT 注入 `X-User-*`**；只认 `X-Dev-*` 会导致 admin 全量与 `/me/*` 失效。
3. **客户端同名头一律剥除**后再注入，防伪造身份。
4. **`sys_menu.component` 必须与前端 `componentByPath` 键完全一致**；空 component → 有菜单无页面。
5. **JWT 不含权限码**；auth-service 的 `@PreAuthorize` authorities 必须验签/恢复会话时从 RBAC 装载。
6. **业务服务禁用 `@PreAuthorize` / starter-security**（auth 例外）；否则 `/internal` 误 401、出现 generated password。
7. **网关 `lb://` 必须显式依赖 LoadBalancer**，否则 `/me/*` 503（与 RBAC 无关但同路径易混）。
8. **admin 全量不要依赖 `sys_role_menu` 逐条绑**；新建菜单对 admin 应立即可见。
9. **`INSERT IGNORE` 种子不更新旧行**；改 component 等需附 `UPDATE`（模板已有）。
10. **角色授权用覆盖语义**时前端要回传完整 `menuIds`，避免误清空。
11. **删除菜单先校验子节点**；只逻辑删除自身会留下孤儿树。
12. **permission 唯一性在应用层**（目录 `''` 多行）；不要建列级唯一索引一刀切。
13. **禁止 `id=0`**；根只用 `parent_id=0`。目录/菜单 id 按深度 `d×1000+n`（300x=三级菜单）；**按钮 800x、接口点 500x**（§2.0.5）。
14. **`createMenu` 禁止客户端传 id / 自增**；服务端按段/本层 `max+1` 分配（§2.0.2 / §2.0.5）。
15. **按钮/接口点必须挂 type=2 菜单**；禁止挂根或目录。

---

## 13. 相关文档

| 文档 | 关系 |
|------|------|
| `CLAUDE.md` §5.2 / §6.4.5 / §6.5 | 权限标识、纯网关鉴权、审计发号、库表规范（上位契约） |
| RuoYi / RuoYi-Vue | 设计参考（菜单树、角色授权、动态路由、数据范围）；对照取舍见 §1.3 |
| `example-system-design.md` / `example-mono-design.md` | 模板系统落地引用本文件 |
| `user-center-design.md` | 用户主数据；角色分配「选人」经 `user-api` |
| `unified-auth-center-design.md` | 认证中心例外（`@PreAuthorize` + 权限装载） |
| `deploy/db/seed/rbac-framework-template.sql` | L2/L3 种子模板 |
| `docs/template/fixbug/2026-09-25-unify-login-sso-gateway.md` | `/me/*` 空、component、网关注入等实测缺陷 |
