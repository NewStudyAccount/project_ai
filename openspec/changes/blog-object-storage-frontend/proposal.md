## Why

`docs/object-storage-design/object-storage-design.md` §7（管理端前端设计）裁决了对象存储「管理端双页」：**文件管理展示页**（`/files` → `views/file/FileList.vue`）与**对象存储服务商配置页**（`/storage/providers` → `views/storage/StorageProviderList.vue`）。设计 v3 将访问模型改为「公开永久 URL」、并新增「服务商维度」与「服务商配置管理」能力。

后端契约已由两条已落地变更交付，二者**明确将前端排除在外**：

- `blog-object-storage-public-url`（已归档前）：`{ url }` 公开 URL、`Content-Disposition`、删除前引用检查
- `blog-object-storage-multi-provider`（已落地，tasks 全勾）：`sys_storage_provider` 库表、S3 多服务商、`sys_file.storage_code`、`GET/POST /storage/providers` 全套管理 API、`POST /files` 可选 `storageCode`、`GET /files` 筛选 `storageCode`、`FileVO` 出参含 `storageCode`

`frontend/blog-admin` 现状仍是 v1/v2 单服务商 MinIO 形态：

- `src/api/file.ts`：`pageFiles`/`uploadFile` 无 `storageCode`；`FileMeta` 无 `storageCode` 字段
- `src/views/file/FileList.vue`：无服务商筛选、无缩略图列、无「服务商/桶」列、上传对话框无目标服务商选择、预览/下载每次调 `getFileUrl`（未利用列表 VO 内嵌的稳定 `url`）、删除文案仅提「MinIO 对象延后清理」未提引用检查
- 无 `src/api/storageProvider.ts`、无 `src/views/storage/` 目录、路由无 `/storage/providers`
- `MdEditor.vue` 已用 `meta.url` 写正文插图（兼容新契约，不动）

与唯一裁剪源 §7 不一致，无法支撑多服务商排查与服务商运行时配置管理。

## What Changes

- **重写文件管理展示页**（`views/file/FileList.vue`，对齐设计 §7.2）：
  - 统计条增加「默认服务商」卡（取启用中的默认服务商展示 code + 桶）
  - 筛选栏增加**服务商**下拉（全部 / 各启用中 `sys_storage_provider`）
  - 列表增加**缩略图**列（`image/*` 用稳定公开 `url` 作 `<img src>`；非图片显示类型占位如 PDF/MD；加载失败降级占位）
  - 列表增加**「服务商 / 桶」**列（`storageCode` + `bucket`，便于多服务商排查）
  - 预览/下载改用行内 `url`（列表 VO 内嵌；缺省回退 `GET /files/{id}/url`）；落盘/内嵌行为由上传时写入的 `Content-Disposition` 决定
  - 上传对话框增加**目标服务商**下拉（默认系统默认服务商；仅启用中的服务商；停用项灰显不可选）
  - 删除确认文案强调**逻辑删除 + 删除前引用检查**（被正文/业务引用时弹出引用摘要并拒绝）
- **新建存储服务商配置页**（`views/storage/StorageProviderList.vue`，对齐设计 §7.3）：
  - P-List + D-Form（新建/编辑用对话框）
  - 筛选栏：关键字（code/名称）、`provider_type`、状态
  - 列表列：编码/名称、类型标签、endpoint/桶、region/path-style 摘要、密钥掩码、状态胶囊+默认标记、操作
  - 表单字段：code、name、provider_type、endpoint、region（按类型必填）、bucket、access_key、secret_key（密码框，编辑留空=不修改）、path_style、public_base_url、status、is_default、description
  - 操作：测试连通、编辑、设为默认、启用/停用、删除（含删除保护提示「无活跃文件引用才允许，否则建议停用」）
  - SK 安全：编辑回显只用 `secretKeyMasked`；表单 `secretKey` 为只写；禁止明文写入 localStorage/控制台
- **API 与类型扩展**：
  - `src/api/file.ts`：`pageFiles` 增加可选 `storageCode`；`uploadFile` 增加可选 `storageCode`
  - 新建 `src/api/storageProvider.ts`：列表/详情/创建/更新/删除/测试连通/设默认/启停，全部经 `src/api/request` 统一解包
  - `src/types/index.ts`：`FileMeta` 加 `storageCode`；新增 `StorageProviderVO`/`StorageProviderPageQuery`/`StorageProviderInput` 类型与 `PROVIDER_TYPES` 常量
