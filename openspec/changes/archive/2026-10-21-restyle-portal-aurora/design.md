# Design · restyle-portal-aurora

## Context

- `auth-portal`：单页登录门面（Login.vue 99 行 + main.css 60 行），云白 token 子集 + 居中白卡片（`admin-ui-design.md` §4.5 布局已定稿，仅视觉值旧）。
- `blog-portal`：公开博客站（App.vue + 7 个 view），独立极简样式（main.css 39 行），全部 EP 默认蓝/灰硬编码色值，无 token 体系；布局为顶栏 + 960px 居中容器 + 卡片流。
- 参照：`docs/template/tokens-aurora.css`（取值唯一来源）、`admin-ui-design.md` §1/§4.5、样张 `admin-ui-style-aurora.html`。
- 已归档 `restyle-admin-aurora` 确立的纪律：色值只出自 token 体系、渐变只出自 `--accent-gradient`/`--stat-grad-*`、每端独立提交、纯视觉层零行为变化。

## Goals / Non-Goals

**Goals:**

- auth-portal 全量换装到极光 token（§4.5 口径：渐变主按钮胶囊、白卡片 380px、`--bg-page` 页底）
- blog-portal 品牌色轻量对齐：主色/文字/底色/圆角/阴影切极光体系，硬编码色值全部 token 化
- 两门户与三管理端同一品牌语言，无视觉割裂

**Non-Goals:**

- 不给 blog-portal 套管理端壳/侧栏
- 不改布局/组件结构/请求/交互
- 不改后端 / 管理端 / 制品
- 不做暗色主题 / 移动端

## Decisions

**D1 · 两档口径：auth-portal 全量、blog-portal 轻量**
auth-portal 是 `admin-ui-design.md` §4.5 明文管辖的登录门面，按管理端同族 token 全量换装；blog-portal 是公开博客（设计文档范围外），只做**品牌色对齐**（主色/文字/底/圆角/阴影），不引入管理端的壳/渐变侧栏/胶囊按钮语言。备选：blog-portal 也全量套管理端组件语言——否决：公开阅读场景不需要后台仪表盘气质。

**D2 · token 子集同构复制，取值单一来源**
两门户各在 `main.css` `:root` 落一份 token 子集（blog-portal 只需 ~12 枚；auth-portal 对齐 §4.5 用到的子集），取值逐字取自 `tokens-aurora.css`；EP 主题映射（`--el-color-primary` 系列）auth-portal 必须带、blog-portal 未用 EP 组件则不加。备选：@import 公共 token 文件——否决：跨包引用路径脆弱，沿用三管理端「逐字复制 + 注释权威出处」的既定做法。

**D3 · blog-portal 全部硬编码色值 token 化**
`App.vue`/7 view 中的 `#409eff`/`#ecf5ff`/`#303133`/`#606266`/`#909399`/`#ebeef5`/`#f2f2f2`/`#f5f7fa`/`#fff` 逐一映射 token（映射表见 tasks）；`#ecf5ff` 标签浅蓝底 → `--primary-light`（#eeecff）、`#409eff` → `--primary`。代码块底 `#f5f7fa` → `--bg-code`（新增 1 枚子集 token，取 `#f2f1fb` 系浅紫灰，保持 markdown 可读性）。

**D4 · 纯视觉层零行为变化**
只动 `<style>` 块与 main.css；模板结构、script、路由、请求零改动。Login.vue 仅一处 `#909399` → `var(--text-secondary)`。

## Risks / Trade-offs

- [blog-portal 明文 hex 与设计文档无对应条款] → D1 明确轻量口径只出自 `tokens-aurora.css` 取值，不新增设计方言；`--bg-code` 为唯一子集扩展（本变更内定义并注释）
- [markdown 排版可读性受底色变化影响] → 代码块/引用底色只做同明度微调（浅灰 → 浅紫灰），文字对比度不降
- [两门户 token 子集漂移] → 注释统一「权威：tokens-aurora.css」；值不允许本地微调

## Migration Plan

1. auth-portal：main.css token 换值 + 登录卡片/主按钮样式 + Login.vue 一处色值 → 独立提交
2. blog-portal：main.css token 子集 → App.vue → 7 个 view 色值 token 化 → 独立提交
3. `npm run lint && npm run type-check` 两门户通过
4. 回滚：按门户提交单 revert；无后端兼容性问题

## Open Questions

- （无阻塞）blog-portal 是否后续需要品牌化 header/logo——超出本变更，另行排期
