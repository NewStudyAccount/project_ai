# Proposal · align-frontend-cloud-paper

## Why

`admin-ui-design.md` 已定稿「云白」（全浅轻边界 · `#0284c7` · 胶囊/12px 圆角），但四个前端工程（`user-admin` / `auth-admin` / `blog-admin` / `auth-portal`）仍停留在 Element Plus 默认色、旧圆角与不完整 token，壳与页面存在半套混用。需要按 SDD 将云白视觉基线一次性对齐到所有前端，避免各系统继续漂移出视觉方言。

## What Changes

- 将 `docs/template/admin-ui-design.md`「云白」Design Tokens（色板、字体、间距、圆角、阴影）落入各 `{system}-admin` 与 `auth-portal` 的 `styles` 与壳组件，覆盖 Element Plus 主色
- 统一 App Shell：侧栏 240px 纸白 + 右缘细线 + 浅天蓝激活；顶栏 56px；主内容极浅灰 + `.page-card`（圆角 12、轻阴影）
- 统一页面骨架视觉（不改布局模式契约）：P-List / P-Tree / D-Form / D-Panel 使用云白圆角、胶囊主按钮、软胶囊状态
- 统一登录门面 `auth-portal`：页底 `--bg-page`、居中白卡片 380px / 圆角 12 / 胶囊主按钮
- 清理与云白冲突的旧 token 与半套样式（EP 默认蓝、旧 `#f5f7fa/#ebeef5` 体系、不一致圆角）
- **不做**：业务功能、路由/权限契约、暗色主题、移动端重排、新页面模式、独立视觉稿交付物

## Capabilities

### New Capabilities

- `frontend-cloud-paper`: 多系统前端（user-admin / auth-admin / blog-admin / auth-portal）云白视觉基线落地：Design Tokens、App Shell、页面容器与组件质感、登录门面外观

### Modified Capabilities

（无：本变更只收敛视觉实现与 token 对齐，不改变 `user-rbac` / `auth-oidc-login` 等业务/交互需求。`user-admin-ui` / `auth-admin-ui` 中关于旧色值与旧壳尺寸的表述以 `admin-ui-design.md` 与本变更为准，实施时对齐，不在本变更重写其权限/布局契约。）

## Impact

- **前端（样式层）**
  - `frontend/user-admin`：`src/styles/main.css`、`src/components/AppLayout.vue`、各 views 中与 token/圆角/按钮相关的局部样式
  - `frontend/auth-admin`：同上
  - `frontend/blog-admin`：同上
  - `frontend/auth-portal`：`src/styles/main.css`、`src/views/Login.vue`（或 `LoginCard.vue`）登录壳外观
- **文档**
  - 权威基线：`docs/template/admin-ui-design.md`（已定稿云白）
  - Token 参考：`docs/template/tokens-cloud-paper.css`
  - 历史比选样张 `admin-ui-style-*.html` 仅对照，不改业务
- **边界**
  - 不改后端、不改 API/DTO、不改权限标识与菜单下发语义
  - 跨系统禁止共用业务组件；样式 token 以「文档 + 各工程本地复制/合并」方式对齐，不引入跨系统 npm 私有包（保持多系统独立构建）
  - 与 `align-user-rbac-ui` / `align-auth-rbac-ui` 并行时：布局骨架（P-List 等）仍归那些变更；**色板与质感以本变更 / `admin-ui-design.md` 为准**，避免双真相

## Non-goals

- 不做暗色主题、移动端适配、国际化强制交付
- 不引入新 UI 框架、第三方图标包、渐变/毛玻璃装饰
- 不顺手重构业务组件结构、不改接口契约
- 不在本变更内实施（实施走 `/opsx:apply`，按 `tasks.md` 执行）
