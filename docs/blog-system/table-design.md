# blog-system · 库表设计（新增 / 修改 / 待定）

> 状态：**设计稿**；实施时建表 SQL 落 `deploy/db/migration/blog_db/`，**人工执行**（`CLAUDE.md` §2.2）
> 口径：审计字段 / 16 位发号 / `id` 对外 String / 无物理外键 / `uk_` 不做写幂等（`CLAUDE.md` §6.4–6.5）

---

## 1. 结论清单（先看这个）

### 1.1 既有表：需要修改吗？

| 既有表 | 结论 | 说明 |
|--------|------|------|
| `sys_sequence` | **不改** | 发号沿用 |
| `sys_file` | **要改（补 2 列）** | **OSS 对象唯一登记表**；补 `biz_type`/`biz_id`，正文/草稿/封面一律先登记再引用 |
| `blog_audit_log` | **不改** | `target_type` 扩枚举值 CATEGORY / TAG / ARTICLE / FILE（代码层） |
| `sys_menu` / `sys_role` / `sys_user_role` / `sys_role_menu` | **不改** | 菜单/权限为**种子数据**，实施时 INSERT |

**结论：`sys_file` 补业务关联列（DDL 小改）+ 新增 6 张业务表；不另建平行的「OSS 对象表」。**

### 1.2 需要新增的表（6 张）

| # | 表名 | 用途 | 必要性 |
|---|------|------|--------|
| 1 | `blog_category` | 文章分类（树，≤2 级） | 必须 |
| 2 | `blog_tag` | 文章标签 | 必须 |
| 3 | `blog_article` | 文章主表（列表/状态/归属/摘要） | 必须 |
| 4 | `blog_article_detail` | 文章详细表（正文**文件引用**，不存正文） | 必须 |
| 5 | `blog_article_tag` | 文章-标签 M:N | 必须 |
| 6 | `blog_site_config` | 用户端站点配置（站名/关于/SEO 默认） | **建议新增**（portal 首页/关于页需要） |

### 1.2b OSS 上传登记（已有表 `sys_file`，不另建）

> **原则：凡写入 MinIO 的对象，必须先在 `sys_file` 登记一行上传信息；业务表只持 `file_id`（及必要的冗余 object_key）。**

| 用途 | `biz_type` | 谁写 | 业务引用 |
|------|------------|------|----------|
| 管理端通用附件 | `FILE` | 文件管理 | 任意 |
| 文章封面 | `ARTICLE_COVER` | 封面上传 | `blog_article.cover_file_id` |
| 文章**线上正文** | `ARTICLE_LIVE` | 保存草稿→发布提升 / 发布写入 | `blog_article_detail.content_file_id` |
| 文章**草稿正文** | `ARTICLE_DRAFT` | 保存草稿 | `blog_article_detail.draft_file_id` |

上传信息字段见 **§2.0 `sys_file`（修改）**：桶、object_key、原始名、MIME、大小、biz 关联、操作人/时间。

### 1.3 表设计还要补齐 / 裁决的点

| # | 项 | 建议取值 | 影响 |
|---|----|----------|------|
| 1 | 文章是否要 `slug` 友好 URL | **要**：`uk_blog_article_slug` | 用户端 `/articles/:slug`；空则用 id |
| 2 | 分类层级 | **最多 2 级**（根 + 子） | `parent_id`，应用层校验 |
| 3 | 标签冗余文章数 | **不冗余** | 列表 JOIN 或子查询；避免计数不一致 |
| 4 | 正文是否进大字段 | **否**，只存 OSS `object_key` | detail 表 |
| 5 | 封面 | `cover_file_id` → `sys_file` | 复用文件管理上传 |
| 6 | `view_count` | **预留字段，本期无逻辑** | 避免后续加列 |
| 7 | 正文大小上限 | **500KB** | 应用层校验，错误码「正文过大」 |
| 8 | `blog_site_config` 是否要 | **要**（单行配置表） | portal 标题/关于/备案号 |
| 9 | 密码保护文章 / 转载链接 | **本期不做** | 开放问题 |
| 10 | 版本草稿历史表 | **本期不做** | 覆盖保存即可 |

