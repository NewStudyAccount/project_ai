## Purpose

约束 blog 对象存储服务商配置管理的对外契约：S3 多服务商配置的库表真相源、密钥安全（加密落库/出参掩码）、连通性测试、启停与默认项、删除保护与缓存失效语义，对齐 `docs/object-storage-design.md` §5.2/§8.2/§9。

## ADDED Requirements

### Requirement: 服务商配置库表与密钥安全

系统 SHALL 以 `sys_storage_provider` 库表为服务商配置的运行时真相源（yml 引导配置仅用于冷启动/无库开发，DB 配置优先）。`secret_key` MUST 加密落库（AES-GCM 或同级），加密主密钥 MUST 来自环境变量或配置中心、MUST NOT 入仓；任何接口 MUST NOT 返回明文 SK（只回掩码，如 `****`）；应用日志与审计 `detail` MUST NOT 出现 SK。创建/更新时 `secretKey` 为只写字段：传空表示不修改，非空则加密存储后丢弃明文。

#### Scenario: 列表与详情出参脱敏

- **WHEN** 已登录用户请求 `GET /api/v1/storage/providers` 或 `GET /api/v1/storage/providers/{id}`
- **THEN** 出参含 `secretKeyMasked`（掩码），不含明文 SK；`id` 为 String

#### Scenario: 编辑时密钥留空

- **WHEN** 更新服务商时 `secretKey` 传空字符串
- **THEN** 系统保留原密文不修改，其余字段正常更新

#### Scenario: 密钥不入日志与审计

- **WHEN** 服务商创建/更新/连通性测试触发日志与审计记录
- **THEN** 日志与审计 `detail` 中无明文 SK、无完整凭证

#### Scenario: yml 引导与库表优先级

- **WHEN** yml 引导默认服务商与 `sys_storage_provider` 同时存在
- **THEN** 运行时解析以库表配置为准，yml 仅作冷启动引导

### Requirement: 服务商配置 CRUD

系统 SHALL 提供服务商配置管理 API：`POST /api/v1/storage/providers`（新建）、`GET /api/v1/storage/providers`（分页列表，可按关键字/`provider_type`/状态筛选）、`GET /api/v1/storage/providers/{id}`（详情）、`PUT /api/v1/storage/providers/{id}`（更新）。`code` 系统内唯一；`provider_type` 取值限定 `MINIO` / `AWS_S3` / `ALIYUN_OSS` / `TENCENT_COS` / `HUAWEI_OBS` / `CUSTOM_S3`；`endpoint`、`bucket`、`public_base_url` 必填；`region` 按类型必填（如 AWS/OSS/COS/OBS 必填、MinIO 可空）；主键 16 位发号；写接口 SHALL `@RateLimit` 并记录操作审计（关键写 `@Idempotent`）。

#### Scenario: 新建服务商

- **WHEN** 管理员提交合法服务商配置（code 唯一、必填齐全、类型合法）
- **THEN** 创建成功，SK 加密落库，返回出参含 `secretKeyMasked`、不含明文

#### Scenario: code 重复

- **WHEN** 新建时 `code` 与已有服务商重复
- **THEN** HTTP 200 + 业务错误码，`msg` 明确提示编码已存在

#### Scenario: 类型必填项校验

- **WHEN** 新建 `AWS_S3` 类型但 `region` 为空
- **THEN** 返回校验段错误码并指明 `region` 必填；不落库

#### Scenario: 分页筛选

- **WHEN** 管理员按 `provider_type=MINIO` 与状态筛选服务商列表
- **THEN** 返回分页结构（`id` String、SK 掩码），命中条件的服务商记录

### Requirement: 服务商连通性测试

系统 SHALL 提供连通性测试 `POST /api/v1/storage/providers/{id}/test`：以该服务商配置探测桶可达性（`headBucket` 或等价探测）；成功返回成功 `Result`；失败返回业务/校验错误并带**可读原因**（网络不通/签名失败/桶不存在/无权限等），MUST NOT 在错误详情中回显 SK 或带凭证的完整 endpoint；测试后记录审计。

#### Scenario: 测试成功

- **WHEN** 配置正确的 MinIO 服务商执行「测试连通」
- **THEN** 返回成功 `Result`，审计记录该动作

#### Scenario: 测试失败且原因可读

- **WHEN** endpoint 不可达或 AK/SK 错误
- **THEN** HTTP 200 + 业务错误码，`msg` 含可读失败原因（如「签名失败，请检查 AccessKey/SecretKey」），不含 SK 明文

### Requirement: 启用停用与默认服务商

系统 SHALL 支持服务商启用/停用（`PUT /api/v1/storage/providers/{id}/status`）与设默认（`POST /api/v1/storage/providers/{id}/default`）。同库仅一条 `is_default=1`；`status=0`（停用）后 MUST 立即禁止新上传走该服务商，但存量文件 MUST 仍可读 URL/逻辑删除；切换默认服务商 MUST 只影响新上传，历史文件 `url` 仍按其 `storage_code` 回源原服务商，MUST NOT 批量改写历史 URL。默认变更、启停 SHALL 记审计并使该服务商的客户端缓存失效（后续操作按新配置建连）。

#### Scenario: 停用后禁止新上传

- **WHEN** 服务商 A 已停用，上传请求显式指定 `storageCode=A` 或 A 仍为默认
- **THEN** 新上传拒绝（业务错误码），提示该服务商已停用

#### Scenario: 停用后存量可读

- **WHEN** 服务商 A 停用后读取 `storage_code=A` 的历史文件 `url`
- **THEN** 仍按 A 的 `public_base_url` 拼接返回稳定 URL，读取不受影响

#### Scenario: 切换默认不影响历史

- **WHEN** 默认服务商由 A 切为 B 后读取历史（A 上传的）文件列表
- **THEN** 历史 `url` 仍指向 A 的 `public_base_url`；仅新上传走 B

#### Scenario: 配置变更后客户端缓存失效

- **WHEN** 服务商 endpoint/AK 更新成功后立刻执行新上传或连通性测试
- **THEN** 系统按**新配置**建连（不沿用旧缓存实例）；新配置签名错误时以连通性/上传失败可读原因暴露

#### Scenario: 唯一默认项

- **WHEN** 将服务商 B 设为默认且 A 原为默认
- **THEN** 事务内 A 的 `is_default` 置 0、B 置 1，同库始终仅一条默认

### Requirement: 服务商删除保护

系统 SHALL 支持服务商逻辑删除 `DELETE /api/v1/storage/providers/{id}`：仅当**无** `deleted=0` 的 `sys_file` 记录引用该服务商（`storage_code` 匹配）时允许删除；否则返回业务错误码并提示改用停用。删除 SHALL `@Idempotent` 并记审计。

#### Scenario: 存在活跃文件引用

- **WHEN** 有 `deleted=0` 文件的 `storage_code` 指向该服务商时执行删除
- **THEN** HTTP 200 + 业务错误码，`msg` 提示存在引用文件建议停用；配置保留

#### Scenario: 无引用时删除

- **WHEN** 无任何 `deleted=0` 文件引用该服务商
- **THEN** 逻辑删除成功（`deleted=1`）并记审计；重复幂等键返回首次 `Result`
