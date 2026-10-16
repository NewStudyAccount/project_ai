## Purpose

新建只读用户端 `frontend/blog-portal`：访客浏览首页、文章列表/详情、分类导航、标签云与关于页，无管理入口、无登录能力。

## ADDED Requirements

### Requirement: 信息架构与只读边界
`blog-portal` SHALL 提供路由：`/`（置顶+最新）、`/articles`（`?category=&tag=&keyword=`）、`/articles/:idOrSlug`（详情 Markdown 渲染）、`/categories`、`/tags`，及关于页（文案来自站点配置）；仅调用 `/api/v1/public/**`；**无**管理入口、登录、个人中心；技术栈 Vue3 + Vite + TypeScript + Pinia + Element Plus（或不引平行 UI 库的轻量样式）。

#### Scenario: 访客浏览已发布文
- **WHEN** 访客从列表进入详情
- **THEN** 展示标题/摘要/封面/正文/标签，不出现草稿或下架文

#### Scenario: 分类与标签导航
- **WHEN** 访客按分类或标签筛选
- **THEN** 列表仅含对应已发布文章

### Requirement: 详情寻址
详情路由 SHALL 接受 `id` 或 `slug`（`idOrSlug`）：与公开详情接口寻址一致；无效或未发布展示「不存在」态，不暴露草稿。

#### Scenario: slug 友好链接
- **WHEN** 访客打开 `/articles/{slug}`
- **THEN** 渲染对应已发布文章

### Requirement: 站点文案
用户端 SHALL 从 `GET /api/v1/public/site-config` 取站名/描述/版权/关于，用于页头、页脚与关于页；缺失时优雅降级为空文案，不阻塞阅读。

#### Scenario: 关于页
- **WHEN** 访客打开关于页
- **THEN** 展示 `site.about` 等配置文案
