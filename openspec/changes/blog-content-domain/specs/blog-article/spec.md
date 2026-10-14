## Purpose

管理文章主表与详细表的生命周期：元数据与正文分页分离、正文 OSS 双轨（草稿不掉线、发布才切换）、发布/下架/逻辑删除，并强制 OSS 对象经 `sys_file` 登记后以 `file_id` 引用。

## ADDED Requirements

### Requirement: 文章分页查询（管理端）
系统 SHALL 提供文章分页（`GET /api/v1/admin/articles`，权限 `blog:article:list`）：筛选 `keyword`（标题 LIKE）、`categoryId`、`tagId`、`status`；分页 `{records,total,size,current}`；出参含 `id` String、`title`、`slug`、`summary`、`coverFileId`、分类名、标签列表、`authorName`、`status`、`publishTime`、`isTop`、`hasDraft` 等；**禁止**加载 OSS 正文。排序字段白名单：`publish_time|is_top|create_time`。

#### Scenario: 按状态筛选分页
- **WHEN** 管理员传入 `status=0` 与分页参数
- **THEN** 仅返回草稿文章列表，且不含正文字段

#### Scenario: 标题关键字检索
- **WHEN** 传入 `keyword`
- **THEN** 仅返回标题匹配的记录（参数绑定，禁止拼 SQL）

### Requirement: 新建文章主表
系统 SHALL 支持仅创建主表（`POST /api/v1/admin/articles`，权限 `blog:article:create`，`@Idempotent`）：入参 `title*`、`slug?`、`categoryId?`、`summary?`、`coverFileId?`、`tagIds?`、`isTop?`；**不含正文**；`status=0`，`author_id`/`author_name` 自网关 `X-User-*` 快照；封面 `coverFileId` 须指向未删的 `sys_file`（`ARTICLE_COVER` 或 `FILE`）；成功记审计（ARTICLE_CREATE）。前端 MUST NOT 一页同时提交主表+正文。

#### Scenario: 新建草稿主表
- **WHEN** 提交合法标题与可选元数据
- **THEN** 返回主表 VO（无 content），`status=0`，可进入写正文页

#### Scenario: slug 冲突
- **WHEN** 提交已存在非空 `slug`
- **THEN** 返回 `SLUG_DUPLICATE`，不落库

### Requirement: 更新文章主表
系统 SHALL 支持仅更新主表（`PUT /api/v1/admin/articles/{id}`，权限 `blog:article:update`，`@Idempotent`）：只碰标题/摘要/分类/标签/封面/slug/置顶；**不得**改 `content_file_id`、`draft_file_id`、`status`；标签整组替换（删旧插新，同事务）；已发布文改元数据保持 `status=1`。

#### Scenario: 更新已发布文元数据
- **WHEN** 修改标题/分类/标签
- **THEN** 主表更新且 `status` 仍为 1，正文指针不变

### Requirement: 保存正文（草稿双轨）
系统 SHALL 支持保存正文（`PUT /api/v1/admin/articles/{id}/content`，权限 `blog:article:update`，`@Idempotent`）：入参 `mode=draft|publish`、`content*`（≤500KB）、`contentFormat?`（`markdown|html`）、`seoTitle?`、`seoDescription?`。写路径 MUST：put 新对象（`blog-content/{articleId}/draft|live/{uuid}.{md|html}`）→ **INSERT `sys_file`**（`biz_type=ARTICLE_DRAFT|ARTICLE_LIVE`，`biz_id=article_id`）→ 更新 detail 对应轨 `file_id`/格式/字数/SEO。`mode=draft` MUST NOT 改 `content_file_id` 与 `status`（已发布文用户端仍读旧线上文）。旧 draft `sys_file` 逻辑删。`mode=publish` 等价写草稿并立即执行发布。正文格式/大小非法或超限返回 `CONTENT_TOO_LARGE`/校验错误。

