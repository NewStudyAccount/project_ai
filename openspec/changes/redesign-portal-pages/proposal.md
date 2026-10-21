# Proposal: redesign-portal-pages

> 状态：提案  
> 关联设计：`docs/blog-system/prototype-portal-redesign.html`（用户端重设计原型，方向已裁决）；`docs/blog-system/business-design.md` §6（信息架构）；`docs/blog-system/api-design.md` §6 / §7.1（接口契约）  
> 上游变更：`blog-content-domain`（未归档，`blog-portal` 能力基线）；视觉基线 `portal-aurora-visual`（已归档，只约束视觉层）

---

## Why

现行 blog-portal 停留在「骨架即成品」：960px 裸卡片流、封面图（`coverFileId`）从未渲染、置顶只是文字列表、详情正文仅 3 条基础样式（图片/引用/代码块/表格排版缺失）、列表无筛选状态与分页可视化。六路由能读到数据，但阅读体验与品牌感均不足。重设计原型 `prototype-portal-redesign.html` 已按「阅读优先 / 契约驱动 / 品牌轻量」三主线出稿并获裁决，本变更将其实装到 `blog-portal`。

## What Changes

- **页面布局（核心）**：六路由按原型重排——
  - 首页 `/`：hero（site.title/description + 主按钮）+ 置顶精选卡区（isTop=1）+ 最新文章列表 + 侧栏（分类树/标签云/站点）
  - 列表 `/articles`：筛选状态 chips（分类/标签/keyword 可清除）+ 文章卡流 + 分页 + 共 N 篇 + 空态
  - 详情 `/articles/:idOrSlug`：约 760px 居中阅读列（去侧栏）+ 文章头（标题/元信息/标签）+ **完整正文排版体系** + 底部（标签/返回）
  - 分类 `/categories`：≤2 级分类网格卡（含计数）
  - 标签 `/tags`：articleCount 权重字号标签云
  - 关于 `/about`：site.about 居中阅读卡
- **封面图渲染（补历史欠账）**：`ArticleVO.coverFileId` → `GET /api/v1/public/files/{id}`（302 稳定公开 URL）；无封面/加载失败 → CSS 渐变占位（不破图）
- **正文排版体系（新增契约）**：`.markdown` 覆盖服务端渲染 HTML 元素全集（h1–h6/p/a/img/ul/ol/blockquote/pre/code/hr/table/strong/em），对齐 api-design §6.4 `content`=HTML 的注入消费
- **组件化**：ArticleCard / SideCard / Pager / FilterChips / TagCloud / Hero 等拆入 `src/components/`（CLAUDE.md §5.5 单一职责）
- **不变**：API 契约零改动（无新接口/字段/后端）；路由集合与 query 参数（`?category=&tag=&keyword=`）不变；只读边界、技术栈不变
- **无 BREAKING**：纯前端布局与样式层改造

### Non-goals（项目边界，对齐 `CLAUDE.md` §1 / §2.2）

- 不做评论/点赞/阅读量/访客统计（business-design §1.2 Non-goals）
- 不做登录/个人中心/管理入口（portal 只读公开 API）
- 不做上一篇/下一篇、相关推荐（无接口契约支撑，不虚构）
- 不做正文 TOC 浮动目录（可选增强，另议）；不做暗色模式/多语言（CLAUDE.md §2.2）
- **不改**视觉基线口径：装饰色一律经 `color-mix`/`opacity` 从既有 token 派生，**不新增色值**（portal-aurora-visual 约定 blog-portal 子集仅 `--bg-code` 扩展）；不套管理端壳
- 不动后端/DB/存储；不写 CLAUDE.md

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

> `openspec/specs/` 尚无已归档同名能力；基线为未归档变更 `blog-content-domain` 的 `specs/blog-portal/spec.md`。本变更以 **ADDED Requirements** 追加「页面布局与阅读体验」需求；既有「信息架构与只读边界 / 详情寻址 / 站点文案」需求**不变**（路由、只读边界、技术栈均维持原样）。按 `portal-aurora-visual` Purpose 划界：布局/阅读体验归内容域能力（即 `blog-portal`），视觉 token 层仍受该基线约束，本变更不触碰其口径。

- `blog-portal`（见 `blog-content-domain`）：ADDED 页面布局与阅读体验需求（六路由布局、正文排版体系、封面渲染、筛选/分页/空态、响应式）

## Impact

| 面 | 影响 |
|----|------|
| **API** | 零改动（继续消费 `/api/v1/public/**` 既有接口） |
| **前端** | `blog-portal` 六路由视图重排；新增 `src/components/`（ArticleCard/SideCard/Pager/FilterChips/TagCloud/Hero）；`styles/main.css` 扩展布局与 `.markdown` 排版体系；封面经 `/api/v1/public/files/{id}` |
| **后端/DB/存储** | 零改动 |
| **视觉基线** | 沿用 `portal-aurora-visual` token 子集；新装饰经 `color-mix`/`opacity` 从 token 派生，不新增硬编码色值（旧色值零命中口径不变） |
| **跨系统** | 无（仅 blog-portal，多系统独立边界不受影响） |
