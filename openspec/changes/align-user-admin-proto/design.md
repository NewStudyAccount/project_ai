# Design · align-user-admin-proto

## Context

- `frontend/user-admin` 现有 `UserList.vue` / `AuditList.vue` 手写骨架，筛选与校验能力弱于原型；`Dashboard.vue` 为旧版。
- 目标交互由 `docs/admin-system-prototype/user-list.html`（多条件筛选、D-Form 校验、编辑锁定、重置密码、删除确认）与 `audit-log.html`（时间范围 + 详情 D-Panel）给定。
- 工程契约：`docs/template/generic-admin-page-design.md`（CrudPage + PageSchema）；视觉：`docs/template/admin-ui-design.md`（云白，token 由已完成的 `align-frontend-cloud-paper` 落地）。
- 在途 `align-user-rbac-ui` owns 菜单/角色页与 RBAC 后端（文件不相交）；技术栈 Vue3 + TS + Pinia + Element Plus（`CLAUDE.md` §2）。

## Goals / Non-Goals

**Goals:**

- 账号页达到原型全量形态（四类筛选、校验、锁定、确认、软胶囊）
- 审计页统一时间范围筛选 + 详情 D-Panel
- CrudPage 骨架落地（页面差异配置化）
- 首页概览轻量、可降级

**Non-Goals:**

- 不改后端 / 接口 / DTO / 错误码 / 库表；不改密码凭证逻辑
- 不做菜单/角色/授权树（`align-user-rbac-ui`）
- 不做部门组织模型与数据范围语义
- 不做暗色主题 / 移动端 / 图表库 / 跨系统组件包

## Decisions

**D1 · CrudPage 同构复制进 user-admin**
按 `generic-admin-page-design.md` §6 在本系统复制一份 `CrudPage.vue` + `CrudForm.vue` + `src/types/crud.ts`；与 auth-admin / blog-admin 各自独立，骨架级裁决以设计文档为准，避免三份实现漂移出方言（差异经 openspec 收敛）。
备选：抽跨系统共享包——否决：违反 CLAUDE.md §5.1 多系统独立构建边界。

**D2 · 账号筛选全量四件套（关键词/角色/状态/时间范围）**
对齐原型；筛选变更即查、回车即查，`current` 重置 1。时间范围拆 `beginTime/endTime` 两个请求参数（`generic-admin-page-design.md` §3.2）。

**D3 · 编辑锁定用户名、不暴露 id**
用户名为业务标识，编辑锁定（disabled 回显）；id 由后端发号（16 位 string 契约，CLAUDE.md §6.4.5/§6.4.7），前端不提供输入。备选：允许改名——否决：牵连引用与审计追溯，超出本变更范围。

**D4 · 重置密码只做动作接线**
前端只调用后端重置动作 + 二次确认 + toast；不生成密码、不做复杂度校验、不落任何中间状态（密码逻辑归认证中心/后端）。

**D5 · 审计详情 D-Panel 键值对 + 字段以出参为准**
不自造 TraceId 等字段（CLAUDE.md §6.2）；长文本列省略 + title。

**D6 · Dashboard 降级策略**
统计卡片只消费现有接口；无则只留说明卡 + 快捷入口。不新增后端接口（需要时另开变更）。

## Risks / Trade-offs

- [与 `align-user-rbac-ui` 并行工程合并冲突] → 文件目录不相交（views/user、views/audit vs views/menu、views/role）；建议 RBAC 变更归档后 apply，或按目录分别合并
- [CrudPage 三系统复制后各自演化] → 骨架级改动必须走变更并三系统同步；页面禁止覆写骨架（tasks 自检）
- [筛选参数与后端 VO 漂移] → PageSchema `prop` 对齐 `src/types` VO；lint / type-check 门禁
- [手机号脱敏口径] → 以前端不做二次加工为原则，展示值直接取后端出参

## Migration Plan

纯前端重构，无数据/接口迁移：

1. CrudPage / CrudForm / PageSchema 类型落地（过 lint / type-check）
2. UserList 改造 → AuditList 改造 → Dashboard 收敛（每页独立提交）
3. `npm run lint && npm run type-check` 逐页通过
4. 回滚：按页面提交可单页 revert

## Open Questions

- 统计卡片数据源（现有接口是否提供账号/今日操作计数）——实施首日确认，无则按 D6 降级，不阻塞其余任务。
