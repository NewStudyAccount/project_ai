# Design · align-auth-admin-proto

## Context

- `frontend/auth-admin` 已有业务页：`ClientList` / `GrantList` / `AuditLogList` / `LoginAttemptList` / `UserRoleList`，各自手写筛选 + 表格 + 弹窗，形态与交互不统一。
- `docs/admin-system-prototype/`（HTML 样张）给出了目标交互：P-List 全量、密钥 D-Panel、审计详情、二次确认、软胶囊状态。
- `docs/template/generic-admin-page-design.md` 定义了工程落点：`components/common/CrudPage.vue` + `CrudForm.vue` + 每页 `xxx.config.ts`（PageSchema）。
- 视觉基线（云白 token、App Shell、组件质感）由 `docs/template/admin-ui-design.md` 与已完成的 `align-frontend-cloud-paper` 提供；本变更只消费。
- 在途变更 `align-auth-rbac-ui` 正在收敛 `MenuList` / `RoleList` 与 RBAC 后端契约；文件与本变更不相交。
- 技术栈锁定 Vue3 + TS + Pinia + Element Plus（`CLAUDE.md` §2）；`CLAUDE.md` §5.5 前端结构与 §6 开发规范适用。

## Goals / Non-Goals

**Goals:**

- 业务列表页一份骨架（CrudPage）+ 一份配置（PageSchema），页面差异声明式收敛
- Client 密钥查看/轮换走 D-Panel，安全默认打码
- 审计/登录尝试列表统一审计形态（时间范围筛选 + 详情面板）
- 危险操作二次确认、提交防重、toast 反馈统一
- 首页概览轻量降级，不虚构数据

**Non-Goals:**

- 不改后端、接口、DTO、错误码、库表（纯前端）
- 不做菜单/角色/授权树（`align-auth-rbac-ui`）
- 不做登录流程与 auth-portal 外观
- 不做暗色主题 / 移动端 / 图表库 / 跨系统组件包

## Decisions

**D1 · 配置驱动 CrudPage，而非每页手写骨架**
按 `generic-admin-page-design.md` §3 的 PageSchema（`permissionPrefix` / `api` / `columns` / `filters` / `primaryActions` / `rowActions` / `form`）实现 `CrudPage.vue` + `CrudForm.vue`，页面只写 `xxx.config.ts` + 薄模板。
备选：继续每页手写（现状）——否决：形态持续漂移、每页重复实现权限/确认/分页逻辑。
组件按 §6 **同构复制**到各系统（本变更只落 auth-admin 的一份），不建跨系统 npm 包（CLAUDE.md §5.1 多系统独立）。

**D2 · 密钥查看独立 D-Panel，默认打码**
密钥不在列表行内明文展示；「查看密钥」打开 D-Panel（打码 → 显式点击显示 → 复制），轮换走 danger 二次确认。密钥只存组件内存态，不入 localStorage / console / URL。
备选：行内直接明文 + 复制图标——否决：列表常驻明文、截图/共享屏幕泄露面大。

**D3 · 审计详情走 D-Panel 键值对，不做整页跳转**
对齐 `admin-ui-design.md` §3.4 D-Panel 模式与原型；TraceId 等字段仅当后端出参提供才渲染（不自造字段，CLAUDE.md §6.2 前端不自造枚举/字段）。

**D4 · 首页统计卡片按接口可得性降级**
原型统计数字为 mock；真实实现只消费现有接口。无统计接口时首页只留说明卡 + 快捷入口。**不为首页新增后端统计接口**（本变更纯前端；后续需要时另开变更）。

**D5 · 与在途变更的文件分工**
`MenuList` / `RoleList` 归 `align-auth-rbac-ui`；`styles/main.css`、`AppLayout.vue` 的 token/壳外观归 `align-frontend-cloud-paper` 结论（实施时如发现残留旧 token，仅做对齐替换、不重写壳结构）。冲突以 `admin-ui-design.md` / `generic-admin-page-design.md` 为准。

## Risks / Trade-offs

- [CrudPage 抽象过度，页面特殊交互放不进 schema] → 行内操作/列 slot 预留扩展点；骨架级差异只在组件内裁决，页面禁止覆写骨架（§6/§7 自检）
- [与 `align-auth-rbac-ui` 并行改同一工程产生 git 冲突] → 文件目录不相交（views/{client,grant,audit,user} vs views/{menu,role}）；合并顺序建议先归档 RBAC 变更再 apply 本变更，或按目录分别合并
- [密钥在前端内存中驻留] → 组件销毁即释放；不打印、不持久化；轮换后立即更新展示值
- [PageSchema 类型与后端 VO 字段漂移] → schema `prop` 与 `src/types` 中 VO 类型对齐，lint/type-check 门禁（CLAUDE.md §3.2）

## Migration Plan

纯前端重构，无数据/接口迁移。实施顺序：

1. 落地 CrudPage / CrudForm / PageSchema 类型（不接线，先过 lint / type-check）
2. 逐页改造：ClientList（含密钥 D-Panel）→ GrantList / UserRoleList → AuditLogList / LoginAttemptList → Dashboard
3. 每页改造后 `npm run lint && npm run type-check` 通过再进入下一页
4. 回滚策略：按页面独立提交，可单页 revert；无后端兼容性问题

## Open Questions

- 首页统计卡片的数据来源（现有接口是否提供计数类出参）——实施首日确认；若无则按 D4 降级，不阻塞其余任务。
