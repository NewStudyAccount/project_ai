## Purpose

约束 blog 后端对象存储服务商 SecretKey（SK）落库方式：从「AES-GCM 加密 + 主密钥环境变量」改为「dev/test 阶段明文直存」，对齐 `docs/object-storage-design.md` §5.2/§5.3/§9。本变更**仅修订 SK 落库与启动期约束**；出参掩码、权限码、表结构、前端 VO 契约等保持不变。生产部署前须另开独立加密变更重引入加密层。

## MODIFIED Requirements

### Requirement: SK 落库方式（明文，dev/test 阶段）

服务商 SecretKey 在 `sys_storage_provider.secret_key` 列**直接以明文形式存储**（dev/test 阶段）；后端 `StorageProviderServiceImpl` 在 `create`/`update` 写入路径中 SHALL 将请求体 `secretKey` 原值直接 `set` 到实体并入库，不经过任何加密/解密中间层；SHALL NOT 读取任何环境变量（含 `OBJECT_STORAGE_SK_KEY`）；SHALL NOT 依赖 `SecretKeyCipher` 或 `secret-crypto` 配置块。

#### Scenario: 创建服务商——SK 明文落库

- **WHEN** 管理端调用 `POST /api/v1/storage/providers` 且 `secretKey` 字段非空
- **THEN** 后端 `StorageProviderServiceImpl.create` 将 `request.secretKey` 原值直接 `set` 到实体并入库
- **AND** 入库后 `sys_storage_provider.secret_key` 列内容 == 请求体 `secretKey` 原值（明文）
- **AND** 不读取任何环境变量，不依赖 `OBJECT_STORAGE_SK_KEY`
- **AND** 响应 VO 的 `secretKeyMasked` 字段返回 `"****"`（非空时）或 `""`（空时），不回显明文

#### Scenario: 更新服务商——SK 明文落库

- **WHEN** 管理端调用 `PUT /api/v1/storage/providers/{id}` 且 `secretKey` 字段非空
- **THEN** 后端 `StorageProviderServiceImpl.update` 将 `request.secretKey` 原值直接 `set` 到实体并更新
- **AND** 入库后 `sys_storage_provider.secret_key` 列内容 == 请求体 `secretKey` 原值（明文）
- **AND** `secretKey` 字段为空/省略时 SHALL NOT 修改原值（保持现有明文 SK 不变）
- **AND** 不读取任何环境变量

#### Scenario: 启动期不依赖环境变量

- **WHEN** 后端 `blog-service` 启动且环境变量 `OBJECT_STORAGE_SK_KEY` 未设置
- **THEN** 启动正常完成，不抛 `MASTER_KEY_MISSING`
- **AND** 不输出任何 SK 加密相关 WARN/ERROR 日志
- **AND** `actuator/health` 返回 UP

## ADDED Requirements

### Requirement: 生产环境重引入加密（约束）

本变更落地后到生产加密重引入之间的窗口期，**生产环境 `sys_storage_provider` 表禁止直接套用 dev/test 明文存储**；生产部署前 MUST 经独立加密变更重引入 SK 加密层（推荐 KMS / 密钥管理服务托管主密钥，而非环境变量）；重引入变更的删除清单 = 反向执行本变更 tasks.md；窗口期内生产 DB 账号 MUST 走最小权限、审计日志、网络隔离。

#### Scenario: 生产重引入路径

- **GIVEN** 本变更已在 dev/test 落地，SK 明文存储
- **WHEN** 准备上生产部署
- **THEN** MUST 先创建独立加密变更（如 `blog-object-storage-reintroduce-sk-encryption`）
- **AND** 该变更的实施步骤 = 反向执行本变更 tasks.md（恢复 `SecretKeyCipher` / `SecretCrypto` / `secret-crypto` 配置 / `encryptSafely` 等）
- **AND** 推荐主密钥来源从「环境变量」改为「KMS / 密钥管理服务」，避免重蹈 dev 环境变量痛点

## UNCHANGED Requirements

### Requirement: 出参掩码（保留）

服务商 VO 的 `secretKeyMasked` 字段在 SK 已配置时返回 `"****"`，未配置时返回 `""`；任何接口 SHALL NOT 返回明文 SK；审计 `detail` 字段 SHALL NOT 包含 SK 任何子串。本项与是否加密落库正交，保持不变。

#### Scenario: 列表/详情接口不回显明文

- **WHEN** 调用 `GET /api/v1/storage/providers` 或 `GET /api/v1/storage/providers/{id}`
- **THEN** 响应中 `secretKeyMasked` 字段值 == `"****"`（已配置）或 `""`（未配置）
- **AND** 响应中不存在 `secretKey` 明文字段
- **AND** 审计 `detail` 中不包含 SK 任何子串

### Requirement: 错误码翻译分支保留

`BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID` 翻译分支中关于「SK 主密钥未配置」的文案 SHALL 保留，运行时不再产生此分支命中，但保留分支以便生产重引入加密层时零迁移复用。`ObjectStorageException.Reason.MASTER_KEY_MISSING` 与 `SECRET_DECRYPT_FAILED` 枚举值 SHALL 保留（避免破坏枚举序），运行时不再产生。

#### Scenario: 删除加密层后不再产生主密钥缺失错误

- **WHEN** 管理端在环境变量未设情况下保存服务商
- **THEN** 保存成功，响应 200
- **AND** 不返回 `PROVIDER_CONFIG_INVALID · "SK 主密钥未配置..."` 错误
