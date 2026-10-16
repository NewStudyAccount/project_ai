## Context

`scaffold-blog-system` 已具备 `blog-service` / `blog-framework`（ObjectStorageClient、发号、审计、`@Idempotent`、`@RateLimit`）、`blog-gateway`（JWT、`X-User-*`）、`blog-admin` 壳与 `sys_file`/`blog_audit_log`。内容业务设计裁剪源为 `docs/blog-system/`（字段/接口唯一源：`table-design.md` / `api-design.md`；规则：`business-design.md`）。见 proposal.md - Why。

约束：纯网关鉴权（业务服务只信 `X-User-*`）；Entity 仅在 `blog-service`；OSS IO 在事务外；`sys_file` 只增量补列；错误码 `203xxx`。

## Goals / Non-Goals

**Goals:**

- 按已收口设计落地内容域 6 表 + 双轨正文 + 两端前端 + 公开读 API
- 复用既有横切，不改 framework 口径；`sys_file` 最小 DDL 增量
- 建表/种子 SQL 可人工执行、可重复（`IF NOT EXISTS` / `INSERT IGNORE`）

**Non-Goals:**

- 不引入 ES、版本表、评论/统计逻辑、定时清理（见 proposal Non-goals）
- 不改 `scaffold-blog-system` 未归档任务范围；不迁其他系统代码

## Decisions

| # | 决策 | 取舍与理由 | 备选 |
|---|------|------------|------|
| 1 | 正文双轨 = `content_file_id` + `draft_file_id`，对象键只在 `sys_file` | 保存草稿不掉线；与 api/table/ER 一致；禁止业务表裸存 `object_key` | 单轨覆盖（已否决：已发布改稿会掉线） |
| 2 | `sys_file` 补 `biz_type`/`biz_id` 而非平行 OSS 表 | 对齐 `object-storage-design.md` §5，避免双源；DDL 最小 | 新建 `blog_content_file`（否决：登记双源） |
| 3 | 新建两页两接口：`POST /articles` 只建主表，正文 `PUT …/content` | 主表先得 id 再写 OSS；前端禁止一页混提 | 一次提交主表+正文（否决：id/事务/失败补偿复杂） |
| 4 | 发布 = draft 提升为 live（新对象+新 sys_file 行），旧 live 逻辑删 | 审计可追溯；对象运维清理 | 原地覆盖 live 对象（否决：无历史、失败难回滚） |
| 5 | 用户端详情 `{idOrSlug}`：先 id 后 slug | 兼容发号访问与友好 URL | 仅 slug（否决：空 slug 文无法访问） |
| 6 | 正文经业务 JSON（≤500KB）服务端 put；封面走 `/api/v1/files` | 小文件经业务接口，避开网关中转大包；与 object-storage 边界兼容 | 浏览器直传 MinIO（否决：须另开变更收紧 CORS/凭证） |
| 7 | 包结构落在既有 `blog-service` 单模块内 `…/service/{controller,dto,vo,…}`，不新建微服务 | 内容域与骨架同库同限界；避免过度拆分 | 独立 `content-service`（否决：本期无独立部署诉求） |
| 8 | 站点配置 `config_key` 多行而非单行大宽表 | 易扩展；种子键固定 | 单行 id=1 宽表 |
| 9 | `blog-portal` 独立 `frontend/blog-portal` 工程 | 用户端无管理入口，构建/部署独立；符合多系统前端容器 | 塞进 `blog-admin` 路由（否决：边界混杂） |

模块落点（`CLAUDE.md` §5.4–5.6）：

```
blog-common     ArticleStatusEnum、BizFileTypeEnum、错误码枚举扩展、常量
blog-framework  不改封装；仅复用 ObjectStorageClient / 发号 / 审计 / 幂等 / 限流
blog-service    content 域 entity/mapper/service/controller/converter
blog-gateway    放行 /api/v1/public/**
blog-admin      内容菜单域页面
blog-portal     新建只读端
```

事务与 OSS 顺序：`put object` → `INSERT sys_file` → `@Transactional` 更新 detail/article/tag；失败记审计/日志含 `object_key` 待补偿（无定时，运维脚本）。

配置：三份 yml（`CLAUDE.md` §6.10）；MinIO 键沿用 `blog.minio`；正文大小/格式白名单应用层校验（可配置项，缺省 500KB）。

## Risks / Trade-offs

- [put 成功但落库失败留下孤儿对象] → 审计/错误日志记录 object_key；运维脚本按 `sys_file.deleted=1` 或无登记行清理；不建定时任务
- [发布时复制对象增加一次 IO] → 正文 ≤500KB 可接受；失败则发布整体失败并回滚指针事务
- [title LIKE 检索性能] → 本期接受；量级上来再开变更（ES 不做）
- [`sys_file` 补列影响既有文件接口] → 默认值 `'FILE'`/`0`，旧数据兼容；上传参数可选
- [portal 与 admin 网关前缀/部署路径] → Nginx 同域名多前缀或独立 location，实施登记 `docs/port-registry.md`

## Migration Plan

1. 人工执行 `deploy/db/migration/blog_db/03_blog_content_tables.sql`（6 新表）与 `sys_file` 增量 ALTER（可重复/幂等写法）
2. 执行种子：站点配置键、内容管理菜单/权限（短号对齐 `rbac-design.md` §2.0）、可选「未分类」
3. 发布 `blog-service`（含网关 public 放行配置）→ `blog-admin` → `blog-portal` 静态资源（Nginx）
4. 回滚：下线新服务/前端路由；表与 `sys_file` 新列可保留（无破坏性 migration）；必要时停用内容菜单

## Open Questions

- 用户端独立域名 vs 同域 `/blog` 前缀：待运维/产品定，不阻塞开发（见 table-design §6）
- `site.about` 超长是否挂 OSS：先 VARCHAR(1024)，需要时再开变更
