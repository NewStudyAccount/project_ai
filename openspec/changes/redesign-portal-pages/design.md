# Design: redesign-portal-pages

> 需求见 `specs/`；动机见 `proposal.md`；视觉验收参照 `docs/blog-system/prototype-portal-redesign.html`。
> 契约口径以 `CLAUDE.md` 为准；接口字段以 `docs/blog-system/api-design.md` 为准；视觉 token 以 `docs/template/tokens-aurora.css` 为准。

## Context

`blog-portal` 现状：`App.vue` 56px 白条导航 + 960px 单列卡片流；`HomeView` 置顶为文字列表；`ArticleListView` 有 keyword 输入与本地分页但无筛选状态可视；`ArticleDetailView` 注入服务端渲染 HTML（`article-content-server-render` 已落）但仅 3 条 `.markdown` 样式；`CategoryView`/`TagView`/`AboutView` 为素列表；封面图从未渲染。视觉层已按 `portal-aurora-visual` 完成 token 化（子集仅 `--bg-code` 扩展），该基线 Purpose 明确「布局/阅读体验归内容域能力」——本变更正是行使这条边界内的布局改造。

约束：API 契约零改动（`/api/v1/public/**` 既有六接口）；路由与 query 参数不变；只读边界不变；CLAUDE.md §5.5 前端结构约定（组件拆分、组合式 API、Pinia、Element Plus）。

## Goals / Non-Goals

**Goals:**

- 六路由布局按原型重排，详情页达到「好读」的阅读体验（760px 阅读列 + 完整正文排版体系）
- 封面图渲染补齐（coverFileId → `/public/files/{id}`，含降级占位）
- 筛选/分页/空态等列表信息可视化
- 组件化落地（可复用卡/侧栏/分页/chips），布局样式 token 派生纪律化

**Non-Goals:**

- 不动 API/后端/DB/存储；不虚构无契约功能（上下篇/相关推荐/TOC）
- 不做暗色模式、多语言、评论/点赞/阅读量
- 不改视觉 token 口径、不套管理端壳
- 分页参数不入 URL（本期沿用现状本地态；URL 可分享列为开放问题）

## Decisions

1. **布局骨架**：home/list 用 1120px 双栏（主内容 + 292px 侧栏）；detail/about 用 808px 容器内 760px 阅读列（去侧栏，去噪优先）。响应式断点 1200px（侧栏下沉主列下方）/ 768px（文章卡纵排、hero 收敛、导航换行）。
   - 备选：详情保留侧栏——长文阅读视宽被挤，噪音大于导航收益，否决。
2. **封面渲染链路**：`coverFileId>0` → `<img src="/api/v1/public/files/{id}">`（后端 302 稳定公开 URL）；`coverFileId=0` 或 `onerror` → CSS 渐变占位。图片加载异步，**不得**阻塞标题/摘要文本渲染（列表先文本后图）。
   - 备选：预签名 URL 客户端拼装——接口契约无此出参，否决（不虚构契约）。
3. **token 纪律（对齐 portal-aurora-visual）**：装饰渐变/光晕一律经 `color-mix(in srgb, var(--primary) X%, transparent)` 或 `opacity` 从既有 token 派生；**不新增色值常量**；`--bg-code` 仍为唯一子集扩展。旧色值（`#409eff` 等清单）保持零命中。
   - 备选：新增 `--hero-glow` 等子集 token——违反「子集仅 `--bg-code` 扩展」基线约定，若确需新增须先改 `portal-aurora-visual`，本期不需要，否决。
4. **正文排版体系 `.markdown`**：元素全集对齐服务端渲染（flexmark）输出结构——h1–h6/p/a/img/ul/ol/blockquote/pre/code/hr/table/strong/em。要点：行高 ≥1.8；h2 侧条强调；img 限宽+圆角；blockquote 浅底+侧条；`pre` 深底（取 `--text-primary` 系深色，token 派生）+ 右上语言角标（从 `<code class="language-*">` 提取）；table 表头浅底+斑马纹。
   - 备选：引入 prose 类库（如 tailwind typography）——平行依赖，否决。
5. **组件拆分（CLAUDE.md §5.5）**：`src/components/` 落 ArticleCard（封面/标题/摘要/元信息/标签/置顶徽标）、SideCard、CategoryList、TagCloud、Pager、FilterChips、Hero；`<script setup lang="ts">` 组合式、Props 向下 Events 向上；跨页复用状态无（各页自管），不引 Pinia 新 store（现有未用则不强加）。
   - 备选：样式内联在各 View——重复度高、不可维护，否决。
6. **筛选/分页状态**：筛选沿用 `?category=&tag=&keyword=` 既有 query 参数（与 `api-design §6.3` 对齐），chips 清除即改 query 刷新；分页 `current/size` 维持组件本地态（与现状一致，避免本期扩行为）；「共 N 篇」取 `PageResult.total`。
7. **UI 组件口径**：Element Plus 保留作输入等基础控件；卡片/chips/分页/hero 用项目样式实现（不引平行 UI 库，对齐 CLAUDE.md §2）。

## Risks / Trade-offs

- [实装与原型视觉偏差] → 原型为验收参照，逐页面板对照（tasks 5.2）；偏差回写 `docs/blog-system/`
- [封面 302 在离线/异网环境不可达] → `onerror` 渐变占位降级，布局不破
- [`color-mix` 浏览器兼容] → 目标为现代常青浏览器（内部系统）；如需兜底用同 token 的 `rgba` 派生写法
- [新组件引入改动面扩大] → 严格按 tasks 清单落地，不顺手重构无关视图（CLAUDE.md §7.8）
- [响应式细节（断点内布局）验收主观] → 以「无横向滚动、可读、组件不破」为底线场景（specs 已列）

## Migration Plan

1. 纯前端改动：`blog-portal` 样式/组件/视图；无 DB、无后端、无配置变更
2. 门禁：`cd frontend/blog-portal && npm run lint && npm run type-check`（CLAUDE.md §3.2）
3. 冒烟：六路由逐页对照原型；详情页用含图片/表格/代码块的真实文章（slug=1 起）核对排版
4. 回滚：revert 前端提交即可（无状态迁移）

## Open Questions

- 分页 `current` 是否入 URL query（可分享/可回退）——本期维持本地态，待后续裁决
- 正文 TOC 浮动目录（注入后扫 h2/h3 可实现）——可选增强，是否立项待裁决
- 详情页阅读列宽度微调（740–800px 区间）以实装字体渲染效果定
