## Why

`docs/object-storage-design/object-storage-design.md` v2/v3 已将对象存储能力裁决为「**独立模块** + **S3 协议多服务商** + **服务商库表配置**」：SDK 落 `{system}-object-storage` 独立模块、以 S3 协议适配 MinIO/AWS/OSS/COS/OBS 等服务商、运行时配置以 `sys_storage_provider` 库表为真相源、`sys_file.storage_code` 回源历史文件。`backend/blog` 现状（`blog-object-storage-public-url` 变更后）仍是 **framework 内单 MinIO 服务商 + 纯 yml 配置**：SDK 与装配在 `blog-framework/storage`、无服务商配置表、无 `storage_code`、无连通性测试与默认服务商切换，与唯一裁剪源不一致，无法支持多服务商与运行时配置管理。

## What Changes

- **新增独立 Maven 模块 `blog-object-storage`**：MinIO SDK 依赖与 S3 多服务商适配（`ObjectStorageClient` / `ProviderConfigResolver` / `S3Provider` SPI）从 `blog-framework/storage` 整体迁入；SDK 依赖**只允许**出现在该模块（object-storage-design §3.1）；`blog-framework` 可依赖它做装配，`common`/网关/api 禁止引入。
- **S3 多服务商适配**：新增 `provider_type` 枚举（`MINIO` / `AWS_S3` / `ALIYUN_OSS` / `TENCENT_COS` / `HUAWEI_OBS` / `CUSTOM_S3`）；客户端底座**沿用已登记的 MinIO SDK 单栈**（S3 兼容，禁止双栈并行，§3.2.1）；按 providerType 适配 endpoint/region/path-style 差异，禁止渗透业务层。
- **新增 `sys_storage_provider` 表**（库表真相源）+ 实体/Mapper/Service/Controller 归 `blog-service`（表拥有方，§5.6.1）：服务商 CRUD、连通性测试（headBucket）、启用/停用、设默认；`secret_key` **AES-GCM 加密落库**、出参只回掩码、禁止明文/Secret 进日志与审计。
- **`sys_file` 增加 `storage_code` 列**：上传登记归属服务商；读/删**存量**文件按 `storage_code` 解析对应服务商（含已停用：可读可逻辑删、禁新上传）；**新上传**解析顺序 = 显式 `storageCode` → 默认服务商（`is_default=1`）→ yml 引导默认；切默认**只影响新上传**，禁止批量改写历史 URL。
- **多服务商客户端缓存**：按 `providerCode` 缓存客户端实例；配置变更（更新/停用/改默认）后失效缓存。
- **上传 API 增强**：`POST /api/v1/files` 新增可选 `storageCode` 参数；`FileVO` 出参增加 `storageCode`；列表新增 `storageCode` 筛选。
- **新增管理 API**（object-storage-design §8.2）：`GET/POST /api/v1/storage/providers`、`GET/PUT/DELETE /api/v1/storage/providers/{id}`、`POST /{id}/test`、`POST /{id}/default`、`PUT /{id}/status`；删除仅当无 `deleted=0` 文件引用；关键写 `@Idempotent` + 审计，写接口 `@RateLimit`。
- **配置迁移**：`blog.minio.*` 单服务商键 → `blog.object-storage.*`（bootstrap providers 列表 + `upload-max-bytes`/`allowed-content-types`/超时 + `secret-crypto` 主密钥来源）；DB 配置优先于 yml 引导；公共 `application.yml` 仍无主机/账号密码（CLAUDE.md §6.10）。
- **数据迁移 SQL**：建 `sys_storage_provider` 表 + 存量 `sys_file.storage_code` 回填 + 引导默认服务商一条（现 yml MinIO 配置登记为 `minio-local`，`is_default=1`）；随本变更提供、人工执行（不引 Flyway，§2.2）。
- **新增错误码**：服务商相关业务码（`203080` 起在本系统序号内分配，`BlogErrorCodeEnum`）。
- **回填 `CLAUDE.md` §5.4 模块表**：登记 `blog-object-storage` 独立模块形态（object-storage-design §3.1 明确要求）。

