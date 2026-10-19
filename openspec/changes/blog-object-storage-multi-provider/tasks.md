> 实施铁律：只按本清单进行；`proposal / specs / design` 为唯一指令源，不擅自修改（CLAUDE.md §8）。
> 设计决策编号（D1–D9）见 `design.md`。

## 1. 模块脚手架与依赖（D1/D2）

- [x] 1.1 新建 Maven 模块 `backend/blog/blog-object-storage`（父 pom `<modules>` + `dependencyManagement` 登记；模块依赖仅 `blog-common` + MinIO SDK + Spring Boot 配置绑定；**无** MyBatis/数据源/framework 依赖）；包根 `com.qjj.blog.objectstorage`
- [x] 1.2 `blog-framework`：移除 `storage/` 包（`BlogMinioProperties`/`ObjectStorageClient`/`ObjectStorageConfiguration`）与 pom 中 MinIO SDK 依赖；确认 framework 无 `io.minio.*` 残留 import
- [x] 1.3 `blog-service` pom：新增 `blog-object-storage` 依赖；全仓自检「MinIO SDK 坐标只出现在 `blog-object-storage` 的 pom」
- [x] 1.4 提供模块自动装配（`ObjectStorageAutoConfiguration`，`AutoConfiguration.imports` 注册；健康检查不依赖对象存储强可用、客户端懒加载）

## 2. object-storage 能力层（D3/D4/D5/D8）

- [x] 2.1 `ObjectStorageProperties`（`@ConfigurationProperties("blog.object-storage")`）：bootstrap `providers[]`（code/provider-type/endpoint/region/bucket/access-key/secret-key/path-style/public-base-url）+ `default-provider` + `upload-max-bytes`(≤10MB) + `allowed-content-types`(非空) + `connect/read-timeout` + `secret-crypto{key-source,key-env}`
- [x] 2.2 `StorageProviderConfig` POJO + SPI `StorageProviderConfigSupplier`（`resolve(code)` / `defaultProviderCode()` / `listEnabledCodes()`；含 `enabled` 标志支持停用回源）+ yml 引导实现（冷启动兜底，DB 实现优先）
- [x] 2.3 `S3ProviderFactory` 适配层：按 `StorageProviderTypeEnum`（MINIO/AWS_S3/ALIYUN_OSS/TENCENT_COS/HUAWEI_OBS/CUSTOM_S3）给 path-style/region 必填性推荐默认；厂商差异只允许出现在此层；按配置建 MinioClient（超时对齐配置）
- [x] 2.4 `ProviderClientRegistry`：`providerCode → MinioClient` 缓存（`ConcurrentHashMap`）+ `invalidate(code)`/`invalidateAll()`；配置变更后失效
- [x] 2.5 `ObjectStorageClient` 门面（方法按 D4 签名）：`put(storageCode,…)` 写 Content-Type + Content-Disposition（沿用既有 inline/attachment 规则）、`publicUrl(storageCode,objectKey)`（trimRight(publicBaseUrl,'/')+'/'+objectKey，缺 publicBaseUrl 报配置错误）、`getBytes`/`remove`/`exists`、`testConnection(storageCode)`（headBucket 等价探测，失败原因可读、详情无 SK/无带凭证 endpoint）、`maxBytes()`/`isAllowedContentType(ct)`（全局约束）
- [x] 2.6 `SecretKeyCipher`：AES-GCM（随机 IV，落库 Base64(IV‖密文)）；主密钥从 `secret-crypto` 配置的环境变量读取；解密失败抛可识别异常（供服务层转 203086）
- [x] 2.7 上传服务商解析（`ProviderConfigResolver`）：显式 storageCode（不存在/停用 → 明确错误）→ 默认服务商（is_default 且启用）→ yml 引导；全无 → 明确失败（不静默重试）；日志只记 providerCode/bucket/objectKey/size/contentType，禁止 AK/SK

## 3. 数据库 SQL（D6，随变更提供、人工执行）

- [x] 3.1 `deploy/db/migration/blog_db/06_sys_storage_provider.sql`：建 `sys_storage_provider`（字段/索引按设计 §5.2：`uk_sys_storage_provider_code`、`idx_sys_storage_provider_status`、审计 5 字段）；`sys_file` 加列 `storage_code VARCHAR(64) NOT NULL DEFAULT ''` + `idx_sys_file_storage_code` + 回填 `storage_code='minio-local'`（幂等写法）
- [x] 3.2 `deploy/db/seed/2026-10-20-blog-storage-provider.sql`：引导默认服务商 `minio-local`（is_default=1、MINIO、path_style=1、endpoint/bucket/public-base-url 对齐 local/test yml 与 `docs/test-env.md`；SK 以密文占位 + 运维替换注释，**明文 SK 不进 SQL**）；RBAC 菜单/按钮种子：`blog:storage-provider:list/create/update/delete/test`（沿用 blog 种子菜单短号/按钮段号惯例，挂系统管理目录，路由 `/storage/providers`）

## 4. blog-service 服务商域（D7/D9）

