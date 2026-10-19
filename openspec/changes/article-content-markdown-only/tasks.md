# Tasks: article-content-markdown-only

> 实施顺序：DDL（征询）→ 后端契约收敛 → 前端写正文页 → 自检与回归。  
> 自检对齐 `CLAUDE.md` §3.2：`mvn -q compile`、`npm run lint && npm run type-check`（不以测试栈作门禁）。

## 1. 数据与文档锚点

- [x] 1.1 编写 `deploy/db/migration/blog_db/05_article_detail_drop_format_cols.sql`：可重复执行地 `DROP` `blog_article_detail.content_format` / `draft_content_format`（先查 `information_schema`；**执行前按 CLAUDE.md §7 征询**）
- [x] 1.2 确认 `docs/blog-system/{table,api,business}-design.md`、`README.md` 与本变更一致（已预改，抽查无 `contentFormat` 双格式残留）

## 2. 后端收敛（blog-service / 必要时 common）

- [x] 2.1 删除/停用 `ContentFormatEnum` 及一切 `markdown|html` 分支；`ArticleContentStorageService` 固定 ext `.md`、MIME `text/markdown`
- [x] 2.2 `ArticleContentRequest` / `ArticleDetailVO` / `BlogArticleDetail` 等去掉 `contentFormat`、`content_format`、`draft_content_format` 字段与读写
- [x] 2.3 保存正文仅校验非空 + ≤500KB；对象键 `blog-content/{id}/draft|live/{uuid}.md`；detail 只更新 `file_id` / `word_count` / `seo_*` 对应轨
- [x] 2.4 清理 OpenAPI/`@Schema` 中格式字段说明；编译通过 `cd backend/blog && mvn -q compile`

## 3. 管理端写正文页（blog-admin）

- [x] 3.1 去掉格式下拉（或只读「Markdown」）；`content` 只提交源文，请求体无 `contentFormat`
- [x] 3.2 按 `business-design` §E：源码 + 预览分栏（或编辑/预览切换）；工具栏只插 Markdown；提示禁 HTML/禁 base64；预览与 portal 同转义渲染
- [x] 3.3 `npm run lint && npm run type-check`（`frontend/blog-admin`）

## 4. 用户端核对（blog-portal）

- [x] 4.1 确认详情仅 `renderMarkdown` 渲染、类型无 `contentFormat`；不提交/展示 HTML 原文
- [x] 4.2 `npm run lint && npm run type-check`（`frontend/blog-portal`）

## 5. 联调回归

- [ ] 5.1 冒烟：保存草稿 → 发布 → 管理详情/公开详情 → content-url；对象为 `.md`、`sys_file` 登记正确；重复提交走幂等返回首次 Result
- [ ] 5.2 确认无格式校验错误、无 `.html` 新对象；`word_count` 仍落库
- [ ] 5.3 （DDL 执行后）抽检表结构无两格式列；回写 `docs/blog-system` 实施状态一行
