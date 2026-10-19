# markdown-editor-ux · 写正文 Markdown 编辑器

> 选型见 change `design.md`（**md-editor-v3**）；源文契约见 `article-content-markdown-only`。

## ADDED Requirements

### Requirement: 使用单一选定 Markdown 编辑器

管理端写正文 SHALL 使用**唯一**选定的 Markdown 编辑器组件（当前裁决：`md-editor-v3`，经项目薄封装），MUST NOT 在同一写正文页混用第二套编辑器或回退为无工具栏裸 textarea 作为主路径。

#### Scenario: 打开写正文页

- **WHEN** 用户进入写正文页
- **THEN** 展示成熟编辑器 UI（工具栏 + 源码/预览），可直接用工具栏插入 Markdown 语法

#### Scenario: 版本取自基线

- **WHEN** 工程声明 `md-editor-v3` 依赖
- **THEN** 版本与 `docs/version-baseline.md` 前端依赖登记一致

### Requirement: 保存 Markdown 源文

编辑器 SHALL 以 `v-model`（或等价）绑定 **Markdown 源文字符串**；保存草稿/发布提交的 `content` MUST 为该源文，MUST NOT 为编辑器预览 HTML。

#### Scenario: 保存草稿

- **WHEN** 用户在编辑器中修改并点击保存草稿
- **THEN** `PUT …/content` 的 `content` 为编辑器源文（Markdown），无 HTML 文档结构

### Requirement: 能力面收紧

项目封装 SHALL：默认**关闭**编辑器直传文件到业务存储（或仅允许填 URL 插图）；粘贴 HTML MUST NOT 将结构 HTML 写入源文；预览 MUST NOT 作为落库内容；MUST 过滤 `javascript:` 等危险链接。

#### Scenario: 粘贴富文本

- **WHEN** 用户从 Word/网页粘贴含 HTML 标签的内容
- **THEN** 源文为纯文本或 Markdown，不保留可执行/块级 HTML 结构

#### Scenario: 危险链接

- **WHEN** 源文或工具栏插入 `javascript:` 链接
- **THEN** 预览不生成可执行链接；保存内容仍为源文且渲染侧安全
