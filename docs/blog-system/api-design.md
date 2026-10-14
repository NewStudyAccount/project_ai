# blog-system · 接口文档

> 状态：**设计稿** · 与 [table-design.md](./table-design.md) / [business-design.md](./business-design.md) / [prototype.html](./prototype.html) 对齐  
> 契约口径：`CLAUDE.md` §6.2 / §6.4（返回体、错误码、分页、Long→String、校验）  
> 鉴权：纯网关（`X-User-Id` / `X-User-Name`）；权限码 `blog:xxx:yyy`

---

## 0. 全局约定

| 项 | 约定 |
|----|------|
| 前缀 | `/api/v1` |
| 返回体 | `{ "code": number, "msg": string, "data": T }` |
| 分页出参 | `{ "records": T[], "total": number, "size": number, "current": number }` |
| id 类型 | 出参一律 **string**（16 位发号字符串化） |
| 业务失败 | HTTP **200** + `code` 非 0；未登录 401；无权限 403；限流 429 |
| 内容类型 | `application/json`（正文上传也走 JSON 字符串） |
| 幂等 | 标注 **幂等** 的写接口须带 `Idempotent-Key`（或 `X-Request-Id`） |
| 时间 | `yyyy-MM-dd HH:mm:ss`，时区 `Asia/Shanghai` |

**错误码语义（业务段 `2xxxxx` 实施时分配）：**

| 场景 | 语义 |
|------|------|
| 文章不存在 | ARTICLE_NOT_FOUND |
| 文章未发布（用户端） | ARTICLE_NOT_FOUND（不暴露草稿） |
| 无正文不可发布 | CONTENT_REQUIRED |
| 正文过大（>500KB） | CONTENT_TOO_LARGE |
| 分类下存在子类/文章 | CATEGORY_IN_USE |
| 标签被引用 | TAG_IN_USE |
| slug 冲突 | SLUG_DUPLICATE |
| 无草稿可放弃 | DRAFT_NOT_FOUND |
| 已发布不可直接删 | ARTICLE_PUBLISHED |

---

## 1. 管理端 · 分类 `blog:category:*`

### 1.1 分类树查询

| 项 | 内容 |
|----|------|
| **接口名** | 查询分类树 |
| **方法/路径** | `GET /api/v1/admin/categories` |
| **权限** | `blog:category:list` |

**入参（Query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | int | 否 | 1 启用 / 0 停用；空=全部 |
| name | string | 否 | 名称模糊 |

**出参 `data`：CategoryTreeVO[]**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 分类 id |
| parentId | string | 父 id，0=根 |
| name | string | 名称 |
| slug | string | URL 标识 |
| description | string | 描述 |
| sort | number | 排序 |
| status | number | 1/0 |
| articleCount | number | 关联文章数（可选统计） |
| children | CategoryTreeVO[] | 子分类 |

**接口逻辑**

1. 查 `blog_category`（`deleted=0`，可选 status/name）
2. 组装树（最多 2 级）
3. （可选）按 `blog_article.category_id` 聚合文章数

**使用表**：`blog_category`；（可选）`blog_article`

---

### 1.2 创建分类

| 项 | 内容 |
|----|------|
| **接口名** | 创建分类 |
| **方法/路径** | `POST /api/v1/admin/categories` |
| **权限** | `blog:category:create` |
| **幂等** | 是 |

**入参（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| parentId | string | 否 | 默认 0 |
| name | string | 是 | ≤64 |
| slug | string | 是 | ≤64，全局唯一 |
| description | string | 否 | ≤255 |
| sort | number | 否 | 默认 0 |
| status | number | 否 | 默认 1 |

**出参 `data`：CategoryVO**（同树节点，无 children）

**接口逻辑**

1. 校验：parent 存在；parent 仅允许根或 1 级（≤2 级）；slug 唯一；同级 name 不重复
2. INSERT `blog_category`（框架发号 + 审计）
3. 审计 `target_type=CATEGORY`

**使用表**：`blog_category`、`blog_audit_log`

---

### 1.3 更新分类

| 项 | 内容 |
|----|------|
| **接口名** | 更新分类 |
| **方法/路径** | `PUT /api/v1/admin/categories/{id}` |
| **权限** | `blog:category:update` |

