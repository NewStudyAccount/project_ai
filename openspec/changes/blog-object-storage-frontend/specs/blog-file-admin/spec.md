## Purpose

约束 blog 管理端文件管理展示页（`/files` → `views/file/FileList.vue`）在对象存储 v3（公开永久 URL + 多服务商）下的对外契约：服务商维度筛选、缩略图与预览下载使用稳定公开 `url`、上传可选目标服务商、删除文案对齐引用检查，对齐 `docs/object-storage-design.md` §7.2 与后端 `blog-file` 契约（`blog-object-storage-public-url` / `blog-object-storage-multi-provider`）。

## MODIFIED Requirements

### Requirement: 文件列表服务商筛选

文件管理展示页 SHALL 在筛选栏提供**服务商**下拉（选项 = 全部 + 当前启用中的 `sys_storage_provider` 列表，`code` 作为值）；选择某服务商后 SHALL 以 `storageCode` 查询参数调用 `GET /api/v1/files` 精确筛选归属该服务商的文件；列表每行 SHALL 展示「服务商 / 桶」列（`storageCode` + `bucket`），便于多服务商排查。服务商下拉选项 MUST 通过 `GET /api/v1/storage/providers?status=1` 拉取启用项，MUST NOT 在前端硬编码服务商实例。

#### Scenario: 按服务商筛选

- **WHEN** 管理员在筛选栏选择服务商 `minio-local` 并点击查询
- **THEN** 列表仅返回 `storage_code=minio-local` 的有效（`deleted=0`）文件，分页结构与 `id` String 约定不变

#### Scenario: 全部服务商

- **WHEN** 筛选栏服务商下拉为「全部」
- **THEN** 不传 `storageCode` 查询参数，返回所有有效文件

#### Scenario: 展示服务商与桶

- **WHEN** 列表渲染某行文件
- **THEN** 「服务商 / 桶」列显示 `storageCode` 与 `bucket`（如 `minio-local / blog`），便于排查归属

### Requirement: 缩略图与预览下载使用稳定公开 URL

文件管理展示页 SHALL 在列表增加**缩略图**列：对 `contentType` 为 `image/*` 的行，直接用列表 VO 内嵌的稳定公开 `url` 作 `<img src>`；非图片行显示类型占位（如 `PDF`/`MD`/`—`）；图片加载失败（对象已物理删除等）SHALL 降级为类型占位，MUST NOT 阻塞列表渲染。预览/下载 SHALL 优先使用行内 `url`；行内 `url` 缺失时回退调用 `GET /api/v1/files/{id}/url` 取 `{ url }`。浏览器直连该 URL（公共读、无过期），MUST NOT 经网关/业务服务中转 body。

#### Scenario: 图片缩略图直显公开 URL

- **WHEN** 列表渲染 `contentType=image/png` 的文件行
- **THEN** 缩略图列 `<img src>` 指向该文件稳定公开 `url`（`publicBaseUrl + objectKey`），无签名 query、无过期

#### Scenario: 非图片类型占位

- **WHEN** 列表渲染 `contentType=application/pdf` 的文件行
- **THEN** 缩略图列显示类型占位 `PDF`，不发起图片请求

#### Scenario: 缩略图加载失败降级

- **WHEN** 图片 `<img>` 的 `src` 加载失败（对象已物理删除）
- **THEN** 该单元格降级为类型占位，列表其它行正常渲染

#### Scenario: 预览与下载

- **WHEN** 管理员点击某行的「预览」或「下载」
- **THEN** 打开该文件稳定公开 `url`（预览新窗口、下载触发落盘），落盘/内嵌行为由上传时写入的 `Content-Disposition` 决定（image/pdf=`inline` 内嵌，其它=`attachment` 下载）；前端不强制改写

### Requirement: 上传可选目标服务商

文件管理展示页的上传对话框 SHALL 增加**目标服务商**下拉（选项 = 当前启用中的 `sys_storage_provider`，默认选中 `is_default=1` 的默认服务商）；提交时 SHALL 以 multipart form 字段 `storageCode` 传递给 `POST /api/v1/files`；未配置任何启用服务商时 SHALL 禁用上传按钮并提示「无可用服务商，请先在存储服务商配置页新增并启用」。前端 SHALL 即时校验单文件 ≤10MB 与 MIME 白名单（`image/png,image/jpeg,image/gif,application/pdf`），后端强校验失败时展示 `msg`。

#### Scenario: 选择目标服务商上传

- **WHEN** 管理员在上传对话框选择服务商 `aws-sg` 并提交白名单内且 ≤10MB 的文件
- **THEN** 对象写入 `aws-sg`，元数据 `storage_code=aws-sg`，列表刷新后该行 `url` 指向 `aws-sg` 的 `publicBaseUrl`

#### Scenario: 默认服务商上传

- **WHEN** 上传对话框目标服务商下拉保持默认（未手动切换）
- **THEN** 提交时 `storageCode` 取默认服务商，对象写入默认服务商

#### Scenario: 无可用服务商

- **WHEN** 系统无任何启用中的服务商时打开上传对话框
- **THEN** 目标服务商下拉为空、上传按钮禁用、提示「无可用服务商」

#### Scenario: 超限或类型非法

- **WHEN** 文件超过 10MB 或 MIME 不在白名单
- **THEN** 前端即时提示错误，不发起上传请求

### Requirement: 删除文案对齐引用检查

文件管理展示页的删除确认文案 SHALL 强调**逻辑删除**（`deleted=1`，公开 URL 在物理清理前可能仍可访问）与**删除前引用检查**（被正文/业务引用时拒绝删除并返回引用摘要）；删除调用 `DELETE /api/v1/files/{id}`（后端已做引用检查）；被引用拒绝时前端 SHALL 展示后端返回的 `msg`（含引用摘要），MUST NOT 静默成功。

#### Scenario: 删除确认文案

- **WHEN** 管理员点击某行的「删除」
- **THEN** 确认框文案包含「逻辑删除」「公开 URL 在物理清理前可能仍可访问」「删除前会做引用检查，被引用时拒绝」三项要点

#### Scenario: 被引用拒绝删除

- **WHEN** 删除的文件被文章正文/封面引用
- **THEN** 后端返回业务错误码 + `msg`（含引用摘要），前端 `ElMessage.error` 展示该 `msg`，列表不变

#### Scenario: 无引用删除成功

- **WHEN** 删除的文件无任何引用
- **THEN** 逻辑删除成功，列表刷新后该行不可见