- [x] 4.1 `StorageProviderTypeEnum`（BaseEnum，六类 code+desc）；`BlogErrorCodeEnum` 新增 203080–203087（枚举名对齐 D7 表）；`AuditActionEnum` 新增 `STORAGE_PROVIDER_CREATE/UPDATE/DELETE/TEST/DEFAULT/STATUS`
- [x] 4.2 `SysStorageProvider` 实体 + `SysStorageProviderMapper`（`@TableName("sys_storage_provider")`；Entity 只在本服务）
- [x] 4.3 `SysStorageProviderConfigSupplier`（实现 D3 SPI：查库 `deleted=0`，停用也返回且 `enabled=false`；默认项 `is_default=1`）
- [x] 4.4 `StorageProviderService(Impl)`：分页列表（keyword/providerType/status 筛选，出参 `secretKeyMasked` 尾 4 位）；创建/更新（code 唯一校验 203081、region 按类型必填 203085、`secretKey` 空=不改/非空经 `SecretKeyCipher` 加密落库、明文用后即弃）；设默认（事务内唯一 `is_default=1`）；启停（`status` 0/1）；删除保护（存在 `deleted=0` 的 `sys_file.storage_code` 引用 → 203084 提示改停用）；配置变更后调 `registry.invalidate`
- [x] 4.5 连通性测试 `testConnection`：调 object-storage 门面；成功/失败（203083 + 可读原因）均记审计；写接口 `@RateLimit`（配置写 20/min/用户）、删除/设默认/启停 `@Idempotent`（幂等键 `Idempotent-Key`/`X-Request-Id`，重复返回首次 `Result`）
- [x] 4.6 `StorageProviderController`（`/api/v1/storage/providers`）：`GET` 分页、`GET /{id}`、`POST`、`PUT /{id}`、`POST /{id}/test`、`POST /{id}/default`、`PUT /{id}/status`、`DELETE /{id}`；统一 `Result{code,msg,data}`；OpenAPI 注解（`@Operation/@Schema`）；DTO Bean Validation（`@Valid`，失败走全局异常 3xxxx）
- [x] 4.7 `BlogPermissionConstants` 新增 `blog:storage-provider:*` 常量（与种子 SQL 同串）；权限码软校验/按钮显隐口径与既有接口一致

## 5. blog-service 文件域改造（D9）

- [x] 5.1 `SysFile` 实体加 `storageCode` 字段（`sys_file.storage_code`）
- [x] 5.2 `FileService`/`FileServiceImpl`：`upload(file, bizType, bizId, storageCode)`——解析目标服务商（显式须启用，否则 203080/203082；默认链路见 2.7）、`put` + INSERT 落 `storage_code`/`bucket`（失败补偿口径不变：error 日志含 providerCode/objectKey）；`toVO`/`getFileUrl`/`publicImageUrl` 改为按文件 `storage_code` 调 `publicUrl(storageCode, objectKey)`（历史文件回源原服务商）
- [x] 5.3 `FilePageQuery` 加 `storageCode`；`page()` 精确筛选；`FileVO` 出参加 `storageCode`
- [x] 5.4 `FileController`：`POST /api/v1/files` 加可选 `storageCode` 参数（multipart form）
- [x] 5.5 回归确认不动项：公开 URL 无 expireAt、Content-Disposition 规则、删除前引用检查（`FileReferenceChecker` 全量调用）、`@Idempotent`/`@RateLimit`/审计均保持

## 6. 配置与文档

- [x] 6.1 `application.yml`（公共）：`blog.object-storage` 无密钥项（default-provider/upload-max-bytes/allowed-content-types/超时/secret-crypto）；删除 `blog.minio` 键
- [x] 6.2 `application-local.yml`/`application-test.yml`：改写为 `blog.object-storage.providers[]`（minio-local 引导，值对齐 `docs/test-env.md`）；删除旧 `blog.minio` 键
- [x] 6.3 `docs/test-env.md`：登记 `OBJECT_STORAGE_SK_KEY` 生成/保管说明（无生产密钥）、引导服务商 code=`minio-local`、管理端为配置唯一变更通道（直接改库不失效缓存）
- [x] 6.4 回填 `CLAUDE.md` §5.4 模块表（`{system}-object-storage` 按需独立模块，已采用：blog）与 §2.1 落点原则补注（D1 范围，随本变更提交）
- [x] 6.5 `docs/blog-system/*` 对象存储章节同步多服务商口径（服务商配置表/管理 API/storage_code 回源；不改 object-storage-design 已裁决正文）

## 7. 自检与验收（对照 specs 场景）

- [x] 7.1 编译自检：`cd backend/blog && mvn -q compile`（CLAUDE.md §3.2；涉及前端无——本变更不改前端）
- [x] 7.2 依赖自检：`io.minio` 只出现在 `blog-object-storage`；无双栈（无 AWS SDK 坐标）；`allowed-content-types` 非空、`upload-max-bytes` ≤ 10MB
- [x] 7.3 安全自检：任何接口不返回明文 SK；日志/审计 detail 无 SK/AK；`sys_storage_provider.secret_key` 为密文；无生产密钥入仓
- [x] 7.4 行为验收（按 `specs/blog-storage-provider` 与 `specs/blog-file` 场景）：上传默认/指定 storageCode → 列表筛选 → 历史 `url` 不随切默认变化 → 停用禁新传/存量可读 → 测试连通成功与失败可读原因 → 删除保护/幂等重放返回首次 Result → 限流 429 → SK 掩码
- [x] 7.5 提交规范：`feat(blog): 对象存储独立模块与多服务商配置管理`（单提交一事；提交前自查清单 CLAUDE.md §6.3）