**入参（Body）**：同创建；`id` 在路径

**接口逻辑**

1. 分类存在且 `deleted=0`
2. 不可把节点挂到自己/子孙下；层级仍 ≤2
3. slug/name 冲突校验
4. UPDATE `blog_category`；审计

**使用表**：`blog_category`、`blog_audit_log`

---

### 1.4 删除分类

| 项 | 内容 |
|----|------|
| **接口名** | 删除分类 |
| **方法/路径** | `DELETE /api/v1/admin/categories/{id}` |
| **权限** | `blog:category:delete` |
| **幂等** | 是 |

**入参**：路径 `id`

**出参 `data`：`null`**

**接口逻辑**

1. 存在子分类 → 业务错 `CATEGORY_IN_USE`
2. 存在文章引用 → 业务错 `CATEGORY_IN_USE`
3. 逻辑删 `deleted=1`；审计

**使用表**：`blog_category`、`blog_article`、`blog_audit_log`

---

## 2. 管理端 · 标签 `blog:tag:*`

### 2.1 标签分页

| 项 | 内容 |
|----|------|
| **接口名** | 标签分页查询 |
| **方法/路径** | `GET /api/v1/admin/tags` |
| **权限** | `blog:tag:list` |

**入参（Query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| current | long | 否 | 默认 1 |
| size | long | 否 | 默认 10 |
| name | string | 否 | 模糊 |
| status | int | 否 | |

**出参 `data`：PageResult\<TagVO\>**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | |
| name | string | |
| slug | string | |
| sort | number | |
| status | number | |
| articleCount | number | 引用数 |

**接口逻辑**：查 `blog_tag`；LEFT JOIN 统计 `blog_article_tag`

**使用表**：`blog_tag`、`blog_article_tag`

---

### 2.2 创建标签

| 项 | 内容 |
|----|------|
| **接口名** | 创建标签 |
| **方法/路径** | `POST /api/v1/admin/tags` |
| **权限** | `blog:tag:create` |
| **幂等** | 是 |

**入参**：`name*`、`slug*`、`sort?`、`status?`

**出参**：TagVO

**逻辑**：name/slug 唯一校验 → INSERT → 审计

**使用表**：`blog_tag`、`blog_audit_log`

---

### 2.3 更新标签

`PUT /api/v1/admin/tags/{id}` · `blog:tag:update` · 入参同创建 · 校验冲突后 UPDATE · 审计

**使用表**：`blog_tag`、`blog_audit_log`

---

### 2.4 删除标签

`DELETE /api/v1/admin/tags/{id}` · `blog:tag:delete` · 幂等

**逻辑**：`blog_article_tag` 有引用则 `TAG_IN_USE`；否则逻辑删 + 审计

**使用表**：`blog_tag`、`blog_article_tag`、`blog_audit_log`

---

## 3. 管理端 · 文章主表 `blog:article:*`

### 3.1 文章分页

| 项 | 内容 |
|----|------|
| **接口名** | 文章分页查询 |
| **方法/路径** | `GET /api/v1/admin/articles` |
| **权限** | `blog:article:list` |

**入参（Query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| current / size | long | 否 | 分页 |
| keyword | string | 否 | 标题 LIKE |
| categoryId | string | 否 | 含下级可扩展 |
| tagId | string | 否 | |
| status | int | 否 | 0/1/2 |
| orderBy | string | 否 | 白名单：`publish_time` \| `is_top` \| `create_time` |
| order | string | 否 | `asc` \| `desc`（默认 desc） |

**出参 `data`：PageResult\<ArticleVO\>**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | |
| title | string | |
| slug | string | |
| summary | string | |
| coverFileId | string | 0=无 |
| categoryId / categoryName | string | |
| tags | {id,name}[] | |
| authorId / authorName | string | 快照 |
| status | number | 0/1/2 |
| publishTime | string? | |
| isTop | number | |
| hasDraft | boolean | 是否有未发布草稿 |
| createTime / updateTime | string | |

**接口逻辑**

1. WHERE `deleted=0` + 筛选
2. 关联 `blog_category` 名称、`blog_article_tag` + `blog_tag`
3. `hasDraft` = `detail.draft_file_id > 0`
4. **不**读 OSS 正文

