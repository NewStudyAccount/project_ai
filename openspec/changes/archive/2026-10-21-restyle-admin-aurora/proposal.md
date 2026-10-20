# Proposal: restyle-admin-aurora

## Why

现行管理端视觉基线「云白」全浅极简被用户裁决**整体风格过于单调**（2026-10-20）；旧比选四方案（云白/墨岩/石墨/霜钢）经样张复看均不采用，全新设计语言**「极光 Aurora」**已出完整样张（`docs/template/admin-ui-style-aurora.html`）并获用户认可。设计文档已先行切换（`admin-ui-design.md` v「极光 Aurora」+ 新建 `tokens-aurora.css`，跨文档引用同步）。需把三个管理端（`auth-admin` / `user-admin` / `blog-admin`）从云白 token 体系切换到极光 Aurora 视觉基线，消除「文档已换、代码未换」的漂移窗口。

## What Changes

- **Token 换装**（三端 `src/styles/main.css`）：16 项同名变量换值（页面底/文字三级/边框/主色 `#6d5df6` 靛紫系/状态色/圆角 16/10/999/间距 20/10/紫调阴影/`--el-color-primary` 全系列）+ 8 项新增（`--bg-sidebar-gradient`、`--sidebar-glow`、`--accent-gradient`、`--primary-glow`、`--shadow-card-hover`、`--stat-grad-1…4`，另有状态胶囊 5 组底/字配对 token）——取值以 `docs/template/tokens-aurora.css` 为唯一来源
- **壳样式换装**（三端 `src/components/AppLayout.vue`）：侧栏深靛紫极光渐变 + 顶部光晕 + 玻璃拟态激活项 + 渐变激活竖条；顶栏白面下缘细线；`el-menu` 深色模式
- **组件视觉对齐**：主按钮渐变胶囊 + 光晕（`--accent-gradient` + `--primary-glow`）、卡片 hover 抬升、状态软胶囊五组固定配对、分页当前页渐变强调、Dashboard 统计卡渐变（仅接口可得时，维持既有降级语义）
- **旧色值清底**：三端 grep 云白残值（`#0284c7`/`#0369a1`/`#f0f9ff`/`#0c4a6e`/`#18181b` 等硬编码）零残留，一律 token 化
- **纯视觉层**：布局、交互、请求、权限判定零变化；页面形态归 `align-blog-admin-proto` / `align-user-admin-proto` / `align-auth-admin-proto`（本变更不改其形态与行为）

## Capabilities

### New Capabilities

- `admin-aurora-visual`: 三管理端统一「极光 Aurora」视觉基线——token 体系（色板/圆角/阴影/间距）、应用壳（渐变侧栏/光晕/玻璃激活/顶栏）、组件视觉约定（渐变主按钮/状态软胶囊/统计卡渐变/弹窗阴影）

### Modified Capabilities

（无。`blog-admin-crud-ui` / `user-admin-crud-ui` / `auth-admin-crud-ui` 的形态与行为需求不变；在途变更制品中「骨架样式 SHALL 只用『云白』token」措辞泛指 `admin-ui-design.md` §1 token 体系（同名变量换值），基线换装后自动指向极光 Aurora，不构成需求修改，本变更不触碰其制品。）

## Impact

- **代码**（仅三管理端前端，不触碰后端/门户）：
  - `frontend/blog-admin`：`src/styles/main.css`、`src/components/AppLayout.vue`（+ 视图内硬编码旧色值清底，若有）
  - `frontend/user-admin`：同上两文件（+ 清底）
  - `frontend/auth-admin`：同上两文件（+ 清底）
  - **不碰**：`CrudPage.vue` / `CrudForm.vue` / `DetailDialog.vue`（骨架零改动，走 token 自动换肤）、任何业务视图逻辑/模板结构、`views/menu`、`views/role`、后端
- **设计文档**（本提案创建前已按用户指令先行更新，本变更不再改）：`docs/template/admin-ui-design.md`（重写为极光 Aurora）、`docs/template/tokens-aurora.css`（新建）、`docs/template/tokens-cloud-paper.css`（标废）、`docs/template/generic-admin-page-design.md` 与 `docs/blog-system/README.md`、`docs/object-storage-design/*` 引用切名
- **API / 数据**：零变更。不新增接口、DTO、错误码、库表
- **并行变更分工（硬边界）**：
  - `align-blog-admin-proto` / `align-user-admin-proto`（未归档，缺口注记在案）owns：页面形态与业务字段——本变更只动视觉层（token/壳/组件样式），遇到结构冲突以彼为准
  - `blog-object-storage-*` owns：文件/服务商业务——本变更零触碰
  - 样张 `admin-ui-style-aurora.html` 为视觉细节参照；token 取值冲突以 `tokens-aurora.css` / `admin-ui-design.md` §1 为准

## Non-goals（项目边界，对齐 CLAUDE.md §1 / §2.2）

- 不改后端、不新增/修改接口、DTO、错误码、库表（纯前端视觉层）
- 不改任何页面布局/交互/请求/权限行为（P-List/D-Form/D-Panel 形态归 `align-*-proto`）
- 不做 auth-portal / blog-portal 门户换装（另行跟进，见 admin-ui-design.md §4.5/§8 裁决注）
- 不做暗色**内容区**主题、移动端重排、多语言、图表库
- 不引入新 UI 框架、第三方图标包、装饰插画
- 不修改任何 OpenSpec 制品（proposal/specs/design/tasks of other changes）
- 不顺手重构与本变更无关的组件/工具（CLAUDE.md §7.8 最小改动）
