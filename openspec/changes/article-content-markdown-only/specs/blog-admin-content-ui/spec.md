# blog-admin-content-ui · Delta（article-content-markdown-only）

> 基线：`openspec/changes/blog-content-domain/specs/blog-admin-content-ui/spec.md`（未归档）。  
> 本 delta 收敛写正文页编辑体验；两页流程、双轨按钮、SEO 草稿轨不变。

## MODIFIED Requirements

### Requirement: Markdown 写正文页（双轨）

写正文页 SHALL 提供 **Markdown 源码编辑 + 预览**（分栏或等价切换），且 MUST NOT 提供 markdown/html 格式切换；工具栏 SHALL 只插入 Markdown 语法。保存时 MUST 提交 Markdown **源文**（非预览 HTML）。按钮保持：**保存草稿**（`mode=draft`）、**发布/更新发布**、**放弃草稿**；已发布且有草稿时显示「有未发布草稿 · 线上仍是旧版」；SEO 保存至草稿轨。≤500KB 提示保留。

#### Scenario: 无格式切换

- **WHEN** 用户打开写正文页
- **THEN** 不出现正文格式下拉（或仅只读「Markdown」）；可编辑源码并查看预览

#### Scenario: 保存提交源文

- **WHEN** 用户编辑 Markdown 并点击保存草稿
- **THEN** 请求 `content` 为源码文本；无 `contentFormat` 字段

#### Scenario: 双轨提示仍在

- **WHEN** 已发布文章存在未发布草稿
- **THEN** 页面提示线上仍旧版，且保存草稿不改变线上 `content_file_id`
