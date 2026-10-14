## Purpose

在 `blog-admin` 落地内容管理界面：分类/标签维护、文章两页流程（主表页与写正文页分离）、Markdown 编辑器双轨保存，以及站点配置页。

## ADDED Requirements

### Requirement: 分类与标签管理页
`blog-admin` SHALL 提供分类树管理与标签列表页：创建/编辑/停用/排序/删除交互与后端契约一致；删除失败（有子/有引用）展示 `msg`；权限码控制按钮显隐（`blog:category:*` / `blog:tag:*`），**禁止**前端硬编码角色名。

#### Scenario: 删除被引用标签
- **WHEN** 管理员删除仍被文章引用的标签
- **THEN** 界面展示业务错误 `msg`，列表不错误刷新为已删

### Requirement: 文章列表与两页编辑流程
`blog-admin` SHALL 提供文章列表（筛选/分页/发布/下架/删除/进入编辑）与**分离的**新建主表页、编辑主表页、写正文页：新建主表成功后**跳转写正文页**；改主表与改正文**不得**同页混提；列表展示 `hasDraft` 状态提示。

#### Scenario: 新建文章闭环
- **WHEN** 管理员新建主表并进入写正文保存草稿后发布
- **THEN** 列表出现已发布文章，用户端可读（联调约定）

#### Scenario: 主表保存不碰正文
- **WHEN** 在编辑主表页保存
- **THEN** 不调用 content 接口，正文指针不变

### Requirement: Markdown 写正文页（双轨）
写正文页 SHALL 提供 Markdown 编辑器（≤500KB 提示）与按钮：**保存草稿**（`mode=draft`）、**发布/更新发布**、**放弃草稿**；已发布且有草稿时显示「有未发布草稿 · 线上仍是旧版」；SEO 字段保存至草稿轨，发布时覆盖线上 SEO。

#### Scenario: 已发布文改稿不下线
- **WHEN** 已发布文在写正文页点「保存草稿」
- **THEN** 提示成功且标明线上仍旧版；不触发下架

#### Scenario: 放弃草稿
- **WHEN** 点「放弃草稿」
- **THEN** 草稿丢弃后编辑器回到当前线上正文（若有）

### Requirement: 封面上传接线
文章表单 SHALL 使用既有文件上传（`POST /api/v1/files`，`blog:file:upload`）并回填 `coverFileId`；`bizType` 可传 `ARTICLE_COVER`；不得绕过文件管理直写 MinIO。

#### Scenario: 选择封面
- **WHEN** 管理员上传封面成功
- **THEN** 表单持有 `coverFileId`，保存主表时提交该 id

### Requirement: 站点配置页
`blog-admin` SHALL 提供站点配置编辑页（`blog:site:list`/`blog:site:update`）：读改 `site.title`/`site.description`/`site.copyright`/`site.about` 并保存。

#### Scenario: 保存站点关于
- **WHEN** 管理员修改关于文案并保存
- **THEN** 保存成功；用户端关于页随后展示新文案
