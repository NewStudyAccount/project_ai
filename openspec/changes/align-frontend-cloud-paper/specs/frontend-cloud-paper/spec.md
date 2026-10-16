# frontend-cloud-paper · 多系统前端云白视觉基线

收敛 `user-admin` / `auth-admin` / `blog-admin` / `auth-portal` 的视觉实现，使管理端与登录门面符合 `docs/template/admin-ui-design.md`「云白」基线。不改变业务契约与页面布局模式。

## ADDED Requirements

### Requirement: Design Tokens 全局生效

每个前端工程 SHALL 在全局样式（`src/styles/main.css`）中定义并使用云白 Design Tokens，且 MUST NOT 保留与云白冲突的旧主色/旧底色体系。

#### Scenario: Token 取值与设计文档一致

- **WHEN** 打开任一 `{system}-admin` 或 `auth-portal` 的全局样式
- **THEN** `--bg-page` 为 `#f6f7f9`、`--bg-surface` 与 `--bg-sidebar` 为 `#ffffff`、`--primary` 为 `#0284c7`、`--border` 为 `#e4e4e7`
- **AND** 圆角变量区分卡片 `12px` 与主按钮 `999px`（胶囊）

#### Scenario: Element Plus 主色对齐

- **WHEN** 页面渲染任意 `el-button type="primary"` 或 `el-menu` 激活态
- **THEN** 主色呈现为 `#0284c7` 族（或文档规定的 hover/active 阶），MUST NOT 为 EP 默认蓝 `#409eff` 作为品牌主色

#### Scenario: 状态软胶囊样式

- **WHEN** 列表展示启用/停用/禁用类状态
- **THEN** 使用 `.status-pill` 软胶囊（启用 `#d1fae5`/`#065f46`，停用 `#fef3c7`/`#92400e`，禁用/失败 `#ffe4e6`/`#9f1239`），MUST NOT 用大面积色块行或无样式纯文字

### Requirement: App Shell 符合云白壳布局

各 `{system}-admin` 的应用壳 SHALL 为：侧栏固定 `240px` 纸白底 + 右缘 `1px` 细线；侧栏激活项左侧 `3px` 竖条 `#0284c7` + 底色 `#f0f9ff` + 文字 `#0c4a6e`；顶栏 `56px` 白底 + 下缘细线；主内容底色 `--bg-page`。

#### Scenario: 管理端壳尺寸与分层

- **WHEN** 进入 `user-admin` / `auth-admin` / `blog-admin` 任一已登录页面
- **THEN** 可观察到侧栏 240px、顶栏 56px、主内容浅灰底上的 `.page-card` 白面
- **AND** 侧栏为浅色体系，MUST NOT 深墨蓝/深色侧栏与浅内容混用

#### Scenario: 侧栏激活态

- **WHEN** 用户选中某个菜单项
- **THEN** 该项显示 `3px` 主色竖条与浅天蓝底，文字色为 `--text-sidebar-active`

### Requirement: 页面容器与控件质感统一

列表/树页的页面容器 `.page-card` SHALL 为白底、圆角 `12px`、轻阴影；工具栏主按钮 SHALL 为 `type="primary"` 且圆角胶囊；弹窗 SHALL 圆角 `12px` 并使用文档规定阴影。

#### Scenario: 列表页容器

- **WHEN** 打开用户/角色/菜单/审计等列表或树管理页
- **THEN** 主内容区使用 `.page-card`（圆角 12、margin/padding 按文档 16–20px 族）
- **AND** 主操作按钮为胶囊形 primary，行内危险操作为 danger 链接并保留 confirm 行为（行为不变，仅质感）

#### Scenario: 表单/授权弹窗

- **WHEN** 打开 D-Form / D-Panel 弹窗
- **THEN** 弹窗圆角 12px，底栏「取消」「保存」右对齐，主按钮为 primary 胶囊
- **AND** 不因换肤引入全页跳转表单或新布局模式

### Requirement: 登录门面与壳同族

`auth-portal` 登录页 SHALL 使用 `--bg-page` 页底与居中白卡片（宽 `380px`、圆角 `12px`、轻阴影），主按钮为 `--primary` 胶囊。

#### Scenario: 登录页外观

- **WHEN** 未登录访问受保护资源或直接打开 `auth-portal`
- **THEN** 可见居中白卡片登录门面，底色为云白 `--bg-page` 族
- **AND** 各 `*-admin` 内 MUST NOT 出现账密表单（仍 OIDC 跳转）

### Requirement: 无冲突装饰与半套混用

实现 SHALL 避免大面积渐变、毛玻璃、重阴影、第三方彩色图标包；同一系统内 MUST NOT 同时存在旧 token 与云白 token 两套主色。

#### Scenario: 提交前样式自检

- **WHEN** 对任一前端完成样式修改并执行 lint / type-check
- **THEN** 全局样式仅保留一套云白 token
- **AND** 壳与 page-card 均已更新（禁止只改 `:root` 不改壳）
