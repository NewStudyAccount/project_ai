# Blog 系统设计文档（结构 · 版本 · 表 · 业务）

| 项 | 值 |
|----|-----|
| 范围 | `system_code=blog`、平台 `file` 服务、与统一认证/本地 RBAC 的衔接 |
| 形态 | 个人/团队博客；前台**匿名可读**；评论需**审核** |
| 对应变更 | `add-file-service` → `add-blog-foundation-rbac` → `add-blog-content-modules` |
| 上游 | `add-unified-auth-center`、`add-foundation-db-schema` |

---

## 1. 定位与边界

| 决策 | 内容 |
|------|------|
| 产品 | 个人/团队博客（非社交社区一期） |
| system_code | `blog` |
| 前台 | 匿名公开读；写操作需登录 + 权限码 |
| 分类 | **扁平** |
| 正文 | **MD 原文存库**；**内容文件（md 副本/html 成品/图片）进 OSS** |
| 评论 | 有；**待审 / 通过 / 驳回** |
| 检索 | 本期**不做 ES** |
| 编辑器 | **md-editor-v3** |
| 文件 | **F2：独立平台 file 服务**（非埋在 blog 内） |

**不做（一期）：** 注册开放投稿、关注点赞、全文检索 ES、定时发布、图片水印/缩略图流水线。

---

## 2. 系统结构与模块

```
frontend/
  auth-portal/                 # 已有
  blog-admin/                  # 写作、分类标签、评论审核
  blog-portal/                 # 匿名阅读、评论提交

backend/
  auth/                        # 已有：统一认证
  common/platform-common/      # 跨系统薄契约（JWT 验签/UserHeaders）；blog 不依赖
  file/
    file-service/              # 上传/登记/删除；独占 file_db；持 OSS 凭证
  blog/
    blog-common/               # 本系统基础契约（§6.4 全量）；不依赖 platform-common
    blog-gateway/              # 仅 JWT → X-User-Id
    blog-content-service/      # post / category / tag / comment
```

### 2.1 逻辑模块

| 模块 | 职责 |
|------|------|
| blog-common | 统一返回体/分页、错误码、全局异常、审计与发号、Long 序列化、JWT 验签、权限注解、Swagger 公共配置 |
| post | 草稿/发布/下线/置顶/封面引用 |
| category | 扁平分类 |
| tag + post_tag | 标签与多对多 |
| comment | 提交、审核、前台展示 |
| content | MD 权威入库；发布渲 HTML 并写 OSS |
| （对接）file | 传图、存 content.md/html、元数据 |

### 2.2 基础模块约定（common 边界）

**形态：各系统自持 `{system}-common`。** blog 的 `blog-common` 不依赖 `platform-common`；JWT 验签在 blog 内自实现，配置与 auth **同 `iss`/密钥**。跨系统只走接口；禁止 import 其他系统业务代码。

`blog-common` 落地 `CLAUDE.md` §6.4 契约（禁止业务模块再自建）：

| 包 | 内容 |
|----|------|
| `result` | `Result` `{code,msg,data}`、`PageResult` `{records,total,size,current}` |
| `error` | 错误码枚举（1xxxx/2xxxx/3xxxx）+ `BizException` |
| `exception` | 全局 `@RestControllerAdvice` |
| `page` | `PageQuery`（current/size）+ `orderBy` 白名单 |
| `audit` | `BaseEntity`、`MetaObjectHandler`、`CurrentUser`、14 位 `IdService` + `sys_sequence` |
| `json` | Long→String 序列化 |
| `auth` | `UserHeaders`、`JwtVerifier`、`@RequiresPermission` 切面、权限码常量 |
| `swagger` | springdoc 公共配置 |

**不进 common：** 文章/评论业务规则、OSS、数据范围引擎、具体 Controller/Service。

### 2.3 前端工程结构（blog-admin / blog-portal）

| 工程 | 用户 | 技术 | 本阶段范围 |
|------|------|------|------------|
| `blog-admin` | 登录管理 | Vue3+TS+Pinia+Element Plus | 布局壳 + RBAC 三页骨架 |
| `blog-portal` | 匿名读者 | Vue3+TS+Pinia | 壳 + 首页占位 |

统一约定（§5.5）：`src/api|views|components|stores|router|types|utils|styles`；axios 只在 `api/`；`id` 为 string；令牌策略对齐 auth-portal；登录走统一认证；权限码与 `BlogPermissions` 一致。dev 代理：`/api`→blog-gateway，`/auth`→auth-service。

### 2.4 文件为何独立（F2）

- OSS 密钥、类型/大小白名单、配额、GC **集中**在 file
- 多系统复用，避免各业务抄上传
- blog 只存 object_key/URL 引用
- 后续可升级直传（F3）而不改 blog 表语义

---

## 3. 组件与版本（大版本线；小版本以 pom/package 为准）

### 后端

