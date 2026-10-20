# Design · align-blog-admin-proto

## Context

- `frontend/blog-admin` 实体页多且各有骨架：`CategoryList` / `TagList` / `ArticleList` / `FileList` / `StorageProviderList` / `AuditList`；另有内容编辑器与站点配置页。
- 形态目标：`docs/admin-system-prototype/`（P-List 全量样张）+ `docs/template/generic-admin-page-design.md`（CrudPage 工程契约）；视觉：`docs/template/admin-ui-design.md`（云白）。
- **多变更并行**是本变更最大约束：`blog-object-storage-frontend`（FileList / StorageProviderList 业务字段与动作）、`blog-content-domain` / `adopt-markdown-editor` / `article-content-markdown-only`（文章内容/编辑器）均在途，且 `scaffold-blog-system` 尚未归档。
- 技术栈 Vue3 + TS + Pinia + Element Plus（`CLAUDE.md` §2）。

## Goals / Non-Goals

**Goals:**

- CrudPage 骨架落地 blog-admin（页面差异配置化）
- 分类 / 标签页达到全量形态（筛选、校验、删除保护）
- 文章 / 文件 / 服务商页完成**形态层**收敛（行为零变化）
- 审计 + Dashboard 收敛
- 与在途变更文件/字段分工可执行、冲突面最小

**Non-Goals:**

- 不改后端 / 接口 / DTO / 错误码 / 库表
- 不改文章内容域、编辑器、发布语义
- 不改文件/服务商业务动作与字段（上传、测试连通、设默认、SK 处理、引用检查）
- 不做菜单/角色/授权树、站点配置页改造
- 不做暗色主题 / 移动端 / 图表库 / 跨系统组件包

## Decisions

**D1 · CrudPage 同构复制 + 自定义 action 扩展点**
`generic-admin-page-design.md` §6 的 CrudPage 复制进 blog-admin 一份。blog 页业务动作多（上传/测试连通/设默认），PageSchema 的 `primaryActions` / `rowActions` 必须支持自定义动作接线（`permission` 显式全串 + `confirm` 文案），骨架统一提供确认/toast/刷新。
备选：每页手写——否决：形态继续漂移。

**D2 · 形态层 / 业务层硬分离（与在途变更的分工协议）**
本变更只动：模板骨架（toolbar/table/pagination/dialog 布局）、筛选布局、校验提示、确认与 toast 反馈。业务层（请求参数、动作处理、字段语义）保持现状逐行不动。冲突时：**业务字段以在途变更为准，形态以本变更为准**。
备选：等所有在途变更归档再动——可行但阻塞；折中方案是文件级分工 + 每页独立提交（见 Risks）。

**D3 · 删除保护只展示后端语义**
分类/标签的引用约束由后端判定（业务 code + `msg`，HTTP 200 分流，CLAUDE.md §5.3）；前端只做二次确认 + 展示 `msg`，不自行查引用、不自造文案分支。

**D4 · 文章列表只收敛列表形态**
ArticleList 收敛到 toolbar/表格/确认反馈；点击编辑跳转/弹出编辑器的链路与编辑器本体一律不动（`adopt-markdown-editor` owns）。状态列 pillTone 映射后端枚举 `desc`（前端不自造枚举值）。

**D5 · Dashboard 降级策略**
同 auth/user 变更：统计卡片只消费现有接口，无则降级说明卡 + 快捷入口；不新增后端接口。

## Risks / Trade-offs

- [多变更并行改同目录文件（FileList / StorageProviderList）] → 形态层与业务层分离改（D2）；建议 apply 时机：`blog-object-storage-frontend` 归档后，或与持有者合并协调；每页独立提交便于单页 revert
- [形态收敛误伤业务逻辑] → tasks 明确「行为零变化」验收：改造前后请求参数/结果处理对照；`npm run lint && type-check` + 手测关键动作
- [CrudPage 扩展点不足承载 blog 特有交互（如缩略图列、上传对话框）] → 列渲染 slot + 自定义 action；仍不足的页面允许局部 slot 注入，但禁止覆写骨架骨架级布局
- [三系统 CrudPage 复制漂移] → 骨架级改动走变更并三系统同步（与 auth/user 变更同一契约文档）

## Migration Plan

纯前端重构，无数据/接口迁移。建议顺序（依赖就绪度）：

1. CrudPage / CrudForm / PageSchema 类型落地（含自定义 action 扩展点）
2. 低风险页先行：CategoryList / TagList → AuditList → Dashboard
3. 高并行页最后（待协调）：ArticleList → FileList / StorageProviderList（形态层改造，逐动作手测）
4. 每页独立提交 + `npm run lint && npm run type-check` 通过
5. 回滚：按页面提交单页 revert；无后端兼容性问题

## Open Questions

- FileList / StorageProviderList 的 apply 时机（是否等 `blog-object-storage-frontend` 归档）——实施开始前与该变更持有者确认；不影响其余页面先行。
- 统计卡片数据源（现有接口可提供的计数）——实施时确认，无则降级。