**使用表**：`blog_article`、`blog_category`、`blog_article_tag`、`blog_tag`、`blog_article_detail`

---

### 3.2 新建文章主表

| 项 | 内容 |
|----|------|
| **接口名** | 新建文章主表 |
| **方法/路径** | `POST /api/v1/admin/articles` |
| **权限** | `blog:article:create` |
| **幂等** | 是 |
| **说明** | **只建主表**，不含正文；成功后前端跳「写正文」 |

**入参（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | string | 是 | ≤200 |
| slug | string | 否 | ≤120；空则空串（访问用 id） |
| categoryId | string | 否 | 默认 0 |
| summary | string | 否 | ≤500 |
| coverFileId | string | 否 | 已上传的 sys_file id |
| tagIds | string[] | 否 | |
| isTop | number | 否 | 0/1 |

**出参 `data`：ArticleDetailVO**（无 content；status=0）

**接口逻辑**

1. 校验 title/slug/category 存在/cover 为 `ARTICLE_COVER` 或 `FILE` 且未删
2. INSERT `blog_article`（status=0，author 取 `UserContext`）
3. UPSERT `blog_article_detail` 空行（可选）
4. 整组写 `blog_article_tag`
5. 审计 ARTICLE_CREATE

**使用表**：`blog_article`、`blog_article_detail`、`blog_article_tag`、`blog_category`、`sys_file`、`blog_audit_log`

---

### 3.3 文章详情（管理）

| 项 | 内容 |
|----|------|
| **接口名** | 文章详情（含草稿正文） |
| **方法/路径** | `GET /api/v1/admin/articles/{id}` |
| **权限** | `blog:article:detail` |

**入参**：路径 `id`

**出参 `data`：ArticleAdminDetailVO**

| 字段 | 类型 | 说明 |
|------|------|------|
| （主表字段） | | 同 ArticleVO |
| content | string | **当前编辑器内容**：有草稿读草稿正文，否则线上正文，否则空 |
| contentFrom | string | `draft` \| `live` \| `none` |
| contentFormat | string | |
| wordCount | number | 对应轨字数 |
| seoTitle / seoDescription | string | 对应轨 SEO |
| draftFileId / contentFileId | string | |
| hasDraft | boolean | |

**接口逻辑**

1. 查 `blog_article`（须 `deleted=0`）
2. 查 `blog_article_detail`
3. 若 `draft_file_id>0`：`sys_file` → object_key → MinIO 读草稿；`contentFrom=draft`
4. 否则若 `content_file_id>0`：读线上；`contentFrom=live`
5. 标签列表

**使用表**：`blog_article`、`blog_article_detail`、`sys_file`、`blog_article_tag`、`blog_tag`

---

### 3.4 更新文章主表

| 项 | 内容 |
|----|------|
| **接口名** | 更新文章主表 |
| **方法/路径** | `PUT /api/v1/admin/articles/{id}` |
| **权限** | `blog:article:update` |
| **说明** | **只改主表/标签**，不碰正文双轨 |

**入参**：同 3.2 Body

**出参**：ArticleVO

**接口逻辑**

1. 文章存在；slug 冲突校验
2. UPDATE `blog_article`；替换 `blog_article_tag`
3. **不**改 `content_file_id` / `draft_file_id` / status
4. 审计 ARTICLE_UPDATE

**使用表**：`blog_article`、`blog_article_tag`、`blog_category`、`sys_file`、`blog_audit_log`

---

## 4. 管理端 · 正文（详细表 + OSS）`blog:article:update`

### 4.1 保存正文（草稿 / 发布）

| 项 | 内容 |
|----|------|
| **接口名** | 保存正文 |
| **方法/路径** | `PUT /api/v1/admin/articles/{id}/content` |
| **权限** | `blog:article:update` |
| **幂等** | 是 |

**入参（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| mode | string | 是 | `draft` \| `publish` |
| content | string | 是 | 正文，≤500KB |
| contentFormat | string | 否 | `markdown` \| `html`，默认 markdown |
| seoTitle | string | 否 | ≤200 |
| seoDescription | string | 否 | ≤300 |

