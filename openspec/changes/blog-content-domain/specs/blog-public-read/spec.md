## Purpose

向未登录读者提供已发布内容的公开只读接口：分类、标签、文章列表与详情，并保证草稿与下架内容不可见。

## ADDED Requirements

### Requirement: 公开分类与标签
系统 SHALL 提供 `GET /api/v1/public/categories`（启用分类树）与 `GET /api/v1/public/tags`（启用标签，可含仅已发布文章的计数）：无需登录；仅 `status=1 AND deleted=0`；可 `@RateLimit`。

#### Scenario: 仅启用分类
- **WHEN** 访客请求公开分类
- **THEN** 不包含停用/已删分类

#### Scenario: 标签计数口径
- **WHEN** 返回标签文章数
- **THEN** 仅统计 `status=1` 文章关联

### Requirement: 公开文章列表
系统 SHALL 提供 `GET /api/v1/public/articles`：入参 `current`/`size`、`categoryId?`、`tagId?`、`keyword?`、`orderBy?`（白名单）；仅 `status=1 AND deleted=0`；默认排序 `is_top DESC, publish_time DESC`；出参列表 VO **不含**内部 `status`/`hasDraft`、**不加载**正文。

#### Scenario: 按分类分页
- **WHEN** 访客按 `categoryId` 浏览
- **THEN** 仅返回该分类下已发布文章分页

#### Scenario: 不暴露草稿
- **WHEN** 存在草稿/已下架文章
- **THEN** 列表结果中不出现

### Requirement: 公开文章详情（id 或 slug）
系统 SHALL 提供 `GET /api/v1/public/articles/{idOrSlug}`：先按 id 精确匹配，未命中再按全局唯一 `slug` 匹配；仅 `status=1 AND deleted=0`，否则按不存在处理；返回主表字段 + 线上 `content`（`content_file_id → sys_file → MinIO`）+ 标签 + SEO（空则回退 title/summary）；**忽略** `draft_file_id`。

#### Scenario: 按 slug 访问已发布文
- **WHEN** 访客用非空 slug 访问已发布文章
- **THEN** 返回线上正文详情

#### Scenario: 草稿不可见
- **WHEN** 访客访问 `status=0` 或仅有草稿正文的文章
- **THEN** 按文章不存在处理（不暴露草稿存在性）

#### Scenario: 用户端忽略草稿轨
- **WHEN** 已发布文存在未发布草稿
- **THEN** `content` 仍来自 `content_file_id` 线上轨

### Requirement: 正文预签名（可选）
系统 SHALL 可选提供 `GET /api/v1/public/articles/{idOrSlug}/content-url`：仅已发布且存在 `content_file_id` 时签发短 TTL 预签名 GET URL 与过期时间；不替代详情组装读（默认服务端组装）。

#### Scenario: 获取预签名
- **WHEN** 对已发布文请求 content-url
- **THEN** 返回短时有效 URL；未发布或无正文返回业务错误
