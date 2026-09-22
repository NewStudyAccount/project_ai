# Proposal: add-file-service

## Why

Blog 及后续系统都需要上传图片/附件并写入对象存储（OSS/MinIO）。若把上传埋进 blog，OSS 凭证、类型与大小限制、元数据、删除 GC 会随业务扩散，违背多系统「不嵌套、可复用」边界。  
需要**平台级独立 file 服务**（对齐 auth 模式：单体、可薄），集中持有对象存储能力。

## What Changes

- 新增 `backend/file/file-service` 单体与 **`file_db`**（含 `file_object`、发号）。
- 对接 OSS/MinIO：上传、登记元数据、删除、访问 URL（公开或可签名，接口预留）。
- 校验：content-type/大小白名单；密钥仅 file 服务持有。
- 提供 `biz_type` / `biz_id`，供 blog 等系统挂接。
- **不包含**：图片处理流水线、病毒扫描、完整直传 STS、跨系统业务表。

## Capabilities

### New Capabilities

- `file-object-storage`: 文件上传/元数据/删除/访问；对象键规范；OSS 凭证集中。

### Modified Capabilities

（无。）

## Impact

- **服务**：`file-service`；Nginx 可选 `/file` 直反代。
- **数据**：`file_db.file_object` 等。
- **下游**：blog 只存 object_key，不持有 OSS 密钥。
