# blog-system · 设计文档索引

> 本目录是 **blog 系统内容业务**的设计文档集合（独立文档，非 openspec 提案）。
> 契约口径以根目录 `CLAUDE.md` 为准；对象存储策略以 `docs/template/object-storage-design.md` 为准。

| 文档 | 职责 | 状态 |
|------|------|------|
| [business-design.md](./business-design.md) | 业务逻辑总览：领域模型、状态机、接口、两端前端 | 设计稿 |
| [table-design.md](./table-design.md) | **库表契约**：新增/修改清单、字段、索引、种子 | 设计稿 |
| [api-design.md](./api-design.md) | **接口文档**：接口名、入参、出参、逻辑、涉及表 | 设计稿 |
| [er-diagram.html](./er-diagram.html) | 表关系 ER 图（浏览器打开） | 与 table-design 同步 |
| [prototype.html](./prototype.html) | 管理端 + 用户端页面原型（浏览器打开） | 布局参照，效力低于设计正文 |

> **实施状态：** OpenSpec 变更 `openspec/changes/blog-content-domain/` 已落码（SQL + 后端 + blog-admin 内容页 + blog-portal）；`article-content-markdown-only` 已收敛正文唯一 Markdown（去格式字段/列）；`article-content-server-render`（正文改**服务端渲染**，公开详情 `content`=HTML）已提案待实施；**联调冒烟待环境就绪**。契约偏差以代码 + 变更 specs 为准回写本目录。

**阅读顺序：** business-design → table-design → api-design → er-diagram → prototype。

**效力顺序：** `CLAUDE.md`（契约） > `table-design.md` / `business-design.md`（本目录正文） > HTML（ER/原型，仅展示与交互参照）。

---

## 范围一句话

文章分类 / 标签 / 主表 / 详细表（正文双轨：草稿 `draft_file_id` + 线上 `content_file_id`，对象登记 `sys_file`）；`sys_file` 增量补 `biz_type`/`biz_id`；站点配置 `blog_site_config`；扩展 `blog-admin`；新建 `blog-portal` 用户端。

## 对象存储裁剪取值（blog 登记）

> 通用策略、封装、生命周期与安全红线唯一裁剪源：`docs/object-storage-design/object-storage-design.md`（§9）。  
> **此处只登记 blog 取值与差异**，不复制通用条文。连接信息真相源：`docs/test-env.md` §2.4（与 `application-local.yml` 的 `blog.object-storage.providers[]` 一致）。

| 项 | blog 取值 | 相对默认/模板的差异 |
|----|-----------|---------------------|
| 模块形态 | **独立模块** `blog-object-storage`（SDK 唯一落点） | 对齐设计 §3.1；已采用独立模块，framework 不再持 SDK |
| 配置前缀 | `blog.object-storage` | 结构对齐设计 §6.2；`providers[]` + `default-provider` + `secret-crypto` |
| 引导默认服务商 | `minio-local`（is_default=1、MINIO、path_style=1） | 运行时以 `sys_storage_provider` 库表为准，DB 优先 |
| 桶（bucket） | `blog` | 模板示意为 `example` |
| 附件 MIME 白名单 | `image/png`、`image/jpeg`、`image/gif`、`application/pdf` | 与默认一致；**禁止空=全放行** |
| 正文 MIME | `text/markdown`（仅 Markdown） | **业务路径 put**（`ArticleContentStorageService` 走默认服务商），**不过**附件白名单；单篇 ≤ **500KB** |
| 正文对象键 | `blog-content/{articleId}/draft\|live/{uuid}.md` | blog 专有前缀；**无**格式字段（唯一 Markdown） |
| Markdown 编写 | 源码 + 分栏预览；禁 HTML 结构；约定见 `business-design.md` §E | 写正文页唯一编写口径 |
| 附件对象键 | UUID（含扩展名） | 与默认一致 |
| 单文件上限 | 10MB（附件） | 与 `CLAUDE.md` §6.11 一致 |
| 公开永久 URL | 无过期 | `publicBaseUrl` + objectKey（按文件 `storage_code` 回源）；响应 `{ url }` |
| 上传入参 | `POST /api/v1/files`：file + 可选 `bizType`/`bizId`/`storageCode` | `storageCode` 空=走默认链路 |
| 文件权限 | `blog:file:upload` / `list` / `read` / `delete` | 格式 `system:resource:action` |
| 服务商权限 | `blog:storage-provider:list` / `create` / `update` / `delete` / `test` | 服务商配置 CRUD + 连通测试 |
| 删除语义 | `sys_file.deleted=1` 逻辑删除 | 不另设 status；物理清理走运维脚本，**不建定时** |
| SK 加密 | AES-GCM；主密钥来自环境变量 `OBJECT_STORAGE_SK_KEY` | 出参 `secretKeyMasked`（****）；明文不回显、不入日志/审计 |
| MinIO SDK | **8.5.17**（父 POM） | 基线 `docs/version-baseline.md` 为 9.0.3、可退 8.5.17；blog 现用退档 |
| 裁剪源引用 | `object-storage-design.md` | 封装只在 `blog-object-storage` `ObjectStorageClient` |

**运维清理/补偿：** 契约见 `object-storage-design.md` §4.5（dry-run、保留窗口、失败清单）；blog 侧脚本入口与执行人**待登记**（见该文 §8.1）。

---

## 相关既有材料（不在本目录）

| 路径 | 关系 |
|------|------|
| `docs/object-storage-design/object-storage-design.md` | MinIO/公开 URL/逻辑删除唯一裁剪源 |
| `docs/template/rbac-design.md` | 权限标识与菜单短号 |
| `docs/template/admin-ui-design.md` | 管理端视觉（极光 Aurora） |
| `docs/template/fixbug/2026-10-19-minio-signature-object-storage.md` | MinIO `SignatureDoesNotMatch` 排查与配置对齐 |
| `openspec/changes/scaffold-blog-system/` | 骨架横切能力（已落地边界） |
| `deploy/db/migration/blog_db/` | 既有表 SQL；内容表 SQL 落此处（实施时） |
