# blog-admin-content-ui · Delta（adopt-markdown-editor）

> 基线：`article-content-markdown-only` / `blog-content-domain` 中写正文页要求。  
> **行为不变**（源文、双轨、SEO、≤500KB）；**实现改为**选定编辑器。

## MODIFIED Requirements

### Requirement: Markdown 写正文页（双轨）

写正文页 SHALL 提供 Markdown 编辑器（实现：`md-editor-v3` 项目薄封装，见 `markdown-editor-ux`）：工具栏 + 预览（或编辑器自带分屏），**无** markdown/html 格式切换；保存 **Markdown 源文**。按钮保持：保存草稿 / 发布 / 放弃草稿；已发布有草稿时提示「有未发布草稿 · 线上仍是旧版」；SEO 存草稿轨；≤500KB 提示与门禁保留。

#### Scenario: 工具栏编辑

- **WHEN** 用户点击加粗/标题/链接等工具栏
- **THEN** 编辑器向源文插入 Markdown 语法，预览同步更新

#### Scenario: 保存仍走双轨

- **WHEN** 用户保存草稿或发布
- **THEN** 调用既有 `PUT /api/v1/admin/articles/{id}/content`，`content` 为 Markdown 源文，双轨与 `sys_file` 语义与 `article-content-markdown-only` 一致