- **路由接线**：`src/router/index.ts` 新增静态路由 `/storage/providers`（name `StorageProviders` → `views/storage/StorageProviderList.vue`）并加入 `componentByPath` 映射（与 `files` 同模式，RBAC 菜单下发后动态路由复用同组件）
- **权限**：按钮显隐走 `hasPermission('blog:file:*' | 'blog:storage-provider:*')`；菜单由后端 RBAC 下发，前端不硬编码角色（CLAUDE.md §5.2）

**Non-goals / 边界（对齐 CLAUDE.md §1、object-storage-design §1.2、§7）：**

- 不改后端 API 契约（后端已由 `blog-object-storage-multi-provider` / `blog-object-storage-public-url` 落地，本变更只消费契约）
- 不做浏览器直传 `presignPut`、批量上传、对象迁移搬迁、图片压缩/水印、Excel、定时清理（均属后端/运维边界）
- 不引入第二套 UI 库 / 状态库 / axios 替代；不重构既有 `request.ts`/`AppLayout.vue`/`MdEditor.vue`
- 不在前端硬编码 endpoint/AK/SK；服务商只通过管理 API 维护，不写死服务商列表（`PROVIDER_TYPES` 仅作表单下拉枚举，与后端 `provider_type` 一致）
- 不改其它系统前端；不跨系统直连

## Capabilities

### New Capabilities

- `blog-storage-provider-admin`：对象存储服务商配置管理端——列表筛选、新建/编辑表单（含 SK 只写与掩码回显）、连通性测试、设默认、启停、删除保护提示，对齐设计 §7.3 与后端 `blog-storage-provider` 契约。

### Modified Capabilities

> `openspec/specs/` 尚无已归档同名能力；下列为对未归档变更中规格的前端收敛。实施以本变更 specs 为准；归档时与 `scaffold-blog-system` / `blog-object-storage-public-url` / `blog-object-storage-multi-provider` 中 `blog-file` 的前端 delta 合并。

- `blog-file-admin`（见 `scaffold-blog-system` 前端部分）：文件管理展示页增加**服务商维度**——筛选 `storageCode`、列表展示 `storageCode`/`bucket`、缩略图用稳定公开 `url`、预览/下载用行内 `url`、上传对话框可选目标服务商、删除文案对齐引用检查；行为契约不变部分（单文件 ≤10MB、MIME 白名单、公开 URL、逻辑删除）保持不动。

## Impact

- **代码（`frontend/blog-admin`）**
  - `src/types/index.ts`：`FileMeta` 加 `storageCode?: string`；新增 `StorageProviderVO`/`StorageProviderPageQuery`/`StorageProviderInput`/`ProviderType` 类型与 `PROVIDER_TYPES` 常量
  - `src/api/file.ts`：`pageFiles`/`uploadFile` 增加 `storageCode` 可选参数
  - 新建 `src/api/storageProvider.ts`：`storageProviderApi`（page/get/create/update/delete/testConnection/setDefault/updateStatus/listEnabled）
  - 重写 `src/views/file/FileList.vue`：统计条/筛选栏/列表/上传对话框/预览下载/删除按 §7.2 重设计
  - 新建 `src/views/storage/StorageProviderList.vue`：按 §7.3 全功能配置页
  - `src/router/index.ts`：新增 `storage/providers` 静态子路由与 `componentByPath` 映射
- **接口（消费侧，非破坏性）**：`GET /api/v1/files?storageCode=`、`POST /api/v1/files`（multipart 加 `storageCode`）、`/api/v1/storage/providers` 全套——均已在后端变更交付，本变更只接前端
- **数据**：无（后端 `sys_storage_provider` / `sys_file.storage_code` 已由后端变更落地）
- **配置**：无（前端无对象存储直连配置；服务商经管理 API 维护）
- **文档**：本变更制品（proposal/design/specs/tasks）即文档；不修改 `docs/object-storage-design` 已裁决正文
- **边界**：仅 `frontend/blog-admin`；不改其它系统前端、不跨系统直连、不碰后端
