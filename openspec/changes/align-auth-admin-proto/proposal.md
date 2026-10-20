# Proposal: align-auth-admin-proto

## Why

`docs/admin-system-prototype/` 已交付可交互原型（P-List 全量形态、Client 密钥 D-Panel、审计详情、二次确认、状态软胶囊），`docs/template/generic-admin-page-design.md` 已定稿配置驱动 CrudPage 工程契约，但 `frontend/auth-admin` 的业务列表页（Client / Grant / 审计 / 登录尝试 / 用户角色）仍各写各的骨架：筛选、分页、表单校验、危险确认与状态样式形态不一，新页面只能继续复制粘贴。需按原型与工程契约把认证中心管理端**业务列表页**收敛为配置驱动全量形态，与在途的 RBAC/菜单角色变更划清边界。

## What Changes

- 在 `frontend/auth-admin` 落地系统内通用骨架 `components/common/CrudPage.vue` + `CrudForm.vue` + `PageSchema` 类型（`generic-admin-page-design.md` §3/§6 同构复制；**不建跨系统包**，各系统独立）
- `ClientList.vue` 收敛为 P-List 全量 + **D-Panel 密钥查看**（默认打码、显示/复制、轮换二次确认）——对齐原型 `client-list.html`
- `GrantList.vue`、`UserRoleList.vue` 收敛为配置驱动 P-List（筛选变更即查、分页 `current` 从 1 起、D-Form 校验失败字段下红字、编辑锁定不可变字段）
- `AuditLogList.vue`、`LoginAttemptList.vue` 收敛为审计形态 P-List（时间范围筛选 + 详情 D-Panel 键值对展示）——对齐原型 `audit-log.html`
- `Dashboard.vue` 收敛为首页概览（说明卡 + 快捷入口；统计卡片按接口可得性降级，不虚构接口）
- 全局对齐：删除类危险操作一律二次确认 + 成功 toast ≤ 2s + 刷新当前页；状态用软胶囊（启用/停用/失败三色）；无权限按钮**整钮不渲染**（`hasPermission`）

## Capabilities

### New Capabilities

- `auth-admin-crud-ui`: 认证中心管理端业务列表页全量形态与配置驱动 CrudPage 骨架（Client / Grant / 审计 / 登录尝试 / 用户角色、密钥 D-Panel、首页概览）

### Modified Capabilities

（无。`openspec/specs/` 尚无既有 spec；菜单管理 / 角色管理 / 授权树与 RBAC 契约归 `align-auth-rbac-ui`（`auth-admin-rbac` / `auth-admin-ui`），本变更不改其需求；视觉 token 归 `align-frontend-cloud-paper`，本变更只消费不重写。）

## Impact

- **代码**（仅 `frontend/auth-admin`，不触碰后端）：
  - 新增：`src/components/common/CrudPage.vue`、`CrudForm.vue`、`src/types/crud.ts`（PageSchema 类型）
  - 改造：`src/views/client/ClientList.vue`、`grant/GrantList.vue`、`audit/AuditLogList.vue`、`audit/LoginAttemptList.vue`、`user/UserRoleList.vue`、`Dashboard.vue`
  - 可能微调：`src/router`（仅当配置驱动需要路由参数时；不改路由契约与权限 meta 语义）
- **参照文档**：`docs/admin-system-prototype/`（视觉与交互样张）、`docs/template/generic-admin-page-design.md`（工程契约，冲突以它为准）、`docs/template/admin-ui-design.md`（云白视觉基线）
- **API / 数据**：零变更。不新增接口、DTO、错误码、库表；出参 id 一律 string 契约不变
- **并行变更边界**：
  - `align-auth-rbac-ui` owns：RBAC 后端、`MenuList` / `RoleList` 骨架收敛——本变更不碰这两个文件
  - `align-frontend-cloud-paper`（已完成）owns：色板/token/壳外观——本变更不新增色值

## Non-goals（项目边界，对齐 CLAUDE.md §1 / §2.2）

- 不改后端、不新增/修改任何接口、DTO、错误码、库表（纯前端变更）
- 不做菜单管理、角色管理、授权树交互（`align-auth-rbac-ui` 范围）
- 不做登录/登出/OIDC 流程与 `auth-portal` 登录页（`unify-login-facade` / `align-frontend-cloud-paper` 已覆盖）
- 不做暗色主题、移动端重排、多语言强制交付、Excel 导入导出
- 不引入新 UI 框架、第三方图标包、渐变/毛玻璃装饰、跨系统共享组件包
- 不顺手重构与本变更无关的组件/工具（CLAUDE.md §7.8 最小改动）