**Non-goals / 边界（对齐 CLAUDE.md §1、object-storage-design §1.2）：**

- **不改前端**（`frontend/blog-admin` 文件页服务商筛选/服务商配置页属管理端双页，另开变更；本变更只交付后端 API 契约）。
- 不做跨系统统一文件中心；元数据与服务商配置由 blog 自治。
- 不做浏览器直传 `presignPut`（默认关闭）、批量上传、对象迁移搬迁、分布式定时清理（运维脚本契约沿用已有登记）。
- 不引入 AWS SDK 第二套底座、不引入 Flyway/Liquibase；不新建服务拆分。
- 存量对象**不迁移**：历史文件按 `storage_code` 回源原服务商拼 URL。

## Capabilities

### New Capabilities

- `blog-storage-provider`: 对象存储服务商配置管理——`sys_storage_provider` 库表真相源、S3 多服务商类型、SK 加密与脱敏、连通性测试、启停/默认项、删除保护（无活跃文件引用）、缓存失效语义。

### Modified Capabilities

> `openspec/specs/` 尚无已归档能力；下列为对未归档变更中规格的增量收敛，实施以本变更 specs 为准，归档时与既有 delta 合并。

- `blog-file`（见 `scaffold-blog-system`、`blog-object-storage-public-url`）：上传/列表/详情/删除增加**服务商维度**——`storageCode` 入参与出参、按文件 `storage_code` 回源解析服务商拼公开 URL、新上传服务商解析顺序、SDK 落独立模块（行为契约不变部分：公开 URL、Content-Disposition、引用检查删除保持不动）。

## Impact

- **代码（`backend/blog`）**
  - 父 `pom.xml`：新增 `blog-object-storage` 模块与 dependencyManagement；`blog-framework` 去掉 MinIO SDK 直接依赖（改依赖 object-storage 模块）；`blog-service` 依赖 object-storage。
  - 新模块 `blog-object-storage`：`ObjectStorageClient`（多服务商实例缓存 + 失效）、`ProviderConfigResolver`、`S3Provider` SPI、`StorageProviderType` 枚举、SK 加密组件、yml 引导配置绑定。
  - `blog-framework`：`storage/` 包迁出（`BlogMinioProperties`/`ObjectStorageConfiguration` 移除或改造为接线）。
  - `blog-service`：`SysFile` 实体加 `storageCode`；`FileServiceImpl`（上传解析服务商、URL 按文件回源、列表筛选）；新增 `SysStorageProvider` 实体/Mapper/`StorageProviderService(Impl)`/`StorageProviderController`/DTO/VO；`BlogErrorCodeEnum` 新增服务商错误码；RBAC 菜单/权限码种子（`blog:storage-provider:*`）。
- **接口（非破坏性增强 + 新增）**：`POST /api/v1/files` 可选 `storageCode`；`GET /api/v1/files` 筛选 `storageCode`；`FileVO.storageCode`；新增 `/api/v1/storage/providers` 全套。
- **数据**：`sys_storage_provider` 建表 SQL + `sys_file` 加列/回填 `storage_code` SQL（随变更提供、人工执行、生产执行前征询）。
- **配置**：`blog.minio.*` → `blog.object-storage.*`（local/test yml 同步改写；公共 yml 无密钥）；新增 `secret-crypto` 主密钥来源（env `OBJECT_STORAGE_SK_KEY`，不入仓）。
- **文档**：回填 `CLAUDE.md` §5.4 模块表；`docs/test-env.md` 登记引导服务商与主密钥说明；`docs/blog-system/*` 对象存储表述同步为多服务商口径；SDK 版本沿用 `docs/version-baseline.md` 已登记条目（不新开底座）。
- **边界**：仅 `backend/blog`（+项目级文档回填）；不改其它系统、不跨系统直连；前端页面不在本变更。
