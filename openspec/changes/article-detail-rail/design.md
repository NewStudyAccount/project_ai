# Design: article-detail-rail

> 需求见 `specs/`；动机见 `proposal.md`；视觉参照 `docs/blog-system/prototype-portal-redesign.html` 详情面板右轨。

## Context

详情页现状（`redesign-portal-pages` 落码后）：808px 容器内约 760px 阅读列，文章头 + 注入服务端渲染 HTML（`.markdown` 排版体系）+ 文末标签/返回。两侧留白闲置；正文 h2/h3 标题无导航。渲染 HTML 由 flexmark 产出（标题无 id 属性），前端只注入（既有需求「MUST NOT 自带 Markdown 转换」仍有效——扫 DOM 补锚点属呈现增强，不解析 Markdown）。

## Goals / Non-Goals

**Goals:**

- 详情页右侧三块悬浮轨：分类/标签、目录（点击跳转）、返回列表
- 目录由注入 HTML 的 h2/h3 生成，层级可辨、点击平滑滚动
- 阅读体验不降级：阅读列宽度与排版不变，窄屏不破版

**Non-Goals:**

- 不做滚动高亮监听、目录折叠动画、进度条
- 不改服务端渲染输出（锚点由前端补）；不动其他页面；不动 API

## Decisions

1. **布局**：详情页容器扩为约 1120px 双列 `grid-template-columns: minmax(0,1fr) 240px`，阅读列维持约 760px；右轨 `position: sticky; top`（避开 60px 固定头部）+ `max-height: calc(100vh - 110px)`（轨内滚动，短视口也能整轨跟随）；**<1024px 隐藏右轨**（面包屑、文末标签与顶部固定导航兜底；返回入口唯一归右轨，详情底部不重复——评审裁决），保证窄屏无横向滚动。
   - 备选：窄屏右轨下沉文末——与文末标签重复、噪音大，否决。
   - **sticky 杀手约束**：悬浮轨祖先链上 MUST NOT 出现 `overflow: hidden/auto/scroll`（会吞掉滚动容器导致 sticky 失效；原型 `.stage` 曾因 `overflow: hidden` 复现此坑，已改 `overflow: clip`）。
2. **目录生成（`ArticleToc.vue`）**：注入渲染 HTML 后扫 `.markdown` 的 **h2/h3**（两级，对齐正文标题层级使用习惯）；标题缺 id 时补 `sec-{n}-{slug}` 锚点（slug 取文本安全化 + 序号防重）；条目点击 `scrollIntoView({behavior:'smooth'})`；正文无 h2/h3 时整块 `v-if` 收起。
   - 备选：服务端渲染时输出 id（改 flexmark 配置/后端）——动 `article-content-server-render` 契约面，收益低，否决。
3. **三块内容（`DetailRail.vue`）**：①分类（`categoryId`→`/articles?category=`，无则仅文字）+ 标签 chips（`tag.id`→`/articles?tag=`）；②`<ArticleToc>`；③返回列表（`to="/articles"`，**全页唯一返回入口**，详情底部不再放返回钮——评审裁决）。无分类且无标签时①块优雅隐藏（不占空壳）。
4. **组件拆分**：`DetailRail.vue`（三块容器）+ `ArticleToc.vue`（目录扫描/锚点/跳转），组合式 SFC、Props 向下（`ArticleDetailVO` 传必要字段）；样式 scoped，目录锚点滚动偏移用 `scroll-margin-top`（避开固定头部）。
5. **性能/时机**：目录扫描在 `content` 注入后 `nextTick` 一次完成（复用现有 `decorateCodeBlocks` 同一时机）；无轮询、无全局监听（无 scroll-spy，红线 §7.3）。

## Risks / Trade-offs

- [标题无 id 时前端补锚点与未来服务端锚点冲突] → 前端仅在缺 id 时补；服务端将来输出 id 则自动让位
- [sticky 轨在短文时高度不足] → 轨跟内容高度，短文时三块自然紧凑，无空洞
- [h2/h3 判定与用户预期（含 h4?）] → 两级目录已可辨层级；h4 过深易成噪音，如需再开增量
- [无滚动高亮] → 已裁决本期不做（Non-goal），后续可增量

## Migration Plan

1. 纯前端改动（详情页 + 2 新组件 + 样式），无后端/DB
2. 门禁：`cd frontend/blog-portal && npm run lint && npm run type-check`
3. 冒烟：含 h2/h3 多级标题的真实文章（slug=1 含 h1–h4）目录生成与跳转；无标题文章轨块收起；<1200px 无横向滚动
4. 回滚：revert 前端提交

## Open Questions

- 目录是否后续加滚动高亮（IntersectionObserver）——本期不做
- h4 是否纳入目录层级——本期 h2/h3 两级
