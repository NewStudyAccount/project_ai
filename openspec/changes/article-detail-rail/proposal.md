# Proposal: article-detail-rail

> 状态：提案  
> 关联设计：`docs/blog-system/prototype-portal-redesign.html` 详情面板（已加右侧悬浮轨）；`docs/blog-system/api-design.md` §6.4（content=渲染 HTML）  
> 上游变更：`redesign-portal-pages`（未归档，其 specs 为本变更基线）、`blog-content-domain`（未归档）  
> 裁决来源：用户指令——详情页右侧三块悬浮轨（分类/标签、目录点击跳转、返回列表）

---

## Why

文章详情页阅读列两侧留白无信息锚点：读者读长文时无法快速定位章节（服务端渲染的正文含 h2/h3 标题但无目录导航），分类/标签信息与返回入口只在文首/文尾，回看需要滚动整页。用户已裁决：详情页右侧加**三块悬浮区**——分类/标签信息、目录（可点击跳转）、返回文章列表。此设计同时把 `redesign-portal-pages` 中「正文 TOC 浮动目录」从可选增强转正立项。

## What Changes

- **新增右侧悬浮轨（详情页）**：三块 sticky 卡片——
  1. **分类 / 标签**：当前文章分类与标签，**仅展示不跳转**（评审裁决：详情页分类/标签为只读信息）
  2. **目录**：由注入后的渲染 HTML 扫 h2/h3 生成（层级缩进），点击**平滑滚动**到对应标题；正文无标题时该块优雅收起
  3. **返回文章列表**：入口卡片（全页唯一返回入口）
- **布局调整（MODIFIED）**：详情页由「760px 居中阅读列（无侧栏）」改为「阅读列 + 240px 右侧悬浮轨」双列（约 1120px 容器）；<1024px 悬浮轨隐藏（面包屑/顶部固定导航兜底）
- **正文去噪（评审裁决）**：正文上下（元信息下/文末）**不再放置标签信息**，标签仅存右轨①块；面包屑/元信息中的分类同样纯文本展示
- **目录锚点**：注入后的标题无 id 时由前端补 slug 锚点（纯呈现增强，不改服务端渲染输出）
- **不变**：API 契约零改动；正文仍为注入服务端渲染 HTML（不自带转换）；正文排版体系不变

### Non-goals（项目边界，对齐 `CLAUDE.md` §1 / §2.2）

- **不做**目录滚动高亮（IntersectionObserver 监听）——仅点击跳转（不加未被要求的功能）
- 不做目录折叠/展开动画、TOC 进度条、阅读百分比等衍生交互
- 不改 API/后端/DB/存储；不动视觉 token 口径（装饰色仍 token 派生）
- 不改首页/列表/分类/标签/关于各页布局

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

> 基线为未归档变更 `redesign-portal-pages` 的 `specs/blog-portal/spec.md`（其上游为 `blog-content-domain`）。本变更 **MODIFIED**「文章详情阅读体验」需求（无侧栏条款改为右侧悬浮轨），并 **ADDED**「详情右侧悬浮轨」需求。

- `blog-portal`（见 `redesign-portal-pages` → `blog-content-domain`）：详情页布局由「无侧栏阅读列」改为「阅读列 + 右侧三块悬浮轨」；新增悬浮轨（分类/标签、目录跳转、返回列表）与目录生成需求

## Impact

| 面 | 影响 |
|----|------|
| **API** | 零改动（目录由前端扫描注入 HTML 生成，不新增接口） |
| **前端** | `ArticleDetailView.vue` 双列布局；新增 `DetailRail.vue`（三块悬浮卡）+ `ArticleToc.vue`（扫 h2/h3、锚点、平滑跳转）；main.css 补目录样式 |
| **后端/DB/存储** | 零改动 |
| **上游制品** | `redesign-portal-pages` 的「无侧栏」条款由本变更 MODIFIED 覆盖；其「TOC 不做」Non-goal 随本裁决失效（proposal/design 措辞在归档时按 specs 为准） |
