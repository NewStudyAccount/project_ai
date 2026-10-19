# Proposal: article-content-markdown-only

> 状态：提案  
> 关联设计：`docs/blog-system/business-design.md` §3.3 / §E（Markdown 编写约定）；字段源 `table-design.md`；接口源 `api-design.md`  
> 上游变更：`blog-content-domain`（内容域，尚未归档；本变更**收敛正文格式契约**，不扩展新业务能力）

---

## Why

文章正文当前契约为 **markdown | html 双格式**（接口 `contentFormat`、表列 `content_format` / `draft_content_format`、对象键 `.{md|html}`），带来格式双源、校验与渲染口径分裂。业务已裁决：**正文一律用 Markdown 编写与存储**，删除格式字段以保证唯一性；并补齐「怎么写 Markdown」的编写约定（此前仅有「Markdown 编辑器」提法，无交互/语法边界）。

## What Changes

- **BREAKING（接口）**：`PUT /api/v1/admin/articles/{id}/content` **删除**入参 `contentFormat`；管理端详情 / ArticleDetailVO **删除**出参 `contentFormat`；`content` 语义固定为 **Markdown 源文**
- **BREAKING（表结构）**：`blog_article_detail` **删除** `content_format`、`draft_content_format` 两列（保留 `word_count` / `draft_word_count`）
- **存储**：对象键固定 `blog-content/{articleId}/draft|live/{uuid}.md`；MIME 固定 `text/markdown`；不再支持 `.html` 对象
- **编写约定（新增契约）**：写正文页左源码/右预览；工具栏只插 Markdown 语法；**禁止**把 HTML 当正文结构（粘贴降纯文本）；图片禁 base64；保存的是 Markdown **源文**而非预览 HTML；字数仅展示不挡发布
- **校验**：仅校验非空 + ≤500KB（+ 编写约定中的链接/图片边界）；不再校验 format 枚举
- **文档**：`docs/blog-system/*` 已按此收敛（本变更落地代码/DDL/前端，并回写实现偏差）

### Non-goals（项目边界，对齐 `CLAUDE.md` §1 / §2.2）

- 不做 HTML 正文兼容渲染、不做双格式自动转换/迁移工具
- 不引入所见即所得（WYSIWYG）编辑器或重型 Markdown IDE；不新增平行 Markdown 库选型以外的 UI 框架
- 不做正文版本表、协同编辑、实时预览服务化
- 不改草稿双轨（draft/live）、`sys_file` 登记、发布/下架状态机、权限码体系
- 不把服务清单/表结构细节写入 `CLAUDE.md`

## Capabilities

### New Capabilities

- `article-markdown-only`: 文章正文唯一 Markdown——接口无格式字段、表无格式列、对象 `.md`/`text/markdown`、编写与渲染边界（源文保存、禁 HTML 结构、分栏预览、≤500KB）

### Modified Capabilities

> `openspec/specs/` 尚无已归档同名能力；下列为对未归档变更 `blog-content-domain` 中规格的**契约收敛**，实施时以本变更 specs 为准，并回写该变更中仍含 `contentFormat` / `markdown|html` 的表述（仅文档与 tasks 状态位，不改其 proposal/design 既有裁决框架）。

- `blog-article`（见 `blog-content-domain`）：保存正文/详情出参去掉 `contentFormat` 与格式落库；对象键与 MIME 固定 Markdown
- `blog-admin-content-ui`（见 `blog-content-domain`）：写正文页按 `article-markdown-only` 编写约定（分栏预览、源码保存、禁 HTML）

## Impact

| 面 | 影响 |
|----|------|
| **API** | `PUT …/content` 入参、`ArticleAdminDetailVO` / `ArticleDetailVO` 出参；OpenAPI 注解同步 |
| **DB** | `blog_article_detail`：`DROP` `content_format` / `draft_content_format`（**不可逆**，执行前须按 `CLAUDE.md` §7 征询；SQL 随本变更提供、人工执行） |
| **后端** | `ContentFormatEnum`、`ArticleContentRequest`、`ArticleService`、`ArticleContentStorageService`、相关 DTO/VO |
| **前端** | `blog-admin` 写正文页（去格式下拉，补分栏预览/约定提示）；`blog-portal` 保持 Markdown 渲染（已一致） |
| **文档** | 已改：`docs/blog-system/{business,table,api}-design.md`、`README.md`、`prototype.html`、`er-diagram.html` |
| **系统边界** | 仅 **blog** 系统内；不跨系统、不改网关/Nginx/认证；对象存储仍只经 `blog-framework` 封装 |

**兼容说明：** 若库中已有 `html` 格式历史对象，新代码不再读取格式列；详情读对象后**按 Markdown 源文**交给前端渲染。存量 HTML 正文观感可能变化，属接受的收敛代价；无需数据回填（格式列删除后不参与逻辑）。
