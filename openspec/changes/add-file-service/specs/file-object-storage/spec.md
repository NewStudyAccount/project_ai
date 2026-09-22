# Spec: file-object-storage

## ADDED Requirements

### Requirement: 独立文件服务与对象存储

系统 SHALL 以独立 `file-service` 提供文件能力，独占 `file_db`；对象存储凭证 SHALL NOT 出现在业务系统配置中。

#### Scenario: 业务系统不持有 OSS 密钥

- **WHEN** blog 等业务需要存图或内容文件
- **THEN** 通过 file 服务 API 上传，仅保存 object_key/URL

### Requirement: 上传与元数据

系统 SHALL 支持上传文件、登记 `file_object`，并返回可访问 URL 或 key。

#### Scenario: 上传成功

- **WHEN** 上传合法类型与大小内的文件
- **THEN** 写入对象存储并创建元数据，返回 object_key 与 url

#### Scenario: 类型或大小拒绝

- **WHEN** 文件类型不在白名单或超过大小限制
- **THEN** 拒绝上传并返回业务错误

### Requirement: 对象键稳定可覆盖

内容类对象 SHALL 使用稳定 key，支持发布流水线幂等覆盖上传。

#### Scenario: 覆盖发布 HTML

- **WHEN** 对同一 post 重新发布并上传 content.html
- **THEN** 覆盖原 object_key，元数据更新 size/时间

### Requirement: 删除与关联查询

系统 SHALL 支持按 id/key 删除；SHALL 支持按 biz_type/biz_id 查询对象以辅助清理。

#### Scenario: 按业务清理

- **WHEN** 文章删除后按 biz_id 查询 file 对象
- **THEN** 可列出关联 object 并删除，避免孤儿文件
