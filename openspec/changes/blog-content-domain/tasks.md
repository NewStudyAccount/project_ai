## 1. 库表与种子（人工执行，先 SQL 后代码）

- [x] 1.1 编写 `deploy/db/migration/blog_db/03_blog_content_tables.sql`：`blog_category` / `blog_tag` / `blog_article` / `blog_article_detail` / `blog_article_tag` / `blog_site_config`（字段/索引以 `docs/blog-system/table-design.md` §2 为准；审计 5 字段、16 位发号主键、`uk_/idx_` 命名、单表索引 ≤5、无物理外键）
- [x] 1.2 编写 `sys_file` 增量 DDL：`biz_type` VARCHAR(32) 默认 `'FILE'`、`biz_id` BIGINT 默认 `0`、`idx_sys_file_biz`；**不改**既有列与 `deleted` 语义；脚本可重复执行（`04_sys_file_biz_cols.sql`）
- [x] 1.3 编写种子 SQL（`deploy/db/seed/2026-10-13-blog-content-domain.sql`）：站点配置键 `site.title`/`site.description`/`site.copyright`/`site.about`；内容管理菜单 + `blog:category:*`/`blog:tag:*`/`blog:article:*`/`blog:site:*`（短号对齐 `rbac-design.md` §2.0，`INSERT IGNORE`）；可选默认分类「未分类」
- [x] 1.4 登记变更：错误码语义进 `BlogErrorCodeEnum`（码值 `203040`–`203070`）；`docs/blog-system/README.md` 状态保持设计稿+本变更实施

## 2. 后端 · 公共与文件增量

- [x] 2.1 `blog-common`/`blog-service`：`ArticleStatusEnum`、`ContentFormatEnum`、`BizFileTypeEnum`、业务错误码扩展（CATEGORY_*/TAG_*/ARTICLE_*/CONTENT_*/SLUG_*/DRAFT_*/SITE_CONFIG_*）
- [x] 2.2 扩展 `sys_file` 实体与 `POST /api/v1/files`：可选入参 `bizType`/`bizId`（缺省 FILE/0）；列表可按 `biz_type` 过滤
- [x] 2.3 `ArticleContentStorageService`：put 对象 → INSERT `sys_file` → 回填 `file_id`；路径 `blog-content/{articleId}/draft|live/{uuid}.{md|html}`；正文 ≤500KB 与格式校验；ObjectStorageClient 补 `getBytes`

## 3. 后端 · 分类与标签（blog-taxonomy）

- [x] 3.1 Entity/Mapper：`blog_category`、`blog_tag`（审计由 framework MetaObjectHandler）
- [x] 3.2 分类树查询/创建/更新/删除（≤2 级、同级 name、slug 唯一、无子无文章才可删）+ 审计 `CATEGORY` + 关键写 `@Idempotent`
- [x] 3.3 标签分页（含引用数）/创建/更新/删除（无关联才可删）+ 审计 `TAG` + 关键写 `@Idempotent`
- [x] 3.4 Controller + DTO/VO + Bean Validation；orderBy 白名单；Long→String 出参

## 4. 后端 · 文章与正文双轨（blog-article）

- [x] 4.1 Entity/Mapper：`blog_article`、`blog_article_detail`、`blog_article_tag`（关联表整组替换）
- [x] 4.2 管理端列表/详情：筛选分页、`hasDraft`、详情 contentFrom=draft|live|none；列表不读 OSS
- [x] 4.3 新建主表（仅元数据+标签，status=0，作者快照 `X-User-*`）与更新主表（不碰正文指针与 status）
- [x] 4.4 保存正文 `PUT …/content`（mode=draft|publish）：双轨写入、旧 draft 逻辑删、mode=draft 不改 content_file_id/status
- [x] 4.5 放弃草稿 `DELETE …/draft`；发布 `POST …/publish`（draft→live 提升、CONTENT_REQUIRED）；下架 `POST …/offline`；删除文章（须非已发布，关联逻辑删）
- [x] 4.6 审计动作：ARTICLE_CREATE/UPDATE/CONTENT_UPDATE/PUBLISH/OFFLINE/DELETE/DRAFT_DISCARD；写接口 `@Idempotent`

## 5. 后端 · 公开读与站点配置

- [x] 5.1 `GET /api/v1/public/categories`、`/public/tags`、`/public/articles`（仅 status=1；排序 is_top DESC, publish_time DESC；列表无正文）
- [x] 5.2 `GET /api/v1/public/articles/{idOrSlug}`（先 id 后 slug；组装 content_file_id→sys_file→MinIO；忽略草稿）与 `/content-url` 预签名
- [x] 5.3 `GET /api/v1/public/site-config`；`GET/PUT /api/v1/admin/site-config`（`blog:site:list`/`blog:site:update`，UPSERT+审计 SITE_CONFIG）
- [x] 5.4 用户端读 `@RateLimit`；网关放行 `/api/v1/public/**`

## 6. 前端 · blog-admin 内容域（blog-admin-content-ui）

- [x] 6.1 路由/组件映射接线内容管理域；按钮按权限码显隐（`hasPermission`）
- [x] 6.2 分类树管理页、标签列表页（CRUD/停用/排序/删除失败展示 msg）
- [x] 6.3 文章列表页（筛选/发布/下架/删除/hasDraft 提示）+ 新建/编辑主表页（含封面 `POST /api/v1/files`）
- [x] 6.4 写正文页：编辑器、保存草稿/发布/放弃草稿、双轨提示文案、SEO 字段；与主表分页分离
- [x] 6.5 站点配置页

## 7. 前端 · blog-portal（blog-portal）

- [x] 7.1 新建 `frontend/blog-portal`（Vue3+Vite+TS+Pinia+Element Plus）：路由 `/`、`/articles`、`/articles/:idOrSlug`、`/categories`、`/tags`、关于页
- [x] 7.2 公开 API 封装与列表/详情渲染（Markdown）；分类/标签筛选；站点配置文案
- [x] 7.3 不存在/未发布文章降级页；无管理入口、无登录

## 8. 部署与自检

- [x] 8.1 Nginx：`deploy/nginx/blog-portal.conf` 静态托管 + `/api` 反代；`docs/port-registry.md` 登记 5177/5200
- [x] 8.2 提交前自检：`mvn -q compile` 通过；`blog-admin`/`blog-portal` `type-check`+`lint` 通过
- [ ] 8.3 联调冒烟：分类/标签 CRUD → 两页建文 → 草稿不掉线 → 发布切换 → 公开列表/详情 → 站点配置；幂等重放返回首次 Result；限流 429（**待测试环境 SQL/MinIO 就绪后执行**）
- [ ] 8.4 回填 `docs/blog-system/` 实施状态与差异说明（冒烟后）
