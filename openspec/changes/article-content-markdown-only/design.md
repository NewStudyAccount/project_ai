# Design: article-content-markdown-only

> 关联：`proposal.md` · 规格 `specs/` · 裁剪源 `docs/blog-system/business-design.md` §E · `table-design.md` · `api-design.md`  
> 约束：`CLAUDE.md` §5.4–5.6 模块边界、§6.2/§6.5 契约与表、§5.2 纯网关鉴权、§2.2 不引平行编辑器框架

---

## Context

`blog-content-domain` 已落码内容域（文章双轨正文 + `sys_file` + MinIO），但契约仍带 `contentFormat ∈ markdown|html` 与表列 `content_format` / `draft_content_format`。用户端已按 Markdown 渲染，HTML 轨实际未闭环。本变更**只收敛格式契约与编写体验**，不改双轨/状态机/权限。

现状代码锚点（实施对照）：

| 层 | 位置 |
|----|------|
| 枚举 | `ContentFormatEnum`（MARKDOWN / HTML） |
| 存储 | `ArticleContentStorageService.putContent`（按 format 选 ext/MIME） |
| 业务 | `ArticleService` 读写 format 字段 |
| 契约 | `ArticleContentRequest` / `ArticleDetailVO` / `BlogArticleDetail` |
| 前端 | `ArticleContentEditor.vue`（格式下拉）；portal 已 `renderMarkdown` |

---

## Goals / Non-Goals

**Goals:**

- 接口、表、对象存储三端**唯一 Markdown**，无格式枚举
- 写正文页可落地的 Markdown 编写约定（分栏预览、源文保存、禁 HTML 结构）
- 最小代码集：删字段/分支，不顺手重构

**Non-Goals:**

- HTML 历史对象自动转 Markdown
- 引入 WYSIWYG / 新 UI 框架 / 独立预览服务
- 改草稿双轨、发布状态机、权限码、`sys_file` 语义
- 在 `CLAUDE.md` 写入表字段或实现代码

---

## Decisions

| # | 决策 | 理由 | 备选（否决） |
|---|------|------|----------------|
| 1 | **删除** `contentFormat` 入参/出参与表列，而不是「固定 markdown 仍保留列」 | 用户要求保证唯一性；保留列仍有双源与误写风险 | 保留列写死默认（否决：契约不干净） |
| 2 | 对象键 / MIME **写死** `.md` + `text/markdown` | 与 `object-storage-design.md` 登记一致；去掉分支 | 按扩展名推断（否决：已无第二格式） |
| 3 | 编写约定落在 **business-design §E**，specs 引用可测行为 | 避免把长文交互说明塞进 requirement；场景仍可测 | 只写在 prototype（否决：效力不足） |
| 4 | 编辑器 = **源码 + 分栏预览**，工具栏只插 Markdown | 轻量、与 Element Plus 现栈兼容；保存源文与 OSS 契约一致 | WYSIWYG（否决：重、易存 HTML） |
| 5 | 表变更 = **DROP 两列**，SQL 随变更人工执行 | `CLAUDE.md` §2.2 无 Flyway；不可逆步骤执行前征询 | 保留列作兼容（否决：与目标冲突） |
| 6 | 历史 HTML 对象**不迁移**，详情仍当 Markdown 源文输出 | 收敛代价可接受；避免扩大范围写迁移工具 | 启动时批量转换（否决：范围蔓延） |
| 7 | `word_count` 保留 | 仅展示；与格式无关 | — |

**模块落点（不越界）：**

```
blog-service   删 format 分支 / DTO·VO 字段
blog-common    如有格式枚举依赖则同步（ContentFormatEnum 在 service 则只改 service）
blog-framework 不改 ObjectStorageClient 口径（仍 put(stream, size, contentType)）
blog-admin     写正文页去格式下拉 + 分栏预览示意/约定提示
blog-portal    已 Markdown 渲染，核对无 contentFormat 依赖即可
```

---

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| [DROP 列不可逆] | SQL 标注征询；先备份表结构；执行记录写入 `deploy/db/migration/blog_db/` 旁文档 |
| [存量 html 正文渲染观感变化] | 在 proposal/Impact 已声明接受；若业务后续投诉再开「HTML 转 Markdown」独立变更 |
| [前端仍提交 contentFormat] | 后端忽略未知字段或删 DTO 字段后校验失败——以删字段+回归为准；联调冒烟覆盖 |
| [预览 XSS] | 约定「先转义再渲染」；与 portal `renderMarkdown` 同口径；禁止 v-html 原文 |
| [blog-content-domain 规格仍写双格式] | 本变更 specs 覆盖；归档后回写 docs；不改其 proposal/design 裁决框架（§7） |

---

## Migration Plan

1. **DDL（人工、执行前征询）**：`ALTER TABLE blog_article_detail DROP COLUMN content_format, DROP COLUMN draft_content_format;`（幂等写法可先查 `information_schema`）
2. **后端**：删枚举/字段/分支；`content` 一律 `text/markdown` + `….md`
3. **前端**：`blog-admin` 写正文页按 §E；去掉 format 下拉
4. **回归**：保存草稿 / 发布 / 详情 / 公开详情 / content-url；确认无 format 校验错误
5. **回滚**：代码可回退；**列删除不自动回滚**（可按备份重建列，值恒为 markdown）

---

## Open Questions

- 存量含 HTML 标签的正文是否需人工抽查改写（默认：不迁移，随发布再改）。
- `word_count` 算法是否需精确中文分词（默认：去 Markdown 语法后长度估算即可）。
