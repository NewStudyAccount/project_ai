# Tasks: adopt-markdown-editor

> 顺序：基线登记 → 装包 → 薄封装 → 替换写正文页 → 自检。  
> 自检：`cd frontend/blog-admin && npm run lint && npm run type-check`（不以测试栈作门禁）。

## 1. 选型与基线

- [x] 1.1 在 `docs/version-baseline.md` 前端依赖表登记 **md-editor-v3**（写清用途：管理端写正文 Markdown 编辑器；版本按 npm 当前稳定线锁定并写入变更记录）
- [x] 1.2 在 `docs/blog-system/business-design.md` §E 将「编辑器形态」改为 **md-editor-v3（项目薄封装）**，保留源文/禁 HTML/分栏预览等约定

## 2. 依赖与封装

- [x] 2.1 `frontend/blog-admin` 安装 `md-editor-v3`（版本与基线一致，更新 `package-lock.json`）
- [x] 2.2 新增 `src/components/markdown/MdEditor.vue` 薄封装：`v-model` 源文、中文语言、主题适配 EP、**禁用上传**、粘贴降纯文本/Markdown、过滤 `javascript:`；可选图片=插入 `![](url)`
- [x] 2.3 封装不暴露危险能力（不直连 MinIO、不提交 HTML 到父组件）

## 3. 写正文页替换

- [x] 3.1 `ArticleContentEditor.vue` 用 `MdEditor` 替换自研工具栏/textarea/preview 分栏（或保留外层字数条与双轨按钮）
- [x] 3.2 保存/发布仍只传 `content`（Markdown 源文）+ SEO；≤500KB 门禁保留
- [x] 3.3 移除已无用的自研插入逻辑（`onToolbar` 等），避免双实现

## 4. 自检与回归

- [x] 4.1 `npm run lint && npm run type-check`（blog-admin）
- [ ] 4.2 手测：工具栏插入、预览、保存草稿/发布/放弃草稿；确认请求体为 Markdown 源文
- [x] 4.3 回写本变更 tasks 勾选；如有实施偏差回写 `docs/blog-system` §E 一行