**出参 `data`**

| 字段 | 类型 | 说明 |
|------|------|------|
| draftFileId | string | mode=draft 后草稿文件 id |
| contentFileId | string | mode=publish 后线上文件 id |
| hasDraft | boolean | |
| status | number | 文章状态 |

**接口逻辑**

1. 文章存在；`content` 非空且 ≤500KB
2. **put** MinIO 对象（`blog-content/{id}/draft|live/{uuid}.{md|html}`）
3. **INSERT `sys_file`**（biz_type=ARTICLE_DRAFT 或 ARTICLE_LIVE，biz_id=文章 id）
4. **mode=draft**：
   - UPDATE detail：`draft_file_id` / `draft_content_format` / `draft_word_count` / `draft_seo_*`
   - **不**改 `content_file_id`、**不**改 `status`
   - 旧 draft sys_file 逻辑删
5. **mode=publish**：
   - 同 draft 写入后立即执行发布提升（见 5.1）；或等价：live 登记 + `content_file_id` 切换 + `draft_file_id=0` + `status=1` + `publish_time`
6. 审计 ARTICLE_CONTENT_UPDATE

**使用表**：`blog_article`、`blog_article_detail`、`sys_file`、`blog_audit_log` + **MinIO**

---

### 4.2 放弃草稿

| 项 | 内容 |
|----|------|
| **接口名** | 放弃草稿 |
| **方法/路径** | `DELETE /api/v1/admin/articles/{id}/draft` |
| **权限** | `blog:article:update` |
| **幂等** | 是 |

**入参**：路径 `id`

**出参**：`{ hasDraft: false, contentFileId, status }`

**接口逻辑**

1. `detail.draft_file_id=0` → `DRAFT_NOT_FOUND`
2. 对应 `sys_file` 逻辑删（ARTICLE_DRAFT）
3. detail 草稿字段清零
4. **不**改线上与 status；审计

**使用表**：`blog_article_detail`、`sys_file`、`blog_audit_log`

---

## 5. 管理端 · 生命周期 `blog:article:publish` / `delete`

### 5.1 发布（草稿→线上）

| 项 | 内容 |
|----|------|
| **接口名** | 发布 / 更新发布 |
| **方法/路径** | `POST /api/v1/admin/articles/{id}/publish` |
| **权限** | `blog:article:publish` |
| **幂等** | 是 |

**入参**：路径 `id`

**出参**：`{ status: 1, publishTime, contentFileId, hasDraft: false }`

**接口逻辑**

1. 文章存在
2. 有 `draft_file_id`：
   - 将草稿对象 **提升/复制** 为 live 对象（新 object_key）
   - INSERT/更新 `sys_file` biz_type=ARTICLE_LIVE
   - `content_file_id` ← 新 live；`draft_file_id=0`；`seo_*` ← `draft_seo_*`
   - 旧 live / 旧 draft `sys_file` 逻辑删
3. 无草稿但已有 `content_file_id`：仅置 `status=1`、`publish_time=now`（重复发布/下架后再发）
4. 两者皆无 → `CONTENT_REQUIRED`
5. `status=1`，`publish_time=now`；审计 ARTICLE_PUBLISH

**使用表**：`blog_article`、`blog_article_detail`、`sys_file`、`blog_audit_log` + **MinIO**

---

### 5.2 下架

| 项 | 内容 |
|----|------|
| **接口名** | 下架文章 |
| **方法/路径** | `POST /api/v1/admin/articles/{id}/offline` |
| **权限** | `blog:article:publish` |
| **幂等** | 是 |

**入参**：路径 `id`

**出参**：`{ status: 2 }`

**接口逻辑**：须已 `status=1` → 置 `status=2`；**保留** `content_file_id`；审计 ARTICLE_OFFLINE

**使用表**：`blog_article`、`blog_audit_log`

---

### 5.3 删除文章

| 项 | 内容 |
|----|------|
| **接口名** | 删除文章（逻辑） |
| **方法/路径** | `DELETE /api/v1/admin/articles/{id}` |
| **权限** | `blog:article:delete` |
| **幂等** | 是 |

**入参**：路径 `id`

**出参**：`null`

**接口逻辑**

