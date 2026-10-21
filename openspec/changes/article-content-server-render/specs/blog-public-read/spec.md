# blog-public-read · Delta（article-content-server-render）

> 基线：`openspec/changes/blog-content-domain/specs/blog-public-read/spec.md`（未归档）。  
> 本 delta **仅修订「公开文章详情」需求**：`content` 由 Markdown 源文改为服务端渲染 HTML（渲染契约见 `article-content-server-render`）；列表、分类标签、草稿不可见等需求不变。

## MODIFIED Requirements

### Requirement: 公开文章详情（id 或 slug）
系统 SHALL 提供 `GET /api/v1/public/articles/{idOrSlug}`：先按 id 精确匹配，未命中再按全局唯一 `slug` 匹配；仅 `status=1 AND deleted=0`，否则按不存在处理；返回主表字段 + 线上 `content` **渲染 HTML**（`content_file_id → sys_file → MinIO` 读 Markdown 源文后**服务端渲染**：转义 + URL 消毒，契约见 `article-content-server-render`）+ 标签 + SEO（空则回退 title/summary）；**忽略** `draft_file_id`。

#### Scenario: 按 slug 访问已发布文
- **WHEN** 访客用非空 slug 访问已发布文章
- **THEN** 返回线上正文详情，`content` 为渲染 HTML

#### Scenario: 草稿不可见
- **WHEN** 访客访问 `status=0` 或仅有草稿正文的文章
- **THEN** 按文章不存在处理（不暴露草稿存在性）

#### Scenario: 用户端忽略草稿轨
- **WHEN** 已发布文存在未发布草稿
- **THEN** `content` 仍来自 `content_file_id` 线上轨（渲染 HTML）

#### Scenario: 全语法渲染直出
- **WHEN** 线上正文含 Markdown 图片/链接/表格等 §E.2 允许语法
- **THEN** `content` 中输出对应 HTML 结构（`<img>`/`<a>`/`<table>` 等），而非字面语法文本
