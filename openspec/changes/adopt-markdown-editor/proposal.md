# Proposal: adopt-markdown-editor

> 状态：提案（**技术选型**）  
> 上游：`article-content-markdown-only`（正文唯一 Markdown；当前写正文页为自研 textarea + 工具栏）  
> 约束：`CLAUDE.md` §2 前端栈（Vue3 + TS + Element Plus）；**不引平行 UI 库**；版本唯一登记 `docs/version-baseline.md`

---

## Why

写正文页需要**成熟 Markdown 编辑器**（工具栏、预览、稳定快捷键与粘贴处理），自研 textarea 覆盖不足、维护成本高。需选定一款与 Vue3 / Element Plus 兼容的编辑器，固定选型与接入边界，避免各页各引各的。

## What Changes

- **技术选型锁定**：在候选中选定 **一款** Markdown 编辑器组件，写入 `docs/version-baseline.md` 前端依赖表（先改基线再装包）
- **接入范围**：仅 **blog-admin 写正文页**（及后续明确登记的写作页）；**不是**全局 UI 框架
- **替换** `ArticleContentEditor.vue` 自研工具栏/分栏，改为选型组件 + 项目侧薄封装（`components/markdown/` 或 `utils`）
- **保留契约**：提交仍是 **Markdown 源文**；≤500KB；双轨保存/发布/放弃草稿；预览不替代保存内容
- **Non-goals**：不做协同编辑、不做多编辑器并存、不改后端格式契约、不在 portal 引入编辑器

### Non-goals（边界）

- 不引入第二套通用 UI 库 / 不替代 Element Plus
- 不做 WYSIWYG 与源码「双模式产品化」并行交付（编辑器内部模式可配，**产品默认一种**）
- 不把编辑器选型写进 `CLAUDE.md` 服务清单；稳定依赖说明进 `version-baseline.md` + 本变更 design

## Capabilities

### New Capabilities

- `markdown-editor-ux`: 写正文页成熟 Markdown 编辑器的交互与源文保存契约（工具栏、预览、粘贴/HTML 边界、字数）

### Modified Capabilities

- `blog-admin-content-ui`（`article-content-markdown-only` / `blog-content-domain`）：写正文页编辑器实现改为选定组件；**规格行为不变**（源文、双轨、禁 HTML 结构）

## Impact

| 面 | 影响 |
|----|------|
| 依赖 | `blog-admin` 新增 **1** 个 Markdown 编辑器 npm 包；版本入 `docs/version-baseline.md` |
| 前端 | `ArticleContentEditor.vue`、可选 `src/components/markdown/*` 薄封装；去掉自研 `onToolbar` 插入逻辑 |
| 渲染 | 预览可由编辑器自带；**对外仍存源文**；与 portal `renderMarkdown` 口径可并存（编辑器预览 ≠ 保存格式） |
| 后端 / DB | **无** |
| 风险 | 包体积上升；需限制能力面（禁文件上传直传 MinIO、禁 HTML 粘贴为结构） |
