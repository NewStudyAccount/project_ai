## Context

`backend/blog` 当前（`blog-object-storage-public-url` 变更后）对象存储实现为：MinIO SDK 与装配在 `blog-framework/storage`（`BlogMinioProperties` + `ObjectStorageClient` + `ObjectStorageConfiguration`）、单一 yml 服务商（`blog.minio.*`，local/test 各一份）、`sys_file` 无 `storage_code`、无服务商配置表与管理 API。公开 URL、`Content-Disposition`、删除前引用检查已按设计 v3 落地并保持不动。本变更是 `docs/object-storage-design/object-storage-design.md` §3（独立模块 + S3 多服务商）与 §5.2/§8.2（服务商配置表与管理 API）在 blog 的裁剪落地，动机见 `proposal.md`。

约束：`CLAUDE.md` §2.1 落点原则、§5.4–5.6 模块与包结构、§6.4.2 错误码（blog 系统号 03 → `203xxx`）、§6.5 库表、§6.7 密钥分级、§6.10 配置三件套、§2.2 不引 Flyway；SDK 版本唯一取用处 `docs/version-baseline.md`（MinIO SDK 已登记：9.0.3，可退 8.5.17；父 pom 现为 8.5.17 属「可退」情形，本变更**不调整版本**）。

## Goals / Non-Goals

**Goals:**

- MinIO SDK 依赖收敛到独立模块 `blog-object-storage`（全仓唯一 SDK 落点，§3.1）
- 以 MinIO SDK 单栈适配 S3 多服务商（六类 `provider_type`），运行时配置以 `sys_storage_provider` 库表为真相源
- `sys_file.storage_code` 归属登记 + 存量回源（切默认不影响历史 URL）
- 服务商管理 API 全套（CRUD/测试/启停/设默认/删除保护）+ SK 加密落库与出参脱敏
- 数据迁移 SQL 随变更提供（`deploy/db/migration/blog_db/` + `deploy/db/seed/`）

**Non-Goals:**

- 前端管理页（文件页服务商筛选、服务商配置页）——另开变更，本变更只交付后端契约
- AWS SDK v2 第二底座、`presignPut` 直传、批量上传、对象迁移、定时清理（见 proposal Non-goals）
- 不改公开 URL / Content-Disposition / 引用检查既有契约（`blog-object-storage-public-url` 已裁决）

## Decisions

### D1：独立模块与依赖方向

新增 Maven 模块 `blog-object-storage`，父 pom `modules` 与 `dependencyManagement` 登记。依赖方向：

```
blog-common  ◄──  blog-object-storage（SDK 唯一落点，无 MyBatis/Redis/DB）
                     ▲
              blog-service（直依赖；同时仍依赖 blog-framework）
blog-framework：移除 storage 包与 MinIO SDK 依赖（不再涉对象存储）
```

- `blog-object-storage` 依赖：`blog-common`（纯基础）+ MinIO SDK + Spring Boot（配置绑定/自动装配）。**禁止**依赖 framework、MyBatis-Plus、数据源——否则破坏「能力层不持业务 Entity」边界（§5.6.1）。
- `blog-service` 直依赖 `blog-object-storage`（design §3.1 允许 domain-service 依赖该模块；framework 仅在需要横切接线时才依赖，当前无此需求，保持最简）。
- 模块内包结构：`com.qjj.blog.objectstorage.*`（能力模块，非业务模块，不套 §5.6.2 业务包结构）。
- **回填 `CLAUDE.md` §5.4 模块表**：登记「`{system}-object-storage`（按需，对象存储能力独立模块）」，并在 §2.1 落点原则处补一句「对象存储 SDK 落 object-storage 独立模块（已采用系统：blog）」——按 object-storage-design §3.1 的登记要求执行，随本变更提交。
- 备选「保持 framework 内封装」被否：与 v2/v3 裁剪源冲突，且 SDK 边界无法强制。

### D2：SDK 单栈沿用 MinIO SDK

客户端底座**沿用 MinIO SDK**（S3 兼容，支持任意 endpoint/region/path-style/credentials 组合），满足六类 `provider_type` 适配；**不引入** AWS SDK for Java 2.x。理由：版本基线已登记 MinIO SDK（无需新开基线条目）、`ObjectStorageClient` 现有 put/get/remove/exists 语义可平移、禁止双栈并行（§3.2.1）。备选 AWS SDK v2 被否：引入第二套 SDK 属平行选型（CLAUDE.md §2），收益仅是 virtual-hosted 厂商的原生兼容面，而 MinIO SDK 亦可连 AWS/OSS/COS/OBS 的 S3 兼容 endpoint。

### D3：配置供给 SPI——object-storage 零 DB 依赖

`sys_storage_provider` 的 Entity/Mapper/Service 归**拥有表的** `blog-service`（§5.6.1）；而 `ProviderConfigResolver` 能力在 object-storage 模块。解耦方式：

