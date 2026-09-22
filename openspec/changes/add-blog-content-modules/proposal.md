# Proposal: add-blog-content-modules

## Why

在 blog 基础与 RBAC 之上交付**具体业务**：文章（MD 入库 + 发布写 OSS）、扁平分类、标签、评论与审核、匿名前台阅读。  
编辑器 **md-editor-v3**；正文「库 MD 权威 + OSS 成品文件」双轨。

## What Changes

- 文章：草稿/发布/下线、置顶、封面引用、slug。
- **正文双轨**：`content_md` 存库；发布渲 HTML（XSS 白名单）并经 **file 服务** 写 `content.md` / `content.html`。
- 扁平分类、标签多对多。
- 评论：待审 → 通过/驳回；前台仅通过；管理端审核。
- 前台匿名列表/详情/评论提交；写文与审核需权限码。
- 前端：blog-admin（md-editor-v3）、blog-portal。
- **不包含**：ES、点赞关注、定时发布、图片处理流水线。

## Capabilities

### New Capabilities

- `blog-post`: 文章与正文双轨、发布流水线、状态机。
- `blog-taxonomy`: 扁平分类与标签。
- `blog-comment`: 评论提交与审核。
- `blog-public-read`: 匿名列表与详情阅读。

### Modified Capabilities

（无；依赖 blog-foundation / blog-rbac / file-object-storage。）

## Impact

- **服务**：blog-content-service；调用 file-service。
- **数据**：blog_post、blog_post_content、blog_category、blog_tag、blog_post_tag、blog_comment。
- **前端**：md-editor-v3。
- **文件**：OSS 键 `blog/posts/{id}/...`。