1. `status=1` → `ARTICLE_PUBLISHED`（须先下架）
2. `blog_article.deleted=1`；关联 detail/tag 逻辑删
3. 相关 `sys_file` 逻辑删（COVER/LIVE/DRAFT）
4. 审计 ARTICLE_DELETE

**使用表**：`blog_article`、`blog_article_detail`、`blog_article_tag`、`sys_file`、`blog_audit_log`

---

## 6. 用户端公开读 `/api/v1/public/**`

> 网关放行；无登录；限流 `@RateLimit`；**只读 `status=1 AND deleted=0`**

### 6.1 启用分类树

| 项 | 内容 |
|----|------|
| **接口名** | 用户端分类树 |
| **方法/路径** | `GET /api/v1/public/categories` |
| **权限** | 公开 |

**入参**：无

**出参**：CategoryTreeVO[]（仅 status=1；可带 articleCount）

**逻辑**：查启用分类 → 组树 → （可选）已发布文章计数

**使用表**：`blog_category`、`blog_article`

---

### 6.2 启用标签

`GET /api/v1/public/tags` · 公开

**出参**：`{ id, name, slug, articleCount }[]`（仅启用标签；计数仅已发布文章）

**使用表**：`blog_tag`、`blog_article_tag`、`blog_article`

---

### 6.3 文章列表

| 项 | 内容 |
|----|------|
| **接口名** | 用户端文章分页 |
| **方法/路径** | `GET /api/v1/public/articles` |
| **权限** | 公开 |

**入参（Query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| current / size | long | 否 | |
| categoryId | string | 否 | |
| tagId | string | 否 | |
| keyword | string | 否 | 标题 LIKE |
| orderBy | string | 否 | `publish_time` \| `is_top` \| `create_time` |

**出参**：PageResult\<ArticleVO\>（无 status 内部态；无 hasDraft）

**逻辑**

1. WHERE `status=1 AND deleted=0` + 筛选
2. ORDER `is_top DESC, publish_time DESC`
3. 组分类名/标签；**不加载正文**

**使用表**：`blog_article`、`blog_category`、`blog_article_tag`、`blog_tag`

---

### 6.4 文章详情

| 项 | 内容 |
|----|------|
| **接口名** | 用户端文章详情 |
| **方法/路径** | `GET /api/v1/public/articles/{id}` |
| **权限** | 公开 |

**入参**：路径 `id`（或 `slug` 由路由层解析）

**出参 `data`：ArticleDetailVO**

| 字段 | 类型 | 说明 |
|------|------|------|
| 主表字段 | | 同 ArticleVO |
| content | string | **线上正文**（content_file_id → sys_file → MinIO） |
| contentFormat / wordCount | | |
| seoTitle / seoDescription | | 空则回退 title/summary |

**逻辑**

1. `status=1 AND deleted=0`，否则按「不存在」处理
2. detail.`content_file_id` → sys_file(ARTICLE_LIVE) → 读对象
3. **忽略** `draft_file_id`
4. 审计可选 view（本期 view_count 不写）

**使用表**：`blog_article`、`blog_article_detail`、`sys_file`、`blog_article_tag`、`blog_tag` + **MinIO**

---

### 6.5 正文预签名（可选）

| 项 | 内容 |
|----|------|
| **接口名** | 正文预签名 URL |
| **方法/路径** | `GET /api/v1/public/articles/{id}/content-url` |
| **权限** | 公开 |

**出参**：`{ url: string, expiresAt: string }`

**逻辑**：仅已发布 + `content_file_id` → `presignGet` 短 TTL（配置项）

**使用表**：`blog_article`、`blog_article_detail`、`sys_file` + **MinIO**

---

## 7. 站点配置（公开）

### 7.1 站点配置查询

| 项 | 内容 |
|----|------|
| **接口名** | 站点配置 |
| **方法/路径** | `GET /api/v1/public/site-config` |
| **权限** | 公开 |

**出参**：`{ title, description, copyright, about }`

**逻辑**：读 `blog_site_config` 预置键；缓存可选（TTL）

**使用表**：`blog_site_config`

### 7.2 站点配置保存（管理）