---

## 2. 新增表结构

### 2.0 `sys_file`（修改）· OSS 对象上传登记表

> 通用形态见 `docs/template/object-storage-design.md` §5。本节只登记 **blog 侧 DDL 增量**与业务取值。

**既有列（保留）：** `id` / `bucket` / `object_key` / `original_name` / `content_type` / `size_bytes` / 审计 5 列 / `deleted`

**新增列：**

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| biz_type | VARCHAR(32) | N | 'FILE' | 对象用途：`FILE` / `ARTICLE_COVER` / `ARTICLE_LIVE` / `ARTICLE_DRAFT` |
| biz_id | BIGINT | N | 0 | 业务 id（如 article_id）；非业务为 0 |

**索引（在现有 ≤5 约束内调整）：**

- 保留 `uk_sys_file_object_key (object_key)`
- 保留 `idx_sys_file_create_time (create_time)`
- **新增** `idx_sys_file_biz (biz_type, biz_id)`

**上传信息登记时机（强制）：**

```
业务要写 OSS
  → put 对象成功
  → INSERT sys_file（bucket, object_key, original_name, content_type, size_bytes, biz_type, biz_id, create_by…）
  → 再把 file_id 写入业务表（cover_file_id / content_file_id / draft_file_id）
```

- 禁止只写 MinIO 不落 `sys_file`；禁止业务表只存裸 `object_key` 而无 `sys_file` 行
- 逻辑删除 `deleted=1` 即文件删除语义（对象清理运维脚本，不建定时）
- 文件管理列表可按 `biz_type` 过滤；预签名/下载经 file_id 解析

### 2.1 `blog_category` 文章分类

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | 发号 | 主键 16 位 |
| parent_id | BIGINT | N | 0 | 父分类；0=根。**应用校验最多 2 级** |
| name | VARCHAR(64) | N | | 分类名 |
| slug | VARCHAR(64) | N | | URL 标识，全局唯一 |
| description | VARCHAR(255) | N | '' | 描述 |
| sort | INT | N | 0 | 升序 |
| status | TINYINT | N | 1 | 1 启用 / 0 停用 |
| create_time | DATETIME | N | | 审计 |
| update_time | DATETIME | N | | 审计 |
| create_by | BIGINT | N | 0 | 审计 |
| update_by | BIGINT | N | 0 | 审计 |
| deleted | TINYINT | N | 0 | 逻辑删除 |

**索引：**

- `uk_blog_category_slug (slug)`
- `idx_blog_category_parent_id (parent_id)`
- `idx_blog_category_status (status)`

**约束（应用层）：** 同级 `name` 不重复；删除须无子分类且无文章引用。

---

### 2.2 `blog_tag` 文章标签

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | 发号 | 主键 |
| name | VARCHAR(64) | N | | 标签名，全局唯一 |
| slug | VARCHAR(64) | N | | URL 标识 |
| sort | INT | N | 0 | 排序 |
| status | TINYINT | N | 1 | 1 启用 / 0 停用 |
| create_time / update_time / create_by / update_by / deleted | | | | 审计 |

**索引：**

- `uk_blog_tag_name (name)`
- `uk_blog_tag_slug (slug)`
- `idx_blog_tag_status (status)`

**约束：** 有 `blog_article_tag` 关联则不可删。

---

### 2.3 `blog_article` 文章主表

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | 发号 | 主键 |
| slug | VARCHAR(120) | N | '' | 友好 URL；空则用 id 访问；有则唯一 |
| category_id | BIGINT | N | 0 | 分类；0=未分类 |
| title | VARCHAR(200) | N | | 标题 |
| summary | VARCHAR(500) | N | '' | 摘要（列表） |
| cover_file_id | BIGINT | N | 0 | 封面 → `sys_file.id` |
| author_id | BIGINT | N | 0 | 作者 id（`X-User-Id` 快照） |
| author_name | VARCHAR(64) | N | '' | 作者显示名快照 |
| status | TINYINT | N | 0 | **0 草稿 / 1 已发布 / 2 已下架** |
| publish_time | DATETIME | NULL | NULL | 最近发布时间 |
| is_top | TINYINT | N | 0 | 1 置顶 |
| view_count | BIGINT | N | 0 | **预留**，本期不写 |
| create_time / update_time / create_by / update_by / deleted | | | | 审计 |

