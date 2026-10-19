## MODIFIED Requirements

### Requirement: 单文件上传

系统 SHALL 支持单文件上传（`POST /api/v1/files`，multipart）：服务端校验单文件 ≤10MB、MIME 白名单（`image/png,image/jpeg,image/gif,application/pdf`，禁止空=全放行）、UUID 文件名、元数据落 `sys_file`（16 位发号，含归属服务商 `storage_code` 与 `bucket`）；上传 SHALL `@RateLimit`（默认形态 10/min/用户）并记录审计。上传可携带可选参数 `storageCode` 指定目标服务商（仅允许启用中的服务商）。对象存储 SDK 只经独立模块 `blog-object-storage` 封装（`object-storage-design.md` §3.1），业务 MUST NOT 直绑 SDK。

#### Scenario: 上传成功

- **WHEN** 管理员上传白名单内且 ≤10MB 的文件
- **THEN** 对象写入解析出的目标服务商、元数据落库（含 `storage_code`、`bucket`），返回 `FileVO`（`id` String、`storageCode`、`bucket`、`objectKey`、`url`、`originalName`、`contentType`、`sizeBytes`、`deleted`）

#### Scenario: 指定目标服务商上传

- **WHEN** 上传请求携带 `storageCode=B` 且 B 为启用中的有效服务商
- **THEN** 对象写入 B 的桶，元数据 `storage_code=B`，返回按 B 的 `public_base_url` 拼接的 `url`

#### Scenario: 超限或类型非法

- **WHEN** 文件超过大小限制或 MIME 不在白名单
- **THEN** 返回 HTTP 200 + 业务/校验段错误码与明确 `msg`，不落库、不写对象（或写后清理一致）

#### Scenario: 触发限流

- **WHEN** 同一用户上传频率超过配置阈值
- **THEN** 返回 HTTP 429 + 系统段 `10003`

## ADDED Requirements

### Requirement: 上传目标服务商解析

新上传的目标服务商解析顺序 SHALL 为：请求显式 `storageCode` → 系统默认服务商（`sys_storage_provider.is_default=1` 且启用）→ yml 引导默认。显式 `storageCode` 不存在或对应服务商已停用时 MUST 拒绝上传并返回业务错误码；无任何可用配置时 MUST 明确失败（业务/校验错误），MUST NOT 静默用错误端点重试。读/删**存量**文件 SHALL 按该文件自身的 `storage_code` 解析服务商（停用服务商允许读与逻辑删除，仅禁新上传）。

#### Scenario: 未指定服务商走默认

- **WHEN** 上传请求不带 `storageCode` 且存在启用中的默认服务商
- **THEN** 对象写入默认服务商，元数据登记其 `storage_code`

#### Scenario: 显式服务商已停用

- **WHEN** 上传请求 `storageCode=A` 但 A 已停用
- **THEN** 拒绝上传，业务错误码 + `msg` 提示服务商已停用；不写对象

#### Scenario: 无可用配置

- **WHEN** 库表与 yml 均无可用服务商配置时发起上传
- **THEN** 明确业务失败（可读 `msg`），无静默重试、无对象写入

#### Scenario: 存量文件按归属服务商操作

- **WHEN** 对 `storage_code=A` 的历史文件取 `url` 或执行逻辑删除（A 已停用）
- **THEN** 仍按 A 的 `public_base_url` 拼 URL / 允许逻辑删除，不因停用而失败

### Requirement: 文件列表服务商筛选

`GET /api/v1/files` SHALL 支持按 `storageCode` 筛选（精确匹配归属服务商）；列表出参每条记录 SHALL 含 `storageCode` 字段，便于多服务商排查。

#### Scenario: 按服务商筛选

- **WHEN** 管理员以 `storageCode=A` 筛选文件列表
- **THEN** 仅返回 `storage_code=A` 的有效（`deleted=0`）文件记录，分页结构与 `id` String 约定不变
