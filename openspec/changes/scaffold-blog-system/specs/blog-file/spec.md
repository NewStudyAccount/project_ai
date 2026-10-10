# blog-file · MinIO 文件生命周期

> 策略与封装契约裁剪源：`object-storage-design.md`；管理端交互对齐 `example-system-design.md` §7.1。

## ADDED Requirements

### Requirement: 单文件上传
系统 SHALL 支持单文件上传（`POST /api/v1/files`，multipart）：服务端校验单文件 ≤10MB、MIME 白名单（`image/png,image/jpeg,image/gif,application/pdf`，禁止空=全放行）、UUID 文件名、元数据落 `sys_file`（16 位发号）；上传 SHALL `@RateLimit`（默认形态 10/min/用户）并记录审计。SDK 只经 `{system}-framework` 封装（`object-storage-design.md` §3）。

#### Scenario: 上传成功
- **WHEN** 管理员上传白名单内且 ≤10MB 的文件
- **THEN** 对象写入 MinIO、元数据落库，返回 `FileVO`（`id` String、`bucket`、`objectKey`、`originalName`、`contentType`、`sizeBytes`、`deleted`）

#### Scenario: 超限或类型非法
- **WHEN** 文件超过大小限制或 MIME 不在白名单
- **THEN** 返回 HTTP 200 + 业务/校验段错误码与明确 `msg`，不落库、不写对象（或写后清理一致）

#### Scenario: 触发限流
- **WHEN** 同一用户上传频率超过配置阈值
- **THEN** 返回 HTTP 429 + 系统段 `10003`

### Requirement: 文件查询与预签名
系统 SHALL 提供文件分页列表（`GET /api/v1/files`，可 `@RateLimit`）、元数据详情（`GET /api/v1/files/{id}`）与预签名 URL（`GET /api/v1/files/{id}/url` → `{url, expireAt}`，短 TTL 默认 10min，可 `@RateLimit`）；排序字段白名单，禁 `SELECT *`。

#### Scenario: 分页列表
- **WHEN** 管理员分页查询文件
- **THEN** 返回分页结构，`id` 均为 String

#### Scenario: 获取预签名 URL
- **WHEN** 管理员对有效文件请求预签名 URL
- **THEN** 返回短时有效 URL 与过期时间；对已逻辑删除或不存在记录返回业务段错误码

### Requirement: 文件逻辑删除
系统 SHALL 支持文件逻辑删除（`DELETE /api/v1/files/{id}`）：置 `deleted=1` 作为删除语义（不另设 status 列）；关键写 SHALL `@Idempotent` 并记录审计。MinIO 对象清理走运维脚本，MUST NOT 建定时任务。

#### Scenario: 删除文件
- **WHEN** 管理员删除某文件
- **THEN** 逻辑删除成功并记录审计；重复幂等键返回首次 `Result`

#### Scenario: 重复删除
- **WHEN** 对已逻辑删除文件再次提交（非幂等重放）
- **THEN** 返回业务段错误码或与实现约定一致的明确结果，不抛裸异常

### Requirement: 管理端文件页
`blog-admin` SHALL 提供文件管理页（对齐 `example-system-design.md` §7.1）：上传（前端即时校验大小/类型 + 服务端强校验）、筛选列表（关键字/MIME + 分页）、预签名预览/下载、逻辑删除；预签名 URL 短 TTL、不进前端日志全文。

#### Scenario: 页面操作闭环
- **WHEN** 管理员在文件页完成上传 → 列表筛选 → 预签名访问 → 删除
- **THEN** 与后端契约一致，失败按 `{code,msg,data}` 展示 `msg`

#### Scenario: 前端上传预校验
- **WHEN** 所选文件超过 10MB 或 MIME 不在白名单
- **THEN** 前端即时拒绝并提示约定文案；不发起上传（后端仍二次校验）