**索引：**

- `uk_blog_article_slug (slug)`（slug 可为空串；建议唯一索引对空串用生成值或应用保证非空时唯一）
- `idx_blog_article_category_id (category_id)`
- `idx_blog_article_status_pub (status, publish_time)`
- `idx_blog_article_author_id (author_id)`
- `idx_blog_article_title (title)`

**状态机：** 草稿 →（发布）→ 已发布 →（下架）→ 已下架 →（发布）→ 已发布；仅草稿/已下架可逻辑删。

**写入顺序（与 business-design §3.3 一致）：**

1. **新建（两页）**：先 INSERT `blog_article` 得 id → **跳转写正文页** → put OSS + **登记 `sys_file`** → 写 `blog_article_detail`
2. **编辑（两页分离）**：编辑主表页只碰主表（封面也先登记 sys_file）；写正文页 `mode=draft` 只写 **draft 轨**（新 sys_file 行 + `draft_file_id`）
3. **已发布再改**：保存草稿**不**改 `content_file_id`、**不**改 `status`；`publish` 才 draft→线上（新 ARTICLE_LIVE 行）
4. 发布前须有正文 file（draft 或线上）；已发布不可直接删

---

### 2.4 `blog_article_detail` 文章详细表

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | 发号 | 主键 |
| article_id | BIGINT | N | | 1:1 主表 |
| content_file_id | BIGINT | N | 0 | **线上正文** → `sys_file.id`（biz_type=ARTICLE_LIVE） |
| draft_file_id | BIGINT | N | 0 | **草稿正文** → `sys_file.id`（biz_type=ARTICLE_DRAFT）；0=无草稿 |
| content_format | VARCHAR(16) | N | 'markdown' | 线上正文格式 |
| word_count | INT | N | 0 | 线上正文字数 |
| draft_content_format | VARCHAR(16) | N | 'markdown' | 草稿格式 |
| draft_word_count | INT | N | 0 | 草稿字数 |
| seo_title | VARCHAR(200) | N | '' | 线上 SEO（发布时生效） |
| seo_description | VARCHAR(300) | N | '' | 线上 SEO |
| draft_seo_title | VARCHAR(200) | N | '' | 草稿 SEO |
| draft_seo_description | VARCHAR(300) | N | '' | 草稿 SEO |
| create_time / update_time / create_by / update_by / deleted | | | | 审计 |

**索引：** `uk_blog_article_detail_article_id (article_id)`

**与 `sys_file` 的关系（正文双轨）：**

| 业务字段 | sys_file | 谁读 | 谁写 |
|----------|----------|------|------|
| `content_file_id` | `biz_type=ARTICLE_LIVE`，`biz_id=article_id` | 用户端（经 file 取 object_key） | **仅发布**（draft 提升或首发写入） |
| `draft_file_id` | `biz_type=ARTICLE_DRAFT`，`biz_id=article_id` | 管理端编辑器 | 「保存草稿」 |
| （封面）`blog_article.cover_file_id` | `biz_type=ARTICLE_COVER` | 列表/详情 | 封面上传 |

**说明：**

- **不存**正文大字段；正文在 OSS，`sys_file.object_key` 为对象定位真相
- 可读路径：`content_file_id → sys_file → object_key → MinIO`（可冗余 cache object_key，非必须）
- 对象键建议：`blog-content/{articleId}/live/{uuid}.md`、`blog-content/{articleId}/draft/{uuid}.md`
- **保存草稿不动 `content_file_id`**；发布时：新 live 行登记 → `content_file_id` 指向新行 → `draft_file_id=0`

---

### 2.5 `blog_article_tag` 文章-标签

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | 发号 | 主键 |
| article_id | BIGINT | N | | 文章 id |
| tag_id | BIGINT | N | | 标签 id |
| create_time / update_time / create_by / update_by / deleted | | | | 审计 |

**索引：**

- `uk_blog_article_tag_at (article_id, tag_id)`
- `idx_blog_article_tag_tag_id (tag_id)`

