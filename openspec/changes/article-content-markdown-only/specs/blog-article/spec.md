# blog-article · Delta（article-content-markdown-only）

> 基线：`openspec/changes/blog-content-domain/specs/blog-article/spec.md`（未归档）。  
> 本 delta **收敛**保存正文/详情的格式契约；双轨、状态机、权限不变。

## MODIFIED Requirements

### Requirement: 保存正文（草稿双轨）

系统 SHALL 支持保存正文（`PUT /api/v1/admin/articles/{id}/content`，权限 `blog:article:update`，`@Idempotent`）：入参 `mode=draft|publish`、`content*`（Markdown 源文，≤500KB）、`seoTitle?`、`seoDescription?`；**无** `contentFormat`。写路径 MUST：put 新对象（`blog-content/{articleId}/draft|live/{uuid}.md`，MIME `text/markdown`）→ **INSERT `sys_file`**（`biz_type=ARTICLE_DRAFT|ARTICLE_LIVE`，`biz_id=article_id`）→ 更新 detail 对应轨 `file_id`/字数/SEO（**无格式列**）。`mode=draft` MUST NOT 改 `content_file_id` 与 `status`。旧 draft `sys_file` 逻辑删。`mode=publish` 等价写草稿并立即执行发布。空内容或超限返回 `CONTENT_TOO_LARGE`/校验错误。

#### Scenario: 草稿保存成功

- **WHEN** `mode=draft` 且 `content` 合法
- **THEN** 新 `….md` 对象 + `sys_file(ARTICLE_DRAFT)` + `draft_file_id`/`draft_word_count`/`draft_seo_*` 更新；`status` 与 `content_file_id` 不变

#### Scenario: 不再识别 HTML 格式

- **WHEN** 入参带有历史字段 `contentFormat=html`（若客户端未升级）
- **THEN** 服务端忽略或拒绝未知字段之一（以实现为准，**不得**写 `.html` 对象或格式列）；合法无格式字段的请求不受影响

## REMOVED Requirements

### Requirement: 保存正文（html 格式分支）

**Reason**: 正文唯一 Markdown，删除双格式契约（`article-content-markdown-only`）  
**Migration**: 不再传入 `contentFormat`；对象与 MIME 固定 Markdown；表列 `content_format`/`draft_content_format` 由 DDL 删除