#### Scenario: 已发布文保存草稿不掉线
- **WHEN** 对 `status=1` 文章 `mode=draft` 保存新正文
- **THEN** `draft_file_id` 更新、`content_file_id` 不变、`status` 仍为 1；用户端仍读原文

#### Scenario: 首次写入正文
- **WHEN** 从未发布文章保存 `mode=draft`
- **THEN** detail 草稿轨有 `file_id`，`content_file_id=0`，`status=0`

#### Scenario: 正文超限
- **WHEN** `content` 超过 500KB
- **THEN** 返回 `CONTENT_TOO_LARGE`，不 put、不落 `sys_file`

#### Scenario: 写入必登记 sys_file
- **WHEN** 任意保存正文成功
- **THEN** 存在对应 `sys_file` 行且 detail 只持 `file_id`，业务表无裸 `object_key`

### Requirement: 放弃草稿
系统 SHALL 支持放弃未发布草稿（`DELETE /api/v1/admin/articles/{id}/draft`，`blog:article:update`，`@Idempotent`）：`draft_file_id=0` 时返回 `DRAFT_NOT_FOUND`；否则草稿 `sys_file` 逻辑删、detail 草稿字段清零；**不**改线上指针与 `status`。

#### Scenario: 放弃草稿
- **WHEN** 存在未发布草稿时请求放弃
- **THEN** 草稿轨清空，线上版与 `status` 不变

### Requirement: 发布与下架
系统 SHALL 支持发布/更新发布（`POST …/publish`，权限 `blog:article:publish`，`@Idempotent`）与下架（`POST …/offline`，同权限）。发布：有草稿则 draft 提升为新 live（新 `sys_file` ARTICLE_LIVE 行）→ `content_file_id` 切换、`draft_file_id=0`、`seo_*`←草稿 SEO、旧 live/draft 逻辑删；无草稿但已有 `content_file_id` 仅置 `status=1` 并刷新 `publish_time`；两者皆无返回 `CONTENT_REQUIRED`。下架：须已发布 → `status=2`，保留 `content_file_id`。

#### Scenario: 有草稿发布
- **WHEN** 对存在 `draft_file_id` 的文章发布
- **THEN** 用户端切换到新正文，`hasDraft=false`，`status=1`

#### Scenario: 无正文不可发布
- **WHEN** 既无草稿也无线上正文时发布
- **THEN** 返回 `CONTENT_REQUIRED`，状态不变

#### Scenario: 下架后重发
- **WHEN** 对 `status=2` 且仍有 `content_file_id` 的文章再次 publish
- **THEN** `status=1` 并更新 `publish_time`，用户端重新可见

### Requirement: 删除文章
系统 SHALL 支持逻辑删除文章（`DELETE /api/v1/admin/articles/{id}`，权限 `blog:article:delete`，`@Idempotent`）：`status=1` 时返回 `ARTICLE_PUBLISHED`（须先下架）；否则主表 `deleted=1`，关联 detail/tag 逻辑删，相关 `sys_file`（COVER/LIVE/DRAFT）逻辑删并记审计。

#### Scenario: 已发布不可直接删
- **WHEN** 删除 `status=1` 的文章
- **THEN** 返回 `ARTICLE_PUBLISHED`，数据保留

#### Scenario: 草稿可删
- **WHEN** 删除 `status=0` 的文章
- **THEN** 逻辑删除成功，关联行一并逻辑删

### Requirement: 管理端文章详情
系统 SHALL 提供管理端详情（`GET /api/v1/admin/articles/{id}`，权限 `blog:article:detail`）：返回主表字段 + **当前编辑器内容**（有草稿读草稿轨，否则线上轨，否则空）及 `contentFrom=draft|live|none`、`hasDraft`、`draftFileId`/`contentFileId`、对应轨格式/字数/SEO。

#### Scenario: 有草稿时详情
- **WHEN** 文章存在未发布草稿
- **THEN** `content` 来自草稿轨且 `contentFrom=draft`，`hasDraft=true`