**约束：** 保存文章标签时同事务「删旧插新」。

---

### 2.6 `blog_site_config` 用户端站点配置（建议新增）

> 单行配置（id=1）或 `config_key` 多行；推荐 **`config_key` 多行** 便于扩展。

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | 发号 | 主键 |
| config_key | VARCHAR(64) | N | | 配置键 |
| config_value | VARCHAR(1024) | N | '' | 配置值 |
| remark | VARCHAR(255) | N | '' | 说明 |
| create_time / update_time / create_by / update_by / deleted | | | | 审计 |

**索引：** `uk_blog_site_config_key (config_key)`

**种子键（实施 INSERT）：**

| config_key | 用途 |
|------------|------|
| `site.title` | 站点标题 |
| `site.description` | 站点描述 |
| `site.copyright` | 备案/版权 |
| `site.about` | 关于正文（短文本；长文可再挂 OSS） |

---

## 3. 关系一览

```text
sys_file（OSS 上传登记） 1 ──── * 业务引用
  ├─ blog_article.cover_file_id        (ARTICLE_COVER)
  ├─ blog_article_detail.content_file_id (ARTICLE_LIVE)
  └─ blog_article_detail.draft_file_id   (ARTICLE_DRAFT)

blog_category 1 ──── N blog_article
blog_tag      M ──── N blog_article   （经 blog_article_tag）
blog_article  1 ──── 1 blog_article_detail
sys_file.object_key ──► MinIO 对象
blog_site_config     独立
```

详见 [er-diagram.html](./er-diagram.html)。

---

## 4. 字段裁决说明

| 决策 | 取舍 | 原因 |
|------|------|------|
| 主表 / 详细表拆分 | 拆 | 列表不碰正文元数据与 SEO 字段；OSS 只存全文 |
| 正文进 OSS 不进 DB | OSS | 用户明确要求；大字段拖垮列表与备份 |
| 封面进 `sys_file` | 复用 | 已有文件管理与预签名；`biz_type=ARTICLE_COVER` |
| **正文/草稿进 `sys_file`** | **登记** | OSS 上传信息统一表；detail 只持 `content_file_id`/`draft_file_id` |
| 不建平行 OSS 表 | 复用 `sys_file` | 对齐 `object-storage-design.md` §5；避免双源 |
| 作者快照 author_name | 快照 | 避免列表 Feign 用户中心；换名不追溯 |
| `view_count` 预留 | 预留 | 本期不做统计；避免后续加列 |
| 无评论/点赞表 | 不做 | 项目边界；需要时另开变更 |
| 无文章版本表 | 不做 | 覆盖保存；历史走 OSS 旧对象 + 审计 |
| `blog_site_config` | 新增 | portal 标题/关于/备案无处安放 |

---

## 5. 种子与迁移

| 类型 | 位置 | 说明 |
|------|------|------|
| 建表 SQL | `deploy/db/migration/blog_db/03_blog_content_tables.sql` | 实施时提供；人工执行 |
| 站点配置种子 | `deploy/db/seed/` | `INSERT IGNORE` |
| 菜单权限种子 | `deploy/db/seed/` | 内容管理菜单 + `blog:category:*` / `blog:tag:*` / `blog:article:*` |
| 默认分类 | 可选 | 「未分类」slug=`uncategorized` |

**不引入** Flyway/Liquibase（`CLAUDE.md` §2.2）。

---

## 6. 开放问题

| # | 问题 | 默认 |
|---|------|------|
| 1 | slug 冲突/空值策略 | 空→用 id；非空→唯一，冲突报校验错 |
| 2 | `blog_site_config.site.about` 是否也上 OSS | 先 VARCHAR；超长再挂对象 |
| 3 | 是否允许未分类发布 | 允许，`category_id=0` |
| 4 | 同步更新 tag 冗余计数 | 不做 |

---

## 7. 与其它文档

| 本文 | 不放 |
|------|------|
| 表/字段/索引/状态/种子键 | 完整实现 SQL 可执行脚本（实施时） |
| 新增/修改结论 | API 详情（见 business-design.md） |
| | 生产密钥、端口账密 |
