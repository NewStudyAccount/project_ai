# Tasks: article-detail-rail

> 验收参照：`docs/blog-system/prototype-portal-redesign.html` 详情面板右轨。完成后逐项勾选状态位（`CLAUDE.md` §8）。

## 1. 组件

- [x] 1.1 `src/components/ArticleToc.vue`：扫注入 HTML 的 h2/h3 生成目录（层级缩进）、缺 id 补唯一锚点、点击 `scrollIntoView` 平滑跳转（`scroll-margin-top` 避开固定头部）；无标题 `v-if` 收起
- [x] 1.2 `src/components/DetailRail.vue`：三块 sticky 卡（分类/标签、目录、返回列表）；无分类且无标签时①块隐藏

## 2. 详情页布局

- [x] 2.1 `ArticleDetailView.vue`：改双列布局（约 1120 容器 = 阅读列 + 240 右轨），右轨 sticky；<1200px 右轨隐藏、单列无横向滚动；文章头/正文注入/底部不变
- [x] 2.2 目录扫描与代码块角标同用注入后 `nextTick` 时机；不引入滚动监听

## 3. 自检与回写

- [x] 3.1 `cd frontend/blog-portal && npm run lint && npm run type-check` 通过
- [x] 3.2 冒烟：含 h2/h3 的真实文章（如 slug=1）目录生成、点击跳转、锚点不与注入 HTML 冲突；无标题文章目录收起；<1200px 无横向滚动
- [x] 3.3 视觉纪律核对：新增样式色值均为 token/token 派生（`portal-aurora-visual` 口径）；提交说明按 `CLAUDE.md` §6.3（如 `feat(blog): 详情页右侧悬浮轨与目录跳转`）

## 4. 评审修订（用户评审意见：去冗余返回入口 + 悬浮跟随）

- [x] 4.1 移除详情页底部「返回文章列表」入口（原型 + 实现）：返回入口唯一归右轨；specs/design 已同步
- [x] 4.2 修复悬浮跟随：原型 `.stage` 的 `overflow: hidden` 改 `overflow: clip`（解除 sticky 抑制）；实现侧右轨加 `max-height` + `overflow-y: auto`，双列断点放宽至 1024（<1024 右轨隐藏）
