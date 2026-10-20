# Proposal: align-blog-admin-proto

## Why

`docs/template/generic-admin-page-design.md` 明确适用于「各系统 `{system}-admin` 的实体管理页（账号、**分类、文章、文件**、Client…）」，`docs/admin-system-prototype/` 已给出 P-List 全量形态样张，但 `frontend/blog-admin` 的实体页（分类 / 标签 / 文章 / 文件 / 存储服务商）仍是各写各的骨架：筛选、分页、表单校验、删除保护与危险确认形态不齐，与 user-admin / auth-admin 持续漂移出页面方言。需按原型与工程契约把博客管理端实体页收敛为配置驱动全量形态，并与在途内容域/存储变更划清文件与字段分工。

## What Changes

- 在 `frontend/blog-admin` 落地系统内通用骨架 `components/common/CrudPage.vue` + `CrudForm.vue` + `PageSchema` 类型（`generic-admin-page-design.md` §3/§6 同构复制；不建跨系统包）
- **内容域实体页收敛**（形态层改造，业务字段/接口调用保持现状）：
  - `CategoryList.vue` / `TagList.vue`：P-List 全量（关键词/状态筛选、D-Form 必填校验、删除二次确认 + 引用检查提示文案）
  - `ArticleList.vue`：筛选（标题/分类/状态）、分页、状态软胶囊、行内操作确认反馈；**不改**文章内容/编辑器/发布语义
- **存储域形态收敛**（仅骨架与交互形态，**不改**业务字段与动作逻辑）：
  - `FileList.vue`：筛选条/表格/分页/删除确认接入 CrudPage 形态；上传、引用检查、storageCode 等业务逻辑保持现状
  - `StorageProviderList.vue`：D-Form/确认/状态软胶囊对齐；测试连通、设默认、SK 掩码回显等业务动作保持现状
- `AuditList.vue` 收敛为审计形态 P-List（时间范围筛选 + 详情 D-Panel）
- `Dashboard.vue` 收敛为首页概览（说明卡 + 快捷入口；统计卡片按接口可得性降级）
- 全局：软胶囊状态、胶囊主按钮、无权限整钮不渲染（`hasPermission`）

## Capabilities

### New Capabilities

- `blog-admin-crud-ui`: 博客管理端实体页全量形态与配置驱动 CrudPage 骨架（分类/标签/文章/文件/服务商的筛选、D-Form、删除保护、详情 D-Panel、首页概览）

### Modified Capabilities

（无。`openspec/specs/` 尚无既有 spec；文章内容域需求归 `blog-content-domain` / `adopt-markdown-editor` / `article-content-markdown-only`，文件与服务商业务能力归 `blog-object-storage-*` 系列——本变更只收敛页面形态，不改这些能力的任何需求。）

## Impact

- **代码**（仅 `frontend/blog-admin`，不触碰后端）：
  - 新增：`src/components/common/CrudPage.vue`、`CrudForm.vue`、`src/types/crud.ts`
  - 形态改造：`views/content/CategoryList.vue`、`TagList.vue`、`ArticleList.vue`、`file/FileList.vue`、`storage/StorageProviderList.vue`、`audit/AuditList.vue`、`Dashboard.vue`
  - **不碰**：`content/ArticleContentEditor.vue`、`ArticleMainForm.vue`、`SiteConfig.vue`（内容域/编辑器归在途变更）
- **参照文档**：`docs/admin-system-prototype/`（样张）、`docs/template/generic-admin-page-design.md`（工程契约，冲突以它为准）、`docs/template/admin-ui-design.md`（视觉）
- **API / 数据**：零变更。不新增接口、DTO、错误码、库表；枚举/字段以后端契约为准；id 一律 string
- **并行变更分工（硬边界）**：
  - `blog-object-storage-frontend` owns：FileList / StorageProviderList 的**业务字段与动作**（storageCode 筛选、上传选服务商、测试连通、设默认、SK 只写掩码）——本变更只动骨架/筛选布局/确认反馈层，遇到字段冲突以该变更为准
  - `blog-content-domain` / `adopt-markdown-editor` / `article-content-markdown-only` owns：文章内容、Markdown 编辑器、发布语义——本变更不碰编辑器两文件
  - `align-frontend-cloud-paper`（已完成）owns：token/壳外观——本变更不新增色值

## Non-goals（项目边界，对齐 CLAUDE.md §1 / §2.2）

- 不改后端、不新增/修改接口、DTO、错误码、库表（纯前端）
- 不改文章内容域、Markdown 编辑器、发布/发布语义（在途变更 owns）
- 不改存储服务商/文件的业务动作与字段（测试连通、设默认、上传、引用检查、SK 处理）
- 不做菜单管理、角色管理、授权树改造（如需另开变更，不在本变更扩围）
- 不做站点配置页（SiteConfig）改造
- 不做暗色主题、移动端重排、多语言强制交付、Excel 导入导出
- 不引入新 UI 框架、第三方图标包、渐变/毛玻璃装饰、跨系统共享组件包
- 不顺手重构与本变更无关的组件/工具（CLAUDE.md §7.8 最小改动）
