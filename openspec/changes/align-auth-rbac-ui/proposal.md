# Proposal: align-auth-rbac-ui

## Why

`docs/template/rbac-design.md`（§2.0 id 规范、§9 校验、§3/§6.4 运行时下发）与 `docs/template/admin-ui-design.md`（视觉基线、App Shell、页面骨架）已定稿，但 `auth-service` / `auth-admin` 实现未跟上：`createMenu`/`createRole` 仍走 16 位发号，结构校验缺失，`/me/*` 过滤与组树不完整，页面骨架未按统一布局模式收敛。需按已确认设计把认证中心对齐到契约，避免种子短号与运行时长号混用、菜单树与前端布局持续漂移。

## What Changes

- **后端 `auth-service`（RBAC）**
  - `createMenu` 按 `rbac-design.md` §2.0 分配短号：type=1/2 层级 `d×1000+n`（d≤4）、type=3 `90001–99999`、type=4 `5001–5999`；禁止客户端传 id / DB 自增 / 改历史 id
  - `createRole` 按 §2.0.3 段内短号 `9001–9999`（`max+1`）
  - 补 §9 结构校验：深度上限、type=3/4 父必须 type=2、禁止挂根/目录、parent 存在性；关联表运行时可仍用 framework 16 位（代理键，§2.0.3）
  - `/me/menus` 仅 `status=1` 且 type∈{1,2}，授权集取**祖先闭包**后组树；`/me/permissions` 非空 `permission` 去重
  - 错误码进 `AuthErrorCodeEnum`（`202xxx` 业务序号段内递增，系统号已登记 `docs/error-code-ranges.md`）
- **前端 `auth-admin`（按 `admin-ui-design.md`）**
  - 收敛 App Shell（侧栏 220 / 顶栏 60 / `.page-card`）
  - 菜单管理对齐 **P-Tree + D-Form**（type 字段联动、不暴露 id 输入）
  - 角色管理对齐 **P-List + D-Form + D-Panel**（授权树回显）
  - 列表/按钮仍走 `hasPermission`；禁止角色名硬编码
- **不改** 公共 API 路径与 `{code,msg,data}` 契约；不改登录/OIDC/Client 业务

Non-goals（项目边界，对齐 `CLAUDE.md` §1 / `rbac-design.md` §1.2）：

- **不做**集中式 RBAC、跨系统角色/菜单同步
- **不做**用户主数据、密码/令牌逻辑变更
- **不做**组织/部门模型与 `data_scope=2` 强制语义
- **不做**`@DataScope` SQL 拦截器、通配权限 `*:*:*`
- **不做**扩展列（`query`/`is_frame`/`is_cache`/`menu_check_strictly`）建列——另案；本变更不扩表
- **不做**暗色主题、移动端重排、独立视觉稿交付物（以 `admin-ui-design.md` 为契约）
- **不做**Excel / 分布式定时 / 多语言 / 平行 UI 库（`CLAUDE.md` §2.2）

## Capabilities

### New Capabilities

- `auth-admin-ui`: 认证中心管理端视觉基线与页面布局（App Shell、P-List/P-Tree/D-Form/D-Panel、登录门面外跳约束）

### Modified Capabilities

- `auth-admin-rbac`: 菜单/角色主键改为 §2.0 短号分配；补齐创建结构校验；`/me/menus` 仅启用节点并按祖先闭包组树

## Impact

- **代码**：仅 `backend/auth/auth-service`（`RbacServiceImpl`、错误码枚举）与 `frontend/auth-admin`（`AppLayout`、`MenuList`、`RoleList`、`main.css`）；**不触碰** `user` / 其它系统源码（`CLAUDE.md` §5.1）
- **数据**：不改表结构；运行时新建菜单/角色 id 形态变为短号（种子已为短号，同表双 ID 空间消除）；既有 16 位菜单/角色行不迁移（禁止改历史 id）
- **API**：路径与出参结构不变；出参 id 仍为 String
- **错误码**：`AuthErrorCodeEnum` 在 `202016+` 递增；**不**新增系统号
- **依赖**：无新增组件；不改 `docs/version-baseline.md`
