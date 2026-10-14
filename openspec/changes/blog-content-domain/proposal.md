# Proposal: blog-content-domain

## Why

`scaffold-blog-system` 已交付 blog 系统骨架与横切能力（RBAC / 文件 / 审计 / 发号 / 幂等限流），但**内容业务域**（分类、标签、文章、正文 OSS、用户端阅读）尚未落地。设计裁剪源 `docs/blog-system/`（business-design / table-design / api-design）已收口，需按 SDD 开变更实施：管理端可维护文章全生命周期，用户端可浏览已发布内容。

## What Changes

- **新增 6 张业务表**（SQL 落 `deploy/db/migration/blog_db/`，人工执行）：`blog_category` / `blog_tag` / `blog_article` / `blog_article_detail` / `blog_article_tag` / `blog_site_config`（字段/索引以 `docs/blog-system/table-design.md` 为唯一源）
- **`sys_file` 增量补列**：`biz_type` / `biz_id` + `idx_sys_file_biz`（**仅补列**，不改既有列与 `deleted` 删除语义）；上传接口补可选 `bizType`/`bizId`；正文/草稿/封面一律先登记 `sys_file` 再持 `file_id`
- **分类 / 标签管理**：树形分类（≤2 级）、扁平标签，CRUD + 停用 + 排序；删除约束（无子/无引用才可删）
- **文章主表 / 详细表双轨正文**：两页两接口（主表元数据与正文分离）；`content_file_id`（线上）+ `draft_file_id`（草稿）双轨——保存草稿不掉线，发布才切换线上版；逻辑删仅限草稿/已下架
- **管理端扩展 `blog-admin`**：内容管理菜单域（分类/标签/文章/站点配置），Markdown 写正文页（保存草稿 / 发布 / 放弃草稿）
- **新建 `frontend/blog-portal`**：只读用户端（首页、列表、详情、分类、标签、关于）；公开 API `/api/v1/public/**` 网关放行
- **站点配置** `blog_site_config`：站名/描述/备案/关于（键值），管理端可写、用户端公开读
- **横切接线（不改口径）**：关键写 `@Idempotent`、用户端读 `@RateLimit`、审计 `blog_audit_log`（CATEGORY/TAG/ARTICLE/SITE_CONFIG）、发号 16 位、错误码 `203xxx`（系统号已登记）

Non-goals（项目边界，对齐 `CLAUDE.md` §1 / §2.2 与 `docs/blog-system` §1.2）：

- **不做**评论、点赞、收藏、阅读量统计、访客分析（`view_count` 仅预留字段，本期无逻辑）
- **不做**用户注册/登录/个人中心/作者主页（归用户中心、认证中心）
- **不做**独立搜索引擎（ES 等）；检索先 DB
- **不做**文章版本历史表、密码保护文章、转载链接、RSS/站点地图（见 table-design §6 开放问题）
- **不做** Excel、分布式定时任务、多语言强制交付、MinIO 对象定时清理
- **不做**跨系统 RBAC 同步、跨库分布式事务、Entity 跨服务共享
- **不改**既有横切能力口径（发号、`@Idempotent`、`@RateLimit`、审计、`sys_file` 既有列与删除语义）；**例外**：`sys_file` 仅增量补 `biz_type`/`biz_id`
- **不入仓**生产密钥

## Capabilities

### New Capabilities

- `blog-taxonomy`: 文章分类（树 ≤2 级）与标签的管理端 CRUD、停用、排序、删除约束
- `blog-article`: 文章主表/详细表、状态机（草稿/已发布/已下架）、正文 OSS 双轨（`content_file_id`/`draft_file_id` + `sys_file` 登记）、发布/下架/删除/放弃草稿
- `blog-public-read`: 用户端公开读（分类树/标签/文章列表/详情 `idOrSlug`/可选预签名），仅 `status=1`
- `blog-site-config`: 站点配置键值（管理端读写 + 用户端公开读）
- `blog-admin-content-ui`: `blog-admin` 内容管理界面（分类/标签/文章两页流程/Markdown 编辑器/站点配置）
- `blog-portal`: `frontend/blog-portal` 只读用户端（首页/列表/详情/分类/标签/关于）

### Modified Capabilities

（无——`openspec/specs/` 尚无既有能力；`blog-file` 上传补 `bizType`/`bizId` 与 `sys_file` 补列记入本变更 `blog-article` / 任务，待 `scaffold-blog-system` 归档后再视需要合并 delta）

## Impact

- **后端**：`backend/blog/blog-service`（entity/mapper/service/controller 内容域）、`blog-common`（枚举/常量）、`blog-framework`（仅在既有 ObjectStorageClient 上使用，不改封装口径）、`blog-gateway`（放行 `/api/v1/public/**`）
- **前端**：扩展 `frontend/blog-admin`（内容菜单域）；**新建** `frontend/blog-portal`（Vue3 + Vite + TS + Pinia + Element Plus，不引平行 UI 库）
- **数据层**：`blog_db` 新增 6 表 + `sys_file` 增量 DDL；种子（站点配置、内容管理菜单/权限、可选「未分类」）落 `deploy/db/seed/`
- **既有接口**：`POST /api/v1/files` 补可选 `bizType`/`bizId`（缺省 `FILE`/`0`）；权限仍 `blog:file:upload`
- **权限码**：`blog:category:*` / `blog:tag:*` / `blog:article:*` / `blog:site:*`（格式 `system:resource:action`）；菜单种子随实施 INSERT
- **错误码**：业务段 **`203xxx`**（系统号 03 已在 `docs/error-code-ranges.md`）；语义见 `docs/blog-system/api-design.md` §0
- **对象存储**：裁剪源 `docs/template/object-storage-design.md`；正文 ≤500KB（`text/markdown`/`text/html`）经业务 JSON 写入后服务端 put；封面走既有文件上传
- **部署**：`blog-portal` 静态由 Nginx 托管 + `/api` 反代（登记 `docs/port-registry.md`）；SQL 人工执行
- **文档**：实施以 `docs/blog-system/` 为裁剪源写码；完成后回填该目录「实施状态」，**不**改 `CLAUDE.md` 服务/表清单
