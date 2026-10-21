# blog-portal · Delta（article-content-server-render）

> 基线：`openspec/changes/blog-content-domain/specs/blog-portal/spec.md`（未归档）。  
> 本 delta **仅修订「信息架构与只读边界」需求**：详情页由「Markdown 渲染」改为「注入服务端渲染 HTML」并移除自研渲染器；路由、只读边界、站点文案等需求不变。

## MODIFIED Requirements

### Requirement: 信息架构与只读边界
`blog-portal` SHALL 提供路由：`/`（置顶+最新）、`/articles`（`?category=&tag=&keyword=`）、`/articles/:idOrSlug`（详情：**注入服务端渲染 HTML**，MUST NOT 自带 Markdown→HTML 转换）、`/categories`、`/tags`，及关于页（文案来自站点配置）；仅调用 `/api/v1/public/**`；**无**管理入口、登录、个人中心；技术栈 Vue3 + Vite + TypeScript + Pinia + Element Plus（或不引平行 UI 库的轻量样式）。

#### Scenario: 访客浏览已发布文
- **WHEN** 访客从列表进入详情
- **THEN** 展示标题/摘要/封面/正文/标签，不出现草稿或下架文

#### Scenario: 分类与标签导航
- **WHEN** 访客按分类或标签筛选
- **THEN** 列表仅含对应已发布文章

#### Scenario: 正文直接注入服务端 HTML
- **WHEN** 详情接口返回 `content`（渲染 HTML）
- **THEN** 前端原样注入展示图片/链接/表格等全语法内容；`blog-portal` 源码内不存在 Markdown 解析/渲染实现（自研 `renderMarkdown` 已移除）
