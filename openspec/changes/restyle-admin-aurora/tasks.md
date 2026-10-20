# Tasks · restyle-admin-aurora

## 1. 前置核对（设计文档已先行更新）

- [x] 1.1 三方一致性核对：`docs/template/tokens-aurora.css` ↔ `admin-ui-design.md` §1 ↔ 样张 `admin-ui-style-aurora.html` 的 token 名/值逐项一致（不一致以 `admin-ui-design.md` §1 为准并修正另两处）
- [x] 1.2 跨文档引用核对：`generic-admin-page-design.md` / `docs/blog-system/README.md` / `docs/object-storage-design/*` 已切「极光 Aurora」命名；`tokens-cloud-paper.css` 已标废

## 2. blog-admin 换装

- [x] 2.1 `frontend/blog-admin/src/styles/main.css`：`:root` 切换 `tokens-aurora.css` 全量取值（16 改值 + 8 新增 + 状态胶囊 5 组配对 + `--el-color-primary` 系列）
- [x] 2.2 `frontend/blog-admin/src/components/AppLayout.vue`：侧栏深靛紫渐变 + 顶部光晕 + 玻璃激活 + 渐变竖条 + `el-menu` 深色模式 + 顶栏白面细线
- [x] 2.3 组件视觉对齐：渐变主按钮 + 光晕、卡片 hover 抬升、软胶囊配对、分页当前页渐变强调、统计卡渐变（接口可得才渲染，降级语义不变）
- [x] 2.4 旧色值清底：`src/` 内 grep 云白残值（`#0284c7` `#0369a1` `#38a2d8` `#6bb8e2` `#9fd0ec` `#b9def2` `#e6f3fa` `#026ea3` `#f0f9ff` `#0c4a6e` `#f6f7f9` `#e4e4e7` `#18181b` `#3f3f46` `#71717a`）零命中（注释引述除外），命中处 token 化
- [x] 2.5 `cd frontend/blog-admin && npm run lint && npm run type-check` 通过；独立提交 `feat(blog-admin): 切换极光 Aurora 视觉基线`

## 3. user-admin 换装

- [x] 3.1 `frontend/user-admin/src/styles/main.css`：同 2.1
- [x] 3.2 `frontend/user-admin/src/components/AppLayout.vue`：同 2.2
- [x] 3.3 组件视觉对齐：同 2.3
- [x] 3.4 旧色值清底：同 2.4
- [x] 3.5 `cd frontend/user-admin && npm run lint && npm run type-check` 通过；独立提交 `feat(user-admin): 切换极光 Aurora 视觉基线`

## 4. auth-admin 换装

- [x] 4.1 `frontend/auth-admin/src/styles/main.css`：同 2.1
- [x] 4.2 `frontend/auth-admin/src/components/AppLayout.vue`：同 2.2
- [x] 4.3 组件视觉对齐：同 2.3
- [x] 4.4 旧色值清底：同 2.4
- [x] 4.5 `cd frontend/auth-admin && npm run lint && npm run type-check` 通过；独立提交 `feat(auth-admin): 切换极光 Aurora 视觉基线`

## 5. 自检

- [x] 5.1 三端一致性走查：token 表逐字一致、壳样式逐字一致（系统名/菜单除外）、骨架三件（CrudPage/CrudForm/DetailDialog）无结构性 diff（色值 token 化除外）
- [x] 5.2 行为零变化对照：布局/交互/请求/权限判定与换装前一致；统计卡降级语义保持；`hasPermission` 整钮不渲染语义不变
- [x] 5.3 渐变纪律走查：`--accent-gradient`/`--stat-grad-*` 之外无渐变；毛玻璃仅侧栏激活项；无过重投影/第三方图标包/彩色 emoji
- [x] 5.4 提交自查：三笔独立提交、改动仅限三端 `src/styles/main.css`、`AppLayout.vue` 及清底命中的视图样式文件；不碰后端/门户/骨架结构/其他变更制品
