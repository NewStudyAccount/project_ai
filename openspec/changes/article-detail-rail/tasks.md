# Tasks: article-detail-rail

> 验收参照：`docs/blog-system/prototype-portal-redesign.html` 详情面板右轨。完成后逐项勾选状态位（`CLAUDE.md` §8）。

## 1. 组件

- [ ] 1.1 `src/components/ArticleToc.vue`：扫注入 HTML 的 h2/h3 生成目录（层级缩进）、缺 id 补唯一锚点、点击 `scrollIntoView` 平滑跳转（`scroll-margin-top` 避开固定头部）；无标题 `v-if` 收起
- [ ] 1.2 `src/components/DetailRail.vue`：三块 sticky 卡（分类/标签、目录、返回列表）；无分类且无标签时①块隐藏

## 2. 详情页布局

- [ ] 2.1 `ArticleDetailView.vue`：改双列布局（约 1120 容器 = 阅读列 + 240 右轨），右轨 sticky；<1200px 右轨隐藏、单列无横向滚动；文章头/正文注入/底部不变
- [ ] 2.2 目录扫描与代码块角标同用注入后 `nextTick` 时机；不引入滚动监听

## 3. 自检与回写

- [ ] 3.1 `cd frontend/blog-portal && npm run lint && npm run type-check` 通过
- [ ] 3.2 冒烟：含 h2/h3 的真实文章（如 slug=1）目录生成、点击跳转、锚点不与注入 HTML 冲突；无标题文章目录收起；<1200px 无横向滚动
- [ ] 3.3 视觉纪律核对：新增样式色值均为 token/token 派生（`portal-aurora-visual` 口径）；提交说明按 `CLAUDE.md` §6.3（如 `feat(blog): 详情页右侧悬浮轨与目录跳转`）