- object-storage 定义 SPI：`StorageProviderConfigSupplier`（`resolve(code) → StorageProviderConfig`、`defaultProviderCode()`、`listEnabledCodes()`），配置模型 `StorageProviderConfig` 为**模块内 POJO**（非 MyBatis Entity）。
- blog-service 提供 `SysStorageProviderConfigSupplier` 实现（查 `sys_storage_provider`，`deleted=0`；停用服务商也返回——供存量回源，带 `enabled` 标志）。
- yml 引导：`ObjectStorageProperties`（`blog.object-storage.*`）内置 bootstrap providers 列表，`StorageProviderConfigSupplier` 无库实现（冷启动）时兜底；DB 配置优先（§6.1）。
- 备选「object-storage 直接依赖 Mapper」被否：能力模块持业务 Entity 违反实体归属硬规则。

### D4：客户端注册表与缓存失效

object-storage 内 `ProviderClientRegistry`：`providerCode → MinioClient` 实例缓存（`ConcurrentHashMap`）；配置变更（更新/停用/改默认/删除）后调 `invalidate(code)`（管理 API 是唯一变更通道，直接改库不保证生效——记入运维说明）。`ObjectStorageClient` 门面方法按服务商作用域化：

```
put(storageCode, objectKey, stream, size, contentType, originalName)
publicUrl(storageCode, objectKey)          // trimRight(publicBaseUrl,'/') + '/' + objectKey
getBytes(storageCode, objectKey) / remove(storageCode, objectKey) / exists(storageCode, objectKey)
testConnection(storageCode)                // headBucket 等价探测，失败原因可读、无 SK
maxBytes() / isAllowedContentType(ct)     // 全局约束（upload-max-bytes / allowed-content-types）
```

- 上传解析顺序（`ProviderConfigResolver`）：显式 `storageCode`（须存在且启用，否则业务错误）→ 默认服务商（`is_default=1` 且启用）→ yml 引导默认；全无 → 明确业务失败，不静默重试。
- 适配层 `S3ProviderFactory`：按 `provider_type` 给推荐默认（path-style、region 必填性），差异只允许出现在此层（§3.2.1）。

### D5：SK 加密

`SecretKeyCipher`（object-storage 内，纯 JDK `AES/GCM/NoPadding`，随机 IV，落库形态 = Base64(IV ‖ 密文)）。主密钥来源：环境变量 `OBJECT_STORAGE_SK_KEY`（Base64 的 32 字节，`blog.object-storage.secret-crypto.key-source=env`）；生产可切 Nacos——主密钥本身禁止入仓（§6.7）。解密失败（主密钥不一致）→ 业务错误码 `PROVIDER_SECRET_DECRYPT_FAILED`。出参一律 `secretKeyMasked`（尾 4 位策略：`****xxxx`）；入参 `secretKey` 只写（空 = 不修改）。

### D6：库表与迁移 SQL

- 新表 `sys_storage_provider`：字段与索引按设计 §5.2 全表落地（`uk_sys_storage_provider_code`、可选 `idx_sys_storage_provider_status`；审计 5 字段、16 位发号主键、`secret_key` 密文）。SQL 落 `deploy/db/migration/blog_db/06_sys_storage_provider.sql`（建表 + `sys_file` 加列 `storage_code VARCHAR(64) NOT NULL DEFAULT ''` + `KEY idx_sys_file_storage_code` + 回填 `UPDATE sys_file SET storage_code='minio-local' WHERE storage_code=''`）。
- 种子 SQL `deploy/db/seed/2026-10-20-blog-storage-provider.sql`：① 引导默认服务商 `minio-local`（取现 local/test yml 的 endpoint/桶/public-base-url，`is_default=1`、`provider_type=MINIO`、path_style=1；**SK 用部署时主密钥加密后的密文占位，明文不写进 SQL**——seed 里登记操作说明，AK 明文可入（测试环境分级允许），SK 以 `-- 由运维用主密钥加密后替换` 注释占位）；② RBAC 菜单/按钮：`blog:storage-provider:list/create/update/delete/test`（沿用 blog 种子的菜单短号与按钮段号惯例，`/storage/providers` 挂系统管理目录）。
- 人工执行、生产执行前征询（§6.5/§7 红线）；不引 Flyway。

### D7：错误码分配（`BlogErrorCodeEnum`，系统号 03）

| code | 枚举 | 语义 |
|------|------|------|
| 203080 | PROVIDER_NOT_FOUND | 服务商不存在 |
| 203081 | PROVIDER_CODE_EXISTS | 服务商编码已存在 |
| 203082 | PROVIDER_DISABLED | 服务商已停用，禁止新上传 |
| 203083 | PROVIDER_TEST_FAILED | 连通性测试失败（msg 带可读原因） |
| 203084 | PROVIDER_IN_USE | 存在引用文件，禁止删除服务商 |
| 203085 | PROVIDER_CONFIG_INVALID | 服务商配置非法（region 缺失等） |
| 203086 | PROVIDER_SECRET_DECRYPT_FAILED | 密钥解密失败（主密钥不一致） |
| 203087 | NO_STORAGE_PROVIDER_AVAILABLE | 无可用存储服务商配置 |

### D8：配置键迁移（BREAKING，仅 local/test 手工配置）

`blog.minio.*` → `blog.object-storage.*`（对齐设计 §6.2 结构）：

