# Tasks · restyle-portal-aurora

## 1. auth-portal 换装（§4.5 全量口径）

- [x] 1.1 `frontend/auth-portal/src/styles/main.css`：token 子集切换极光取值（`--bg-page #f3f5fb`、`--primary #6d5df6`/`--primary-hover #5a49e8`、文字 `#1c1740`/`#3f3a6b`/`#8580ad`、`--border #e6e4f4`、`--danger #f43f5e`、圆角 16/999、紫调 `--shadow-card`、`--accent-gradient` + `--primary-glow`、`--el-color-primary` 系列），权威注释切「极光 Aurora」
- [x] 1.2 登录卡片与主按钮：卡片圆角 16 + 紫调阴影；主按钮渐变胶囊 + 光晕（hover/active/disabled 态齐全，disabled 降饱和不发光）
- [x] 1.3 `src/views/Login.vue`：硬编码 `#909399` → `var(--text-secondary)`；其余模板/script 零改动
- [x] 1.4 旧云白色值清底（`#0284c7` `#0369a1` `#f6f7f9` `#e4e4e7` `#18181b` `#3f3f46` `#71717a` `#e11d48` `#909399`）grep 零命中；`cd frontend/auth-portal && npm run lint && npm run type-check` 通过；独立提交 `feat(auth-portal): 切换极光 Aurora 品牌视觉`

## 2. blog-portal 品牌色轻量对齐（不套管理端壳）

- [x] 2.1 `frontend/blog-portal/src/styles/main.css`：引入极光 token 子集（`--bg-page` `--bg-surface` `--bg-code #f2f1fb`（唯一子集扩展）`--text-primary/regular/secondary` `--border` `--primary` `--primary-light` `--shadow-card` `--page-radius 16px`），权威注释；body/link/card/markdown 代码块样式改走 token
- [x] 2.2 `src/App.vue`：`#fff` `#ebeef5` `#303133` `#909399` → token；布局结构零改动
- [x] 2.3 7 个 view 色值 token 化（映射：`#409eff`→`--primary`、`#ecf5ff`→`--primary-light`、`#303133`→`--text-primary`、`#606266`→`--text-regular`、`#909399`→`--text-secondary`、`#ebeef5`→`--border`、`#f2f2f2`→`--border`（分隔线）或 `--bg-code`、`#f5f7fa`→`--bg-code`、`#fff`→`--bg-surface`）
- [x] 2.4 旧值清底：`#409eff` `#ecf5ff` `#303133` `#606266` `#909399` `#ebeef5` `#f2f2f2` `#f5f7fa` 在 `src/` 内 grep 零命中（注释引述除外）
- [x] 2.5 `cd frontend/blog-portal && npm run lint && npm run type-check` 通过；独立提交 `feat(blog-portal): 品牌色对齐极光 Aurora`

## 3. 自检

- [x] 3.1 取值一致性：两门户 token 子集与 `docs/template/tokens-aurora.css` 同名变量逐值一致（`--bg-code` 除外，注释标注子集扩展）
- [x] 3.2 行为零变化：模板结构/script/路由/请求零 diff（仅 `<style>` 块与 main.css）
- [x] 3.3 口径纪律：blog-portal 无管理端壳元素（侧栏/顶栏/胶囊主按钮/状态软胶囊）；渐变仅 `--accent-gradient`（auth-portal 主按钮）
- [x] 3.4 提交自查：两笔独立提交、改动仅限两门户前端、不碰管理端/后端/其他变更制品
