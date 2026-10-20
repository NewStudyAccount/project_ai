# Proposal: align-user-admin-proto

## Why

`docs/admin-system-prototype/` 的 `user-list.html`（P-List 全量形态：多条件筛选、分页、D-Form 校验、编辑锁定、重置密码、删除二次确认）与 `docs/template/generic-admin-page-design.md` 的配置驱动 CrudPage 契约已定稿，但 `frontend/user-admin` 的账号管理 / 审计列表仍是手写骨架，筛选能力、校验反馈、危险确认与状态样式不齐。需按原型把用户中心管理端**账号与审计页**收敛为配置驱动全量形态，与在途 RBAC 变更划清边界。

## What Changes

- 在 `frontend/user-admin` 落地系统内通用骨架 `components/common/CrudPage.vue` + `CrudForm.vue` + `PageSchema` 类型（`generic-admin-page-design.md` §3/§6 同构复制；不建跨系统包）
- `UserList.vue` 收敛为 P-List 全量——对齐原型 `user-list.html`：
  - 筛选：用户名/姓名关键词、角色、状态、创建时间范围（变更即查/回车即查，页码重置 1）
  - D-Form 新建/编辑：必填校验字段下红字、编辑锁定用户名、状态胶囊单选
  - 行内操作：编辑 / 重置密码（二次确认）/ 删除（danger 二次确认 + toast + 刷新）
- `AuditList.vue` 收敛为审计形态 P-List（时间范围筛选 + 详情 D-Panel 键值对）——对齐原型 `audit-log.html`
- `Dashboard.vue` 收敛为首页概览（说明卡 + 快捷入口；统计卡片按接口可得性降级）
- 全局对齐：软胶囊状态、胶囊主按钮、无权限整钮不渲染（`hasPermission`）

## Capabilities

### New Capabilities

- `user-admin-crud-ui`: 用户中心管理端账号/审计列表全量形态与配置驱动 CrudPage 骨架（筛选、D-Form 校验、重置密码、危险确认、详情 D-Panel、首页概览）

### Modified Capabilities

（无。`openspec/specs/` 尚无既有 spec；菜单管理 / 角色管理 / 授权树与 RBAC 契约归 `align-user-rbac-ui`（`user-rbac` / `user-admin-ui`），本变更不改其需求；视觉 token 归 `align-frontend-cloud-paper`。）

## Impact

- **代码**（仅 `frontend/user-admin`，不触碰后端）：
  - 新增：`src/components/common/CrudPage.vue`、`CrudForm.vue`、`src/types/crud.ts`
  - 改造：`src/views/user/UserList.vue`、`audit/AuditList.vue`、`Dashboard.vue`
  - 不碰：`views/menu/MenuList.vue`、`views/role/RoleList.vue`（`align-user-rbac-ui` owns）
- **参照文档**：`docs/admin-system-prototype/user-list.html`（样张）、`docs/template/generic-admin-page-design.md`（工程契约，冲突以它为准）、`docs/template/admin-ui-design.md`（视觉）
- **API / 数据**：零变更。不新增接口、DTO、错误码、库表；账号字段/枚举以后端契约为准（前端不自造）；id 一律 string
- **并行变更边界**：
  - `align-user-rbac-ui`（15/16 进行中）owns：RBAC 后端、菜单/角色页骨架——文件不相交
  - `align-frontend-cloud-paper`（已完成）owns：token/壳外观——本变更不新增色值

## Non-goals（项目边界，对齐 CLAUDE.md §1 / §2.2）

- 不改后端、不新增/修改接口、DTO、错误码、库表（纯前端）；密码重置仅前端动作接线，**不改密码/凭证逻辑**（归认证中心）
- 不做菜单管理、角色管理、授权树交互（`align-user-rbac-ui` 范围）
- 不做组织/部门模型扩展、数据范围（data_scope）语义变更
- 不做登录流程与 `auth-portal` 登录页
- 不做暗色主题、移动端重排、多语言强制交付、Excel 导入导出
- 不引入新 UI 框架、第三方图标包、渐变/毛玻璃装饰、跨系统共享组件包
- 不顺手重构与本变更无关的组件/工具（CLAUDE.md §7.8 最小改动）
