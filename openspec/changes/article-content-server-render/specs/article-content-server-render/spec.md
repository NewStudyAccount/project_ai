# article-content-server-render · Spec

> 正文渲染职责归属：Markdown→HTML **唯一发生在服务端**（本能力为新增）。
> 契约口径源：`docs/blog-system/business-design.md` §E.2 / §E.5、`docs/blog-system/api-design.md` §0 / §6.4。

## ADDED Requirements

### Requirement: 服务端渲染职责唯一
系统 SHALL 在服务端将文章正文 Markdown 源文转换为 HTML（读取时转换：公开详情组装路径内完成）；转换 MUST 唯一由 `blog-framework` 封装的渲染器组件完成（flexmark-java，Parser/HtmlRenderer 实例复用），业务代码 MUST NOT 自建 Markdown→HTML 转换或另配渲染参数；前端 MUST NOT 自带 Markdown 渲染实现，只注入服务端返回的 HTML。OSS 存储对象 MUST 保持 Markdown 源文（`text/markdown`），MUST NOT 落 `.html` 对象。

#### Scenario: 公开详情读取即渲染
- **WHEN** 调用公开文章详情且线上存在正文源文
- **THEN** 服务端读取 Markdown 源文后完成 HTML 转换再返回，响应 `content` 为 HTML 字符串

#### Scenario: 渲染点唯一
- **WHEN** 检查 `blog-service` / `blog-portal` / `blog-admin` 源码
- **THEN** Markdown→HTML 转换逻辑仅存在于 `blog-framework` 渲染器封装一处

### Requirement: 渲染语法覆盖
渲染器 SHALL 支持 business-design §E.2「允许语法」全集：标题 `#`–`######`、段落、有序/无序列表、引用、围栏代码块、行内代码、链接、**图片**、粗体/斜体、分隔线、表格。输出 HTML 结构与语法语义一致（图片输出 `<img>`、链接输出 `<a>` 等）。

#### Scenario: 图片语法渲染为 img
- **WHEN** 正文含 `![alt](http(s)://… 或相对路径)`（含 title 形式）
- **THEN** `content` 中输出 `<img src="…" alt="…">` 结构，而非字面 `![…](…)` 文本

#### Scenario: 表格与有序列表
- **WHEN** 正文含表格或 `1.` 有序列表
- **THEN** 分别输出 `<table>` 与 `<ol>` 结构

### Requirement: 转义与 URL 消毒
渲染 MUST **先转义再生成**：源文中的原生 HTML（`<div>`、`<script>`、内联样式等）一律转义为可见文本输出，MUST NOT 作为 HTML 执行；链接与图片 URL MUST 消毒——`javascript:` 等可执行协议 MUST 丢弃（不可达），仅 `http`/`https` 与相对路径可达。

#### Scenario: 原生 HTML 视为文本
- **WHEN** 正文含 `<script>alert(1)</script>` 或 `<div>` 等原生 HTML
- **THEN** `content` 中以转义文本形式呈现，浏览器注入后不执行、不产生 DOM 元素

#### Scenario: 可执行协议丢弃
- **WHEN** 正文含 `[x](javascript:alert(1))` 或 `![x](javascript:…)`
- **THEN** 输出中不出现可执行 URL（`javascript:` 不可达）

### Requirement: 正文字段语义分端
`content` 字段语义 MUST 按端区分：公开详情（`GET /api/v1/public/articles/{idOrSlug}`）`content` = **服务端渲染 HTML**（只读注入）；管理端详情（`GET /api/v1/admin/articles/{id}`）`content` = **Markdown 源文**（编辑回写）。保存正文入参 `content` 恒为 Markdown 源文，≤500KB，MUST NOT 收 HTML 渲染结果。

#### Scenario: 公开端收 HTML
- **WHEN** 请求公开文章详情
- **THEN** `content` 为渲染 HTML，且不含 Markdown 源文回传字段

#### Scenario: 管理端收源文
- **WHEN** 请求管理端文章详情（写正文页）
- **THEN** `content` 为可直接回编辑器的 Markdown 源文
