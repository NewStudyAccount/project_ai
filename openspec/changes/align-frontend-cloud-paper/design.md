# Design · align-frontend-cloud-paper

## Context

多系统前端容器 `frontend/{user-admin,auth-admin,blog-admin,auth-portal}` 已能跑通登录与 RBAC 页面，但样式层仍是「EP 默认色 + 各工程手写局部 CSS」。`docs/template/admin-ui-design.md` 已裁决「云白」，并提供 `tokens-cloud-paper.css`。本变更只做视觉基线落地，不碰业务契约。

约束：

- 多系统独立构建（`CLAUDE.md` §4）：不引入跨系统共享 npm 样式包，token 以「文档为源 + 各工程落地副本」对齐
- 组件库固定 Element Plus（`CLAUDE.md` §5.5）；只覆盖变量与壳/容器类，不自建 UI 框架
- 纯网关鉴权、菜单下发等契约不因换肤改变

## Goals / Non-Goals

**Goals:**

- 四个前端外观一致收敛到云白 token（色、字、距、圆角、阴影）
- App Shell / page-card / 按钮 / 状态胶囊与 `admin-ui-design.md` §1–§5 一致
- Element Plus 主色 `#0284c7` 全局生效，消除半套混用
- 可回滚：仅样式文件与模板 class，无行为分支

**Non-Goals:**

- 暗色主题、移动端重排、新页面模式
- 业务组件重构、路由/权限逻辑修改
- 后端与 API 变更
- 跨系统抽取 monorepo UI 包

## Decisions

### D1. Token 落点：每工程 `main.css` 为源，不抽共享包

- **选择：** 将 `tokens-cloud-paper.css` 的 `:root` 与基础类合并进各工程 `src/styles/main.css`（允许少量系统名差异，如登录页布局类）。
- **理由：** `CLAUDE.md` 要求系统可独立构建部署；抽 npm 包增加发布与版本耦合，收益仅是去重 60 行 CSS。
- **备选：** 共享 `frontend/packages/ui-tokens`——否决（边界与构建复杂度）。

### D2. 壳尺寸与质感以 `admin-ui-design.md` 为准

- 侧栏 240px 纸白 + `1px` 右缘；激活浅天蓝 `#f0f9ff` + 左侧 3px `#0284c7` 竖条。
- 顶栏 56px；主内容 `#f6f7f9` + `.page-card` 圆角 12、轻阴影。
- 旧值（220px / 60px / `#f5f7fa` / 圆角 8 无阴影）在 `user-admin-ui` / `auth-admin-ui` 历史表述中出现过：**实施时以设计文档为准**，本变更不回改那些 change 的业务条款，只在 UI 样式实现上收敛。

### D3. Element Plus 变量覆盖，而非逐组件改 class

- 在 `main.css` 覆盖 `--el-color-primary`（含 light/dark 阶）与圆角相关变量；`.page-card` / `.toolbar` / `.status-pill` 为项目类。
- **理由：** 少侵入、易回滚；EP 升级时变量名稳定。
- **备选：** SCSS 主题源码编译——否决（构建复杂，Vite + EP CSS 变量已够）。

### D4. 登录门面独立但同族

- `auth-portal` 页底用 `--bg-page`，卡片 380px / 12px 圆角 / 轻阴影；主按钮胶囊。
- 管理端 `*-admin` 仍禁止账密表单（`rbac-design.md` §10.11），本变更只改外观。

### D5. 实施顺序：先壳后页

1. tokens + `main.css`  
2. `AppLayout.vue`（壳）  
3. 列表/树页局部样式抽查（按钮、状态）  
4. `auth-portal` 登录壳  
5. lint / type-check 与肉眼走查对照样张 `admin-ui-style-final4.html#s-b`

## Risks / Trade-offs

- [四处复制 token 可能漂移] → 以 `admin-ui-design.md` / `tokens-cloud-paper.css` 为唯一源；提交说明与自检清单强制对照；后续若引入共享包再单开变更
- [EP 升级改变默认圆角] → 在 `main.css` 显式写死项目圆角，不依赖默认
- [与 `align-user-rbac-ui` / `align-auth-rbac-ui` 并行冲突] → 色值冲突时以本变更为准；布局结构冲突时停下征询，不盲改
- [半套混用] → tasks 中设「逐系统自检」：禁止只改 tokens 不改壳

## Migration Plan

- 纯前端样式，按系统依次提交；单系统可独立回滚 `styles` 与 `AppLayout` 样式段。
- 无需数据迁移；发布走既有 Jenkins 前端流水线。

## Open Questions

- 无（主色与壳尺寸已由 `admin-ui-design.md` 裁决）。
