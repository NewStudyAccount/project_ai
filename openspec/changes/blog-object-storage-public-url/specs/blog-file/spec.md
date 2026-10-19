## Purpose

约束 blog 文件管理与对象访问的对外契约：上传登记、稳定公开 URL、对象元数据、删除前引用检查，对齐 `docs/object-storage-design.md` v3（公开永久 URL）。

## ADDED Requirements

### Requirement: 文件访问使用稳定公开 URL

系统 SHALL 为每个有效文件提供稳定公开地址 `url = publicBaseUrl + '/' + objectKey`（`publicBaseUrl` 取自该文件 `storage_code` 对应服务商配置；禁止签名 query、禁止 `expireAt`）。列表与详情出参 SHALL 含 `url`。

#### Scenario: 取文件地址

- **WHEN** 已登录用户请求 `GET /api/v1/files/{id}/url` 或读取文件列表/详情
- **THEN** 返回 `Result{ code:0, data:{ url } }`（或 VO 字段 `url`），`url` 形如 `{publicBaseUrl}/{objectKey}`，不含 `expireAt`、不含签名参数

#### Scenario: 跨服务商历史文件

- **WHEN** 默认服务商已切换，读取历史文件的 `url`
- **THEN** 仍按该文件自身的 `storage_code` 解析 `publicBaseUrl` 拼接，不随默认服务商变更

#### Scenario: 预签名响应已移除

- **WHEN** 客户端请求文件访问地址
- **THEN** 系统不返回短 TTL 预签名结果，不要求前端重签或刷新 `src`

### Requirement: 上传写入 Content-Disposition

上传对象时系统 SHALL 同步写入对象元数据 `Content-Type` 与 `Content-Disposition`：`content_type` 为 `image/*` 或 `application/pdf` 时为 `inline`，其它类型为 `attachment; filename="<original_name>"`（文件名按 RFC 5987）。

#### Scenario: 图片上传

- **WHEN** 上传 `image/png` 文件 `cover.png` 成功
- **THEN** 对象元数据 `Content-Disposition` 为 `inline`，浏览器直接打开 `url` 时内嵌预览

#### Scenario: 非图片上传

- **WHEN** 上传 `application/zip`（若白名单允许）或其它非 image/pdf 文件
- **THEN** 对象元数据 `Content-Disposition` 为 `attachment` 且带原始文件名，浏览器打开 `url` 时倾向下载

### Requirement: 删除前引用检查

业务删除文件（逻辑删除）前系统 SHALL 执行引用检查；存在业务引用时 MUST 拒绝删除并返回可读引用摘要；无引用才允许 `deleted=1`。

#### Scenario: 被文章封面引用时删除

- **WHEN** 对仍被某文章 `cover_file_id` 引用的文件调用 `DELETE /api/v1/files/{id}`
- **THEN** HTTP 200 + 业务错误码，`msg` 含引用摘要（如「文章封面占用」），`sys_file.deleted` 仍为 0

#### Scenario: 被正文或草稿引用时删除

- **WHEN** 对 `content_file_id` 或 `draft_file_id` 仍指向的正文对象调用删除
- **THEN** 拒绝删除并提示被正文/草稿占用

#### Scenario: 无引用时删除

- **WHEN** 文件未被任何文章字段或 `biz_type`/`biz_id` 业务行引用
- **THEN** 逻辑删除成功（`deleted=1`），返回成功 `Result`；对象本体不在本接口物理删除

#### Scenario: 物理清理二次检查

- **WHEN** 运维脚本对 `deleted=1` 的对象执行物理清理
- **THEN** 清理前再次引用检查；仍被引用的对象跳过并记入失败/跳过清单
