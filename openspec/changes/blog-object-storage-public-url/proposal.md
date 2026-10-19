## Why

`docs/object-storage-design.md` **v3** 已裁决访问模型为「公开永久 URL」（公共读桶 + 稳定 `url`，无预签名、无过期），并补齐上传 `Content-Disposition`、删除前引用检查、`publicBaseUrl + objectKey` 拼接等契约。`backend/blog` 现状仍为 v2 预签名：`presignGet` / `PresignVO{url,expireAt}` / `presign-ttl`，且 `FileService.logicalDelete` **无**引用检查，与唯一裁剪源不一致，正文配图与列表缩略图会因 URL 过期失效。

## What Changes

- **BREAKING** 访问模型：`GET /files/{id}/url` 由预签名 `{url, expireAt}` 改为稳定公开 `{url}`（`publicBaseUrl + '/' + objectKey`）；删除 `PresignVO.expireAt` 语义（或改为 `FileUrlVO`）。
- **BREAKING** 对象存储封装：`ObjectStorageClient.presignGet` → `publicUrl(objectKey)`；上传 `put` 写入 `Content-Type` + **`Content-Disposition`**（`image/*`、`application/pdf` → `inline`；其余 → `attachment; filename=原名`）。
- 配置：`blog.minio`（或等价）增加 **`public-base-url`**；移除读路径对 `presign-ttl` 的依赖（可保留仅供可选 `presignPut`，默认不用）。
- 文件 VO / 列表出参增加稳定 **`url`**；管理端预览/下载、缩略图、正文插图统一使用该 `url`。
- **删除前引用检查**：`DELETE /files/{id}` 与业务侧删文件前调用 `FileReferenceChecker`（blog 实现：封面 `cover_file_id`、正文/草稿 `content_file_id`/`draft_file_id`、`sys_file.biz_type/biz_id`）；**有引用拒绝删除**并返回引用摘要；运维物理清理二次检查（脚本契约，本变更登记任务项）。
- 公开读接口：`GET /api/v1/public/files/{id}`（插图稳定地址）与 `content-url` 改为返回/跳转**公开 `url`**（或文档改为 Markdown 直接写公开 `url`），不再签发短 TTL。
- 部署约定：MinIO 桶 **公共读**（匿名 GET 200、匿名写失败）；`public-base-url` 可为 `http://ip:port/...`（**不强制 HTTPS**）。
- 同步 `docs/blog-system/*` 中预签名表述为公开 URL 口径（文档随本变更，不改 `docs/object-storage-design` 已裁决条款）。

**Non-goals / 边界（对齐 CLAUDE.md §1、object-storage-design §1.2）：**

- 不做批量上传、图片压缩/水印、病毒扫描、Excel、分布式定时清理、DB 迁移框架。
- 不改多系统边界；不把 blog 文件能力做成跨系统文件中心。
- 不强制 HTTPS；不引入第二套 SDK / Sentinel / 业务直绑 MinIO。
- 默认不做「强制删除」跳过引用检查（需要时另开变更）。

## Capabilities

### New Capabilities

（无——文件与公开读能力已由未归档变更引入，本变更做**契约收敛**，不新开能力名。）

### Modified Capabilities

> `openspec/specs/` 尚无已归档同名能力；下列为对未归档变更中规格的收敛。实施以本变更 specs 为准；归档时与 `scaffold-blog-system` / `blog-content-domain` 中 `blog-file`、`blog-public-read` 合并 delta（仅契约表述，不改其既有任务框架）。

- `blog-file`（见 `scaffold-blog-system`）：访问由预签名改为**公开永久 URL**；上传写 `Content-Disposition`；出参含 `url`；**删除前引用检查**（有引用拒绝）。
- `blog-public-read`（见 `blog-content-domain`）：正文 `content-url` / 公开插图改为稳定公开 `url`（无 `expireAt`、无重签）。

## Impact

- **代码（`backend/blog`）**
  - `blog-framework/.../storage/`：`ObjectStorageClient`、`BlogMinioProperties`、装配（`publicUrl`、`public-base-url`、put 元数据）
  - `blog-service`：`FileService`/`FileServiceImpl`、`FileController`、`FileVO`/`PresignVO`（或 `FileUrlVO`）、`ArticleContentStorageService`、`ArticleService`（正文取址）、`PublicController`
  - 新增：`FileReferenceChecker` 及 blog 实现（文章封面/正文/草稿引用）
  - `application*.yml`：`public-base-url`；读路径去 `presign-ttl`
- **接口（破坏性）**：`GET /api/v1/files/{id}/url` 响应形态；公开 `content-url` / `public/files/{id}` 语义
- **数据**：不改表结构（沿用 `sys_file` + `biz_type`/`biz_id`）；无需 Flyway
- **配置/运维**：MinIO 桶公共读；`public-base-url` 登记 `docs/test-env.md`（无生产密钥）
- **文档**：`docs/blog-system/*` 预签名表述改为公开 URL；不改 `docs/object-storage-design` 已裁决正文
- **边界**：仅 `backend/blog` + blog 文档；不改其它系统、不跨系统直连
