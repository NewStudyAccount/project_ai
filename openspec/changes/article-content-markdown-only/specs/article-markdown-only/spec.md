# article-markdown-only · 文章正文唯一 Markdown

> 正文格式、存储与编写契约；裁剪源 `docs/blog-system/business-design.md` §E。

## ADDED Requirements

### Requirement: 正文唯一 Markdown（无格式字段）

系统 SHALL 将文章正文格式固定为 Markdown：接口 MUST NOT 接受或返回 `contentFormat`（或等价格式字段）；`blog_article_detail` MUST NOT 含 `content_format` / `draft_content_format`；MinIO 对象 MUST 为 `blog-content/{articleId}/draft|live/{uuid}.md` 且 `Content-Type=text/markdown`。

#### Scenario: 保存正文不带格式字段

- **WHEN** 管理端 `PUT /api/v1/admin/articles/{id}/content` 提交 `mode`、`content`（Markdown 源文）、可选 SEO，且不包含 `contentFormat`
- **THEN** 服务端按 Markdown 落对象（`.md` / `text/markdown`）并登记 `sys_file`，响应成功且不含格式字段

#### Scenario: 详情出参无格式字段

- **WHEN** 管理端或用户端读取文章详情
- **THEN** 出参含 `content`（Markdown 源文）等字段，MUST NOT 含 `contentFormat`

#### Scenario: 表无格式列

- **WHEN** 检视 `blog_article_detail` 结构或落库逻辑
- **THEN** 不存在 `content_format` / `draft_content_format`；仅保留 `word_count` / `draft_word_count` 等非格式字段

### Requirement: 正文大小与空内容校验

系统 SHALL 在保存正文时校验：`content` 非空且 UTF-8 字节数 ≤500KB；超限返回业务码 `CONTENT_TOO_LARGE`，空内容返回对应业务/校验错误，且不写对象、不登记 `sys_file`。

#### Scenario: 正文超限

- **WHEN** `content` UTF-8 大小 >500KB
- **THEN** 返回 `CONTENT_TOO_LARGE`，不 put 对象、不插入 `sys_file`

#### Scenario: 正文为空

- **WHEN** `content` 为空或全空白
- **THEN** 返回业务/校验错误，不写 MinIO

### Requirement: Markdown 编写边界（源文保存）

写正文页 SHALL 以 Markdown **源码**为保存对象；工具栏 SHALL 只插入 Markdown 语法；系统 MUST 将 HTML 标签视为文本或拒绝作为结构提交；图片 MUST NOT 以 base64 大图内嵌为正文主体；预览 MUST 与用户端同「先转义再渲染」，且保存内容 MUST NOT 是预览 HTML。

#### Scenario: 保存源文而非预览 HTML

- **WHEN** 用户在分栏预览可见渲染结果后点击保存草稿/发布
- **THEN** 提交的 `content` 为编辑器源码中的 Markdown，而非预览区 HTML

#### Scenario: 禁止脚本/结构 HTML 执行

- **WHEN** 正文含 `<script>` 或块级 HTML 标签
- **THEN** 渲染输出不得执行脚本；HTML 不作为文档结构生效（转义展示或剥离，与 portal 渲染口径一致）

#### Scenario: 链接协议白名单

- **WHEN** Markdown 链接使用 `javascript:` 等非 http(s) 协议
- **THEN** 渲染时不生成可点击执行链接（过滤或转纯文本）

### Requirement: 编辑器布局（源码与预览）

写正文页 SHALL 提供 Markdown 源码编辑区与预览区（左右分栏或等价「编辑/预览」切换）；预览失败 MUST NOT 阻断保存。

#### Scenario: 分栏编辑

- **WHEN** 用户进入写正文页
- **THEN** 可同时（或切换）查看 Markdown 源码与渲染预览；预览与用户端详情渲染口径一致

#### Scenario: 预览异常仍可保存

- **WHEN** 预览渲染出错或延迟
- **THEN** 用户仍可保存草稿/发布（以源码 `content` 为准）
