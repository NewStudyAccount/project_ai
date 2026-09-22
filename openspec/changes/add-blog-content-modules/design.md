# Design: add-blog-content-modules

## Context

- 见 `docs/blog-system-design.md`：C 形态、匿名前台、评论审核、扁平分类、MD 原文存库 + OSS 成品。
- file 为独立 F2 服务；编辑器 md-editor-v3。

## Goals / Non-Goals

**Goals:** 文章/分类/标签/评论与双轨发布；admin 与 portal。  
**Non-Goals:** ES、社交、复杂审核流、媒体处理。

## Decisions

### D1. 状态机
草稿 → 发布 → 下线；发布写 publish_time 并触发 OSS。

### D2. 正文双轨
权威 `content_md`；发布 MD→HTML → file 覆盖写；前台读 OSS HTML；先库后文件。

### D3. 分类/标签
扁平分类；`blog_post_tag` 物理删重建。

### D4. 评论
待审/通过/驳回；默认待审；`blog:comment:moderate`。

### D5. 权限码
`blog:post:create|publish|manage`、`blog:category:manage`、`blog:tag:manage`、`blog:comment:moderate`。

## Risks
- XSS → HTML 白名单。匿名评论 → 限流+待审。不一致 → 以库 MD 重放发布。

## Migration Plan
1. 业务表。 2. 发布链路接 file。 3. admin/portal 联调。

## Open Questions
1. 匿名评论验证码阈值。 2. slug 手填 vs 自动生成。
