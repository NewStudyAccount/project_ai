## Purpose

约束 blog 管理端对象存储服务商配置页（`/storage/providers` → `views/storage/StorageProviderList.vue`）的对外契约：列表筛选、新建/编辑表单（含 SK 只写与掩码回显）、连通性测试、设默认、启停、删除保护提示，对齐 `docs/object-storage-design.md` §7.3 与后端 `blog-storage-provider` 契约（`blog-object-storage-multi-provider`）。

## ADDED Requirements

### Requirement: 服务商列表与筛选

存储服务商配置页 SHALL 提供列表（`GET /api/v1/storage/providers` 分页），筛选栏支持关键字（匹配 `code`/`name`）、`provider_type`（六类枚举）、状态（启用/停用）。列表每行 SHALL 展示：编码/名称、类型标签、endpoint/桶、region/path-style 摘要、密钥掩码 `secretKeyMasked`（只读，MUST NOT 显示明文 SK）、状态胶囊（启用/停用）+ 默认标记、操作列。出参 `id` 一律 String；请求只经 `src/api/storageProvider.ts`。

#### Scenario: 列表展示与掩码

- **WHEN** 管理员进入服务商配置页
- **THEN** 列表展示所有 `deleted=0` 服务商，密钥列显示 `secretKeyMasked`（如 `****2345`），不含明文 SK

#### Scenario: 按类型与状态筛选

- **WHEN** 管理员筛选 `provider_type=MINIO` 且状态=停用
- **THEN** 列表仅返回符合条件的服务商分页记录

#### Scenario: 默认标记

- **WHEN** 列表渲染 `is_default=1` 的服务商行
- **THEN** 状态列附加「默认」标记（与启用胶囊并列）

### Requirement: 服务商新建与编辑表单

新建/编辑表单（`el-dialog` + `el-form`）SHALL 包含字段：`code`（必填、小写连字符格式提示）、`name`（必填）、`provider_type`（必填、六类下拉）、`endpoint`（必填、http(s)）、`region`（按类型必填：AWS/OSS/COS/OBS 必填、MinIO 可空）、`bucket`（必填）、`access_key`（必填）、`secret_key`（密码框、新建必填、编辑留空=不修改）、`path_style`（开关，类型切换时给推荐默认）、`public_base_url`（必填、http/https 均可、无尾斜杠）、`status`（开关启用/停用）、`is_default`（开关，开启时确认「仅影响新上传，历史文件仍按 `storage_code` 回源原服务商」）、`description`（可选）。新建调 `POST /api/v1/storage/providers`，编辑调 `PUT /api/v1/storage/providers/{id}`。

#### Scenario: 新建服务商

- **WHEN** 管理员提交合法新建表单（code 唯一、必填齐全）
- **THEN** 创建成功，SK 加密落库，列表刷新后展示新服务商，密钥列显示掩码

#### Scenario: code 重复

- **WHEN** 新建时 `code` 与已有服务商重复
- **THEN** 后端返回业务错误码 `203081`，前端展示 `msg`「编码已存在」，不关闭对话框

#### Scenario: 类型必填项校验

- **WHEN** 新建 `AWS_S3` 类型但 `region` 为空
- **THEN** 前端表单校验或后端校验段错误码拦截，提示 `region` 必填

#### Scenario: 编辑时密钥留空不修改

- **WHEN** 编辑服务商时 `secret_key` 留空提交
- **THEN** 后端保留原密文不修改，其余字段正常更新，密钥列掩码不变

#### Scenario: 设为默认确认

- **WHEN** 编辑表单开启 `is_default` 开关并保存
- **THEN** 弹出确认框「设为默认仅影响新上传，历史文件仍按其 `storage_code` 回源原服务商」；确认后提交，列表刷新后该行带默认标记

### Requirement: 连通性测试

列表操作列 SHALL 提供「测试连通」按钮（权限 `blog:storage-provider:test`），调用 `POST /api/v1/storage/providers/{id}/test`；成功 toast「测试连通成功」；失败 toast 后端返回的可读原因（`msg`，如「签名失败，请检查 AccessKey/SecretKey」），MUST NOT 在前端展示明文 SK 或带凭证的完整 endpoint。

#### Scenario: 测试成功

- **WHEN** 管理员点击配置正确的 MinIO 服务商的「测试连通」
- **THEN** toast「测试连通成功」，列表不变

#### Scenario: 测试失败原因可读

- **WHEN** endpoint 不可达或 AK/SK 错误时点击「测试连通」
- **THEN** toast 后端返回的 `msg`（可读失败原因），不含 SK 明文

### Requirement: 启用停用与设默认操作

列表操作列 SHALL 提供「设默认」（`POST /{id}/default`，权限 `blog:storage-provider:update`）、「启用/停用」（`PUT /{id}/status`，权限 `blog:storage-provider:update`）。设默认前 SHALL 弹出确认框（仅影响新上传）；停用前 SHALL 提示「停用后禁止新上传，存量文件仍可读 URL/逻辑删除」。按钮显隐按 `hasPermission` 控制。

#### Scenario: 设为默认

- **WHEN** 管理员点击非默认服务商行的「设默认」并确认
- **THEN** 后端事务内唯一 `is_default=1`，列表刷新后该行带默认标记、原默认行去除标记

#### Scenario: 停用服务商

- **WHEN** 管理员点击启用服务商行的「停用」并确认
- **THEN** 后端 `status=0`，列表刷新后该行状态胶囊变停用；该服务商禁止新上传（文件页上传下拉不再列出）

#### Scenario: 启用服务商

- **WHEN** 管理员点击停用服务商行的「启用」
- **THEN** 后端 `status=1`，列表刷新后该行状态胶囊变启用；文件页上传与筛选下拉重新列出

### Requirement: 删除保护提示

列表操作列 SHALL 提供「删除」（`DELETE /api/v1/storage/providers/{id}`，权限 `blog:storage-provider:delete`）。后端删除保护：存在 `deleted=0` 的 `sys_file.storage_code` 引用时返回业务错误码 `203084` 并提示「建议停用」；前端 SHALL 展示该 `msg`，MUST NOT 静默成功或重试。

#### Scenario: 存在活跃文件引用

- **WHEN** 管理员点击被文件引用的服务商行的「删除」
- **THEN** 后端返回业务错误码 `203084`，前端 toast `msg`（含引用文件数/建议停用），配置保留

#### Scenario: 无引用时删除

- **WHEN** 管理员点击无任何文件引用的服务商行的「删除」并确认
- **THEN** 后端逻辑删除成功（`deleted=1`），列表刷新后该行不可见