```yaml
blog:
  object-storage:
    default-provider: minio-local
    providers:            # 引导（可选；运行时以 sys_storage_provider 为准）
      - code: minio-local
        provider-type: MINIO
        endpoint/bucket/access-key/secret-key/path-style/public-base-url…
    upload-max-bytes: 10485760
    allowed-content-types: [image/png, image/jpeg, image/gif, application/pdf]
    connect-timeout: 5s
    read-timeout: 30s
    secret-crypto: { key-source: env, key-env: OBJECT_STORAGE_SK_KEY }
```

公共 `application.yml` 只放无密钥项；local/test 改写为 providers 形式（值对齐 `docs/test-env.md`）；旧 `blog.minio` 键删除（同 PR 内改完，不留兼容读取）。`@ConfigurationProperties` 绑定，禁止散落 `@Value`（§6.10）。

### D9：blog-service 落点（对齐 §5.6.2）

```
com.qjj.blog.service/
├── controller/StorageProviderController.java        # /api/v1/storage/providers
├── dto/StorageProviderRequest.java、StorageProviderPageQuery.java
├── vo/StorageProviderVO.java（含 secretKeyMasked）
├── service/StorageProviderService.java
├── service/impl/StorageProviderServiceImpl.java
├── service/impl/SysStorageProviderConfigSupplier.java   # D3 SPI 实现
├── entity/SysStorageProvider.java、mapper/SysStorageProviderMapper.java
└── enums/StorageProviderTypeEnum.java（BaseEnum）
```

`FileServiceImpl`：`upload(file, bizType, bizId, storageCode)`；`put` 前解析服务商并校验启用；`toVO`/`getFileUrl`/`publicImageUrl` 按文件 `storage_code` 取 `publicUrl`；`page` 增加 `storageCode` 筛选。上传时序保持「put → INSERT，失败记 error 日志（objectKey）走补偿」（§3.6/§4.4）。删除/引用检查/幂等/限流不动。服务商写接口 `@RateLimit`（配置写 20/min/用户，§3.4 示例）、关键写（删除/设默认/启停）`@Idempotent` + 审计（action 扩展 `AuditActionEnum`：`STORAGE_PROVIDER_*`）。

## Risks / Trade-offs

- [MinIO SDK 对 virtual-hosted 厂商（AWS_S3 等）兼容性未在本环境验证] → 先以 MINIO/CUSTOM_S3 联调验收；AWS/OSS/COS/OBS 类型开放配置但验收以「测试连通」结果为准，失败给可读原因（不阻塞主流程）。
- [SK 主密钥丢失/漂移 → 密文不可恢复] → `docs/test-env.md` 登记主密钥生成与保管要求；解密失败给明确业务码提示；服务商配置可经管理端重录 SK 恢复。
- [直接改库改 `sys_storage_provider` 不失效客户端缓存] → 管理 API 为唯一变更通道并写入运维说明（与 §4.5 脚本契约同一文档位）。
- [存量 `sys_file.storage_code` 回填依赖引导 code 精确一致（`minio-local`）] → migration 与 seed 同批提供且 code 写死一致；执行顺序 = 先建表/种子、后回填。
- [上传 `put` 在事务内（现状沿用），失败回滚留下孤儿对象] → 保持既有补偿口径：error 日志（objectKey）+ 运维孤儿回收（§4.5.2 已登记），不在本变更扩范围重构。
- [配置键 BREAKING：遗漏改某环境 yml → 启动无服务商] → 无可用配置时明确失败（203087）+ 启动日志列出已装配服务商；local/test yml 与 `docs/test-env.md` 同 PR 更新。

## Migration Plan

1. **DB**（人工执行，顺序固定）：`migration/blog_db/06_sys_storage_provider.sql`（建表 + `sys_file` 加列）→ `seed/2026-10-20-blog-storage-provider.sql`（默认服务商 + RBAC 菜单）→ 迁移 SQL 内回填 `storage_code='minio-local'`。生产执行前征询（红线 1）。
2. **配置**：设置环境变量 `OBJECT_STORAGE_SK_KEY`（生成命令入 `docs/test-env.md`）；改写 local/test yml 为 `blog.object-storage.*`；部署时经管理端把 `minio-local` 的 SK 录入（触发加密落库）。
3. **代码**：新增模块 → 迁移 framework storage → blog-service 改造 → 编译自检（`cd backend/blog && mvn -q compile`）。
4. **联调验收**：上传（默认/指定 storageCode）→ 列表按服务商筛选 → 历史 URL 不随切默认变化 → 停用后禁新传、存量可读 → 测试连通成功/失败原因 → 删除保护 → SK 掩码/不入日志。
5. **回滚**：代码回滚即可（`sys_file.storage_code` 有默认值、新表无强依赖；`blog.minio` 旧键随代码回滚恢复）。种子/迁移 SQL 不自动回滚（`IF NOT EXISTS` + 加列幂等，可保留）。

## Open Questions

- 服务商配置写接口限流阈值（20/min/用户为设计 §3.4 示例值）——实施时按 `application.yml` 固定，不阻塞任务拆分。
