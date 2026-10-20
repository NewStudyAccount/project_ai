# Proposal: restyle-portal-aurora

## Why

`restyle-admin-aurora`（已归档）把三个管理端切换到了「极光 Aurora」视觉基线，但门户侧仍在旧视觉上：`auth-portal` 登录门面还是「云白」token（天蓝 `#0284c7` 主色、12px 圆角旧阴影），`blog-portal` 公开博客站则是 EP 默认蓝 `#409eff` + 默认灰阶的临时样式——与管理端新基线并存形成品牌割裂。作为 restyle-admin-aurora 遗留待办的收尾，门户侧需对齐「极光 Aurora」品牌视觉。

## What Changes

- **auth-portal（登录门面，`admin-ui-design.md` §4.5 管辖）· 全量换装**：
  - `src/styles/main.css`：token 子集切换极光取值（`--bg-page #f3f5fb`、`--primary #6d5df6`、文字深靛墨系、`--border #e6e4f4`、圆角 16/999、紫调阴影、`--el-color-primary` 系列含 success/warning/danger 派生映射）
  - 登录卡片：圆角 16 + 紫调阴影；主按钮 `--accent-gradient` 渐变胶囊 + `--primary-glow` 光晕（对齐 §4.5「主按钮渐变胶囊」）
  - `Login.vue` 硬编码 `#909399` → `var(--text-secondary)`
- **blog-portal（公开博客站，非管理端）· 品牌色对齐（轻量口径）**：
  - **不套**管理端壳（无侧栏/顶栏改造）、**不动**布局与组件结构——仅品牌视觉层：主色/链接色 `#409eff` → 靛紫 `--primary`、页面底 `#f7f8fa` → `--bg-page`、文字灰阶 → 深靛墨系、卡片圆角 8→16 + 紫调阴影、代码块底色随 token
  - `src/styles/main.css` 引入极光 token 子集；`App.vue` 与 7 个 view 中的硬编码色值（`#409eff`/`#ecf5ff`/`#303133`/`#606266`/`#909399`/`#ebeef5`/`#f2f2f2`/`#f5f7fa`/`#fff`）全部 token 化
  - 主题色对齐后标签/胶囊（TagView `#ecf5ff`/`#409eff`）改用 `--primary-light`/`--primary` 配对

## Capabilities

### New Capabilities

- `portal-aurora-visual`: 门户侧「极光 Aurora」品牌视觉对齐——auth-portal 登录门面全量换装（§4.5）、blog-portal 公开博客品牌色/圆角/阴影轻量对齐

### Modified Capabilities

（无。`admin-aurora-visual` 的适用范围为三管理端，本变更不修改其需求；blog-portal 的布局/阅读体验归 `blog-content-domain` 等内容域能力，本变更不触碰。）

## Impact

- **代码**（仅门户前端，不触碰后端/管理端）：
  - `frontend/auth-portal`：`src/styles/main.css`、`src/views/Login.vue`（样式层）
  - `frontend/blog-portal`：`src/styles/main.css`、`src/App.vue`、`src/views/{Home,ArticleList,ArticleDetail,Category,Tag,About,NotFound}View.vue`（样式层色值 token 化）
  - **不碰**：`frontend/{auth,user,blog}-admin`（已归档 restyle-admin-aurora）、任何模板结构/script/请求、后端
- **设计文档**（不改）：`admin-ui-design.md`（§1 token 体系 + §4.5 登录页约定为基准）；blog-portal 的轻量口径属「品牌色对齐」，不新增设计方言，取值全部出自 `tokens-aurora.css`
- **API / 数据**：零变更

## Non-goals（项目边界，对齐 CLAUDE.md §1 / §2.2）

- 不改后端、不新增/修改接口、DTO、错误码、库表
- 不给 blog-portal 套管理端壳（侧栏/顶栏/Dashboard 语言不适用公开博客）
- 不改 blog-portal 布局/组件结构/阅读体验（文章排版、代码高亮等归内容域）
- 不做暗色主题、移动端重排、多语言
- 不引入新 UI 框架、第三方图标包
- 不顺手重构无关组件/工具（CLAUDE.md §7.8 最小改动）