| 组件 | 版本线 |
|------|--------|
| JDK | 21 |
| Spring Boot | 3.3.x |
| Spring Cloud / Gateway | 2023.0.x / 随 BOM |
| MyBatis-Plus | 3.5.x |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| Nacos | 2.2+ |
| Nginx | 1.20+ |
| SpringDoc | 2.x |
| MapStruct | 1.5.x |
| MD 渲染 | flexmark-java 或 commonmark-java + XSS 白名单 |

### 前端

| 组件 | 版本线 |
|------|--------|
| Node.js | 24+（统一用 24） |
| Vue | 3.5.x |
| Vite | 5.x |
| TypeScript | 5.6.x |
| Vue Router / Pinia | 4.x / 2.x |
| Element Plus | 2.8.x（admin） |
| Axios | 1.7.x |
| **md-editor-v3** | 当前 4.x 线 |

---

## 4. 数据模型

### 4.1 库归属

| 库 | 表 |
|----|-----|
| `file_db` | `sys_sequence`（可选）、`file_object` |
| `blog_db` | `sys_sequence`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`sys_user_ref`、`blog_post`、`blog_post_content`、`blog_category`、`blog_tag`、`blog_post_tag`、`blog_comment` |

通用规范：14 位 ID、审计五件套、无物理 FK、关联表物理删、索引 `idx_/uk_表_字段`。

### 4.2 核心表（逻辑）

**`file_object`**

| 字段 | 说明 |
|------|------|
| id | PK 14 位 |
| object_key | UK，如 `blog/posts/{id}/content.html` |
| bucket / url | |
| content_type / size | |
| owner_id | 中心 user_id |
| biz_type / biz_id | 如 `blog_post` / postId |
| status | 正常/已删 |
| +审计 | |

**`blog_post`**：id, author_id, category_id, title, slug UK, summary, cover_object_key, status(1 草稿/2 已发布/3 下线), is_top, allow_comment, publish_time, view_count, +审计  

**`blog_post_content`**：post_id PK, **content_md（库权威）**, content_md_key, content_html_key  

**`blog_category`（扁平）**：id, name, slug UK, sort, status, +审计  

**`blog_tag`**：id, name UK, slug UK, +审计  

**`blog_post_tag`**：post_id, tag_id UK；物理删  

**`blog_comment`**：id, post_id, parent_id, user_id, content, **status(1 待审/2 通过/3 驳回)**, audit_by, audit_time, +审计  

### 4.3 正文双轨存储（关键）

```
blog_db.content_md  ──发布──►  OSS posts/{id}/content.md（快照）
                      └──────►  OSS posts/{id}/content.html（成品）
图片/封面 上传 ──────►  OSS posts/{id}/assets|cover/*
file_object 登记上述 object
```

| 不变式 |
|--------|
| 改文只改库内 `content_md` |
| 发布以库 MD 为准渲染并覆盖 OSS 成品 |
| 前台 HTML 优先读 OSS；编辑永远读库 MD |
| 库提交成功后再写 OSS；OSS 失败则发布失败/可重试 |

---

## 5. 业务逻辑

### 5.1 文章状态机

```
草稿 ──发布──► 已发布 ──下线──► 下线
```

- 发布：`blog:post:publish`；必填 title；写 publish_time；覆盖 OSS md/html  
- 前台可见：status=已发布且 deleted=0  
- 他人编辑：`blog:post:manage`  

### 5.2 评论审核

```
提交 ──► 待审 ──通过──► 前台可见
           └──驳回──► 不可见
```

- 已发布文且 `allow_comment=1` 可评  
- 管理端 `blog:comment:moderate`  

### 5.3 权限码（blog）

```
blog:post:create / blog:post:publish / blog:post:manage
blog:category:manage / blog:tag:manage
blog:comment:moderate
```

### 5.4 API 草图

```
file:  POST /api/files  DELETE /api/files/{id}  GET /api/files/{id}
blog 管理:  /api/v1/posts|categories|tags|comments/**
blog 公开:  /api/v1/public/posts  /api/v1/public/posts/{slug}
            /api/v1/public/posts/{id}/comments
```

---

## 6. 实施顺序（对应 3 个提案）

1. **add-file-service** — file 单体、file_db、OSS 适配、上传/删除/查询  
2. **add-blog-foundation-rbac** — `blog-common` 基础契约、blog 工程骨架、网关、RBAC 表与鉴权、user_ref  
3. **add-blog-content-modules** — post/category/tag/comment、双轨正文、发布流水线、admin/portal  

---

## 7. 风险摘要

| 风险 | 缓解 |
|------|------|
| 双轨不一致 | 发布失败可重试；以库 MD 为源重放 |
| XSS | 发布 HTML 白名单过滤 |
| OSS 凭证泄露 | 只在 file 服务/密钥管理 |
| 匿名评论滥用 | 限流 + 默认待审 |
| 大 HTML 不进业务表 | 只存 key，读 OSS |