`PUT /api/v1/admin/site-config` · `blog:article:update` 或独立 `blog:site:update` · Body 为键值 · UPSERT `blog_site_config` · 审计

**使用表**：`blog_site_config`、`blog_audit_log`

---

## 8. 对象上传（封面 / 通用，既有能力扩展）

> 完整文件管理见 scaffold `blog-file`；此处仅补与文章相关用法。

### 8.1 上传封面

| 项 | 内容 |
|----|------|
| **接口名** | 上传文件（封面） |
| **方法/路径** | `POST /api/v1/admin/files`（multipart） |
| **权限** | `blog:article:create` 或 `blog:file:upload` |

**入参**：`file` 二进制；`bizType=ARTICLE_COVER`（可选）

**出参**：`FileVO { id, bucket, objectKey, originalName, contentType, sizeBytes }`

**逻辑**：类型/大小校验 → put MinIO → **INSERT sys_file**（biz_type）→ 返回 id 供 3.2/3.4 填 `coverFileId`

**使用表**：`sys_file`、`blog_audit_log` + **MinIO**

---

## 9. 接口总表

| # | 方法 | 路径 | 权限 | 主要表 |
|---|------|------|------|--------|
| 1 | GET | /admin/categories | category:list | blog_category, blog_article |
| 2 | POST | /admin/categories | category:create | blog_category |
| 3 | PUT | /admin/categories/{id} | category:update | blog_category |
| 4 | DELETE | /admin/categories/{id} | category:delete | blog_category, blog_article |
| 5 | GET | /admin/tags | tag:list | blog_tag, blog_article_tag |
| 6 | POST | /admin/tags | tag:create | blog_tag |
| 7 | PUT | /admin/tags/{id} | tag:update | blog_tag |
| 8 | DELETE | /admin/tags/{id} | tag:delete | blog_tag, blog_article_tag |
| 9 | GET | /admin/articles | article:list | blog_article* + detail |
| 10 | POST | /admin/articles | article:create | blog_article, detail, tag, sys_file |
| 11 | GET | /admin/articles/{id} | article:detail | article, detail, sys_file |
| 12 | PUT | /admin/articles/{id} | article:update | blog_article, tag |
| 13 | PUT | /admin/articles/{id}/content | article:update | detail, sys_file, MinIO |
| 14 | DELETE | /admin/articles/{id}/draft | article:update | detail, sys_file |
| 15 | POST | /admin/articles/{id}/publish | article:publish | article, detail, sys_file, MinIO |
| 16 | POST | /admin/articles/{id}/offline | article:publish | blog_article |
| 17 | DELETE | /admin/articles/{id} | article:delete | article*, sys_file |
| 18 | GET | /public/categories | 公开 | blog_category, blog_article |
| 19 | GET | /public/tags | 公开 | blog_tag, blog_article_tag, blog_article |
| 20 | GET | /public/articles | 公开 | blog_article, category, tag |
| 21 | GET | /public/articles/{id} | 公开 | article, detail, sys_file, MinIO |
| 22 | GET | /public/articles/{id}/content-url | 公开 | 同上 |
| 23 | GET | /public/site-config | 公开 | blog_site_config |
| 24 | PUT | /admin/site-config | site:update | blog_site_config |
| 25 | POST | /admin/files | file:upload | sys_file, MinIO |

`blog_article*` = `blog_article` + `blog_article_detail` + `blog_article_tag` 等。

---

## 10. 调用顺序（新建一篇）

```text
POST /admin/files (封面，可选)
  → POST /admin/articles          // 主表
  → PUT  /admin/articles/{id}/content  mode=draft
  → POST /admin/articles/{id}/publish  // 或 content mode=publish
```

列表改稿：

```text
GET /admin/articles/{id}          // contentFrom=draft|live
  → PUT …/content mode=draft      // 线上不动
  → POST …/publish                // 切换线上
```

---

## 11. 与其它文档

| 本文 | 不放 |
|------|------|
| 接口名/入出参/逻辑/表 | 前端组件细节（见 prototype） |
| 全局约定与错误语义 | 库表字段全表（见 table-design） |
| | 实现代码、生产密钥 |

实施时接口注解（`@Operation` / `@Schema`）以后端 Controller 为准；本文件为业务接口契约设计稿。
