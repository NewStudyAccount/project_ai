# Tasks: redesign-portal-pages

> 验收参照：`docs/blog-system/prototype-portal-redesign.html`（六路由面板 + 契约锚点）。完成后逐项勾选状态位（`CLAUDE.md` §8）。

## 1. 样式基建

- [x] 1.1 `styles/main.css` 扩展布局骨架（1120 双栏 / 760 阅读列 / 响应式断点 1200、768）；装饰色一律经 `color-mix`/`opacity` 从既有 token 派生，**不新增色值**（`--bg-code` 仍为唯一子集扩展）
- [x] 1.2 新增 `.markdown` 正文排版体系样式：h1–h6（h2 侧条）/p/a/img（限宽圆角）/ul/ol/blockquote（浅底侧条）/pre（深底）+code/hr/table（表头强调+斑马纹）/strong/em；正文行高 ≥1.8

## 2. 公共组件（`src/components/`，组合式 SFC，Props 向下 Events 向上）

- [x] 2.1 `ArticleCard.vue`：封面（含占位降级）/标题/摘要/元信息/标签/置顶徽标
- [x] 2.2 `SideCard.vue` + `CategoryList.vue` + `TagCloud.vue`：侧栏容器、分类计数列表、标签云（articleCount 权重）
- [x] 2.3 `Pager.vue` + `FilterChips.vue`：分页器（current/size）、筛选状态 chips（单项清除/全部清除）
- [x] 2.4 `Hero.vue` 首页头区；`App.vue` 壳收敛（60px 头部、五导航高亮、搜索跳转 `/articles?keyword=`、页脚 site.copyright）

## 3. 六路由改造（布局对齐原型各面板）

- [x] 3.1 `HomeView.vue`：hero + 置顶精选卡区（isTop=1，无置顶优雅收起）+ 最新列表 + 侧栏（分类/标签/站点）
- [x] 3.2 `ArticleListView.vue`：筛选 chips + 卡流 + 分页 + 共 N 篇 + 空态；筛选沿用 `?category=&tag=&keyword=`
- [x] 3.3 `ArticleDetailView.vue`：约 760px 阅读列（去侧栏）+ 文章头（title/meta/wordCount/tags）+ 正文注入 + 底部（标签/返回）
- [x] 3.4 `CategoryView.vue` / `TagView.vue`：分类网格卡（≤2 级内嵌 chips）/ 权重标签云
- [x] 3.5 `AboutView.vue` / `NotFoundView.vue`：关于阅读卡（site.about 降级空态）/ 空态样式统一

## 4. 封面渲染

- [x] 4.1 封面链路：`coverFileId>0` → `GET /api/v1/public/files/{id}` 缩略（圆角限宽）；`=0`/`onerror` → CSS 渐变占位；异步加载不阻塞文本渲染

## 5. 自检与回写

- [x] 5.1 `cd frontend/blog-portal && npm run lint && npm run type-check` 通过
- [x] 5.2 冒烟：六路由逐页对照原型面板（布局/排版/占位/空态/响应式无横向滚动）；详情页用含图片/表格/代码块的正文核对排版体系
- [x] 5.3 视觉纪律核对：`src/` 旧色值零命中（`portal-aurora-visual` 场景口径）；新增装饰均为 token 派生、无新色值常量
- [x] 5.4 回写实现偏差到 `docs/blog-system/`（原型 vs 实装差异如有）；提交说明按 `CLAUDE.md` §6.3（如 `feat(blog): 用户端页面按重设计原型改版`）
