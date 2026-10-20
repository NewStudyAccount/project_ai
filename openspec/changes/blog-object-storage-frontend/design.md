## Context

`backend/blog` 对象存储后端契约已由两条已落地变更交付：

- `blog-object-storage-public-url`：访问模型为「公开永久 URL」（`{ url }`，无 `expireAt`、无签名 query）；上传写 `Content-Disposition`（image/pdf → `inline`，其它 → `attachment; filename=原名`）；删除前 `FileReferenceChecker` 引用检查（被引用拒绝删除并返回引用摘要）。
- `blog-object-storage-multi-provider`：独立模块 `blog-object-storage`（SDK 唯一落点）；`sys_storage_provider` 库表为运行时配置真相源（SK AES-GCM 加密落库、出参掩码 `secretKeyMasked`）；`sys_file.storage_code` 归属登记；S3 多服务商六类 `provider_type`；管理 API `/api/v1/storage/providers` 全套；`POST /files` 可选 `storageCode`、`GET /files` 可按 `storageCode` 筛选、`FileVO` 出参含 `storageCode`。

本变更只在 `frontend/blog-admin` 落地设计 §7「管理端双页」，纯消费上述契约。前端基线：Vue3 + TS + Pinia + Element Plus + Vite（CLAUDE.md §2 / §5.5）；axios 统一解包 `{code,msg,data}` 并按 `code` 分流（§5.3）；权限标识 `system:resource:action`，按钮走 `hasPermission`，菜单由后端 RBAC 下发、前端动态生成（§5.2）。

约束：`id` 一律 `string`（CLAUDE.md §6.4.7 Long 序列化）；请求只经 `src/api`（§5.5）；表单提交可带 `Idempotent-Key`（§6.11，由 `request.ts` 的 `X-Request-Id` 兜底）；不引入新依赖；不硬编码角色/endpoint/AK/SK。

## Goals / Non-Goals

**Goals:**

- 文件管理展示页按 §7.2 重设计：服务商筛选、缩略图列、服务商/桶列、上传选服务商、预览下载用行内 `url`、删除文案对齐引用检查
- 服务商配置页按 §7.3 新建：列表筛选、CRUD 表单（SK 只写+掩码回显）、测试连通、设默认、启停、删除保护提示
- API/类型/路由扩展最小集，复用既有 `request.ts`/`AppLayout.vue`/`hasPermission`
- 编译、lint、type-check 通过（CLAUDE.md §3.2）

**Non-Goals:**

- 不改后端、不改既有 `request.ts`/`AppLayout.vue`/`MdEditor.vue`、不引入新依赖
- 不做批量上传/直传/图片加工/定时清理（后端/运维边界）
- 不在前端硬编码服务商实例或密钥

## Decisions

### D1：API/类型扩展（最小侵入）

- `FileMeta` 增加可选 `storageCode: string`（后端列表 VO 已含；旧记录空字符串对齐）。
- `pageFiles` 签名扩展为 `{ current?, size?, keyword?, contentType?, storageCode? }`；`uploadFile` 扩展为 `(file, bizType?, bizId?, storageCode?)`，`storageCode` 经 multipart form 字段传递（与后端 `POST /files` 可选 `storageCode` 一致）。
- 新建 `StorageProviderVO`（出参，含 `secretKeyMasked`、不含明文 SK）、`StorageProviderPageQuery`（`keyword`/`providerType`/`status`/`current`/`size`）、`StorageProviderInput`（入参，`secretKey` 可选——空=不改）。
- 新建 `storageProvider.ts`：`storageProviderApi.page/get/create/update/delete/testConnection/setDefault/updateStatus/listEnabled`（`listEnabled` 用于文件页上传对话框与筛选下拉，调 `GET /storage/providers?status=1&size=100` 取启用项，前端缓存到组件 ref，不进 Pinia——单纯展示用，无跨页共享需求）。
- **备选**「服务商列表进 Pinia」被否：仅文件页与服务商页用，且服务商页本身就要分页查全量；进 store 增加缓存失效心智负担，与「状态共享才进 Pinia」（§5.5）不符。

### D2：文件页服务商下拉与默认服务商展示

- 文件页 `onMounted` 调 `storageProviderApi.listEnabled()` 拉启用项（含默认），填筛选下拉与上传对话框「目标服务商」下拉。
- 统计条「默认服务商」卡：从启用列表中取 `isDefault === 1` 的一条展示 `code · 桶 bucket`；无默认则显示「未配置」。
- 上传对话框「目标服务商」下拉：默认选中默认服务商；停用项不在候选（`listEnabled` 已过滤）；未配置任何启用服务商时下拉为空并禁用上传按钮、提示「无可用服务商」。
- 筛选下拉「服务商：全部」+ 启用项 `code`（与后端 `GET /files?storageCode=` 精确匹配一致）。

### D3：缩略图与预览下载的 URL 使用

- 列表 VO 已内嵌 `url`（后端 `FileVO` 含稳定公开 `url`，设计 §7.2「列表 VO 可直接内嵌 url 字段」）。缩略图列对 `image/*` 行直接用 `row.url` 作 `<img src>`；非图片显示类型占位（`PDF`/`MD`/`—`）；`<img>` 加 `@error` 降级为类型占位（对象已物理删除等场景），不阻塞列表。
- 预览/下载优先用 `row.url`；若列表 VO 未带 `url`（兼容旧接口），回退调 `getFileUrl(row.id)` 取 `{ url }`。落盘/内嵌行为由上传时写入的 `Content-Disposition` 决定（image/pdf=`inline` 内嵌预览，其它=`attachment` 下载），前端不强制改写。
- **不做**正文配图改造：`MdEditor.vue` 已用 `meta.url` 写稳定公开 URL，与新契约兼容，本变更不动。

### D4：上传对话框增加目标服务商选择

- `el-upload` 拖拽单文件（沿用既有交互）；对话框增加「目标服务商」下拉（D2 启用项），可选 `bizType`（沿用既有）。
- 前端即时校验大小 ≤10MB 与 MIME 白名单（`image/png,image/jpeg,image/gif,application/pdf`），后端仍强校验（失败展示 `msg`，§5.3）。
- 提交 `uploadFile(file, bizType, bizId, storageCode)`；成功刷新列表。

### D5：删除文案与引用检查对齐

- `FileList.vue` 删除确认文案改为：「确认删除「{originalName}」？此为**逻辑删除**（`deleted=1`），公开 URL 在物理清理前可能仍可访问；**删除前会做引用检查**，被正文/业务引用时将拒绝删除并返回引用摘要。」
- 删除接口 `DELETE /files/{id}` 后端已做引用检查（`blog-object-storage-public-url` 落地）；前端只需把拒绝的 `msg`（含引用摘要）展示给用户（`request.ts` 已统一 `ElMessage.error(result.msg)`，无需特殊处理）。

### D6：服务商配置页表单与 SK 安全

- 表单用 `el-dialog`（宽度 560px）+ `el-form` + Bean Validation 风格的前端校验（必填、`code` 小写连字符、`endpoint` http(s)、`public_base_url` 无尾斜杠）。
- `provider_type` 下拉枚举固定六类（`MINIO`/`AWS_S3`/`ALIYUN_OSS`/`TENCENT_COS`/`HUAWEI_OBS`/`CUSTOM_S3`，作为前端常量 `PROVIDER_TYPES`，与后端枚举一致——非自造，对齐后端契约）；切换类型时按推荐默认调整 `path_style` 与 `region` 必填提示（MinIO path-style 默认开、region 可空；AWS/OSS/COS/OBS region 必填）。
- `secret_key`：密码框；新建必填；编辑留空=不修改（占位符显示 `留空不修改`）；回显**只用** `secretKeyMasked`（禁用明文回填，禁用 `autocomplete`，提交时若空则不发该字段）。
- `is_default` 开关：开启时 `el-message-box` 确认「设为默认仅影响新上传，历史文件仍按其 `storage_code` 回源原服务商」。
- **SK 安全**：禁止把 SK 明文写入 localStorage/控制台；编辑回显只用掩码；`<input type="password">` + `autocomplete="new-password"`。

### D7：服务商页操作语义

| 操作 | 接口 | 前端处理 |
|------|------|----------|
| 测试连通 | `POST /{id}/test` | toast 成功/失败原因（`msg`）；失败不阻塞列表 |
| 设为默认 | `POST /{id}/default` | 确认框（D6 文案）；成功刷新列表 |
| 启用/停用 | `PUT /{id}/status` | 胶囊切换；停用前提示「禁止新上传，存量可读」 |
| 删除 | `DELETE /{id}` | 后端删除保护（存在 `deleted=0` 文件引用 → 业务错误码 `203084`）；前端 `ElMessage.error(msg)` 展示「建议停用」 |
| 编辑 | `PUT /{id}` | `secretKey` 空=不改；非空加密落库 |
| 新建 | `POST` | `code` 唯一校验由后端（`203081`），前端不预校验唯一性 |

- 关键写（删除/设默认/启停）后端已 `@Idempotent`，前端不显式传 `Idempotent-Key`（`request.ts` 已兜底 `X-Request-Id`）。
- 列表出参 `secretKeyMasked` 只读展示；操作列按钮按 `hasPermission('blog:storage-provider:*')` 显隐。

### D8：路由接线（对齐 `files` 模式）

`src/router/index.ts`：

- `staticRoutes` 的 `Home` children 增加 `{ path: 'storage/providers', name: 'StorageProviders', component: StorageProviderList }`（与 `files` 同为静态子路由，便于直接访问与首次渲染）。
- `componentByPath` 增加 `'views/storage/StorageProviderList.vue': StorageProviderList`（RBAC 菜单下发 `path='/storage/providers'` `component='views/storage/StorageProviderList.vue'` 时，`installDynamicRoutes` 复用同组件；`router.hasRoute('Menu_'+id)` 防重复添加）。
- 菜单种子由后端变更 `2026-10-20-blog-storage-provider.sql` 已登记（`blog:storage-provider:*` 挂系统管理目录，路由 `/storage/providers`）。

## Risks / Trade-offs

- [列表 VO 是否内嵌 `url` 取决于后端实现] → 前端缩略图/预览优先用 `row.url`，缺省回退 `getFileUrl`，兼容两种实现；不阻塞验收。
- [`listEnabled` 调 `/storage/providers?status=1&size=100` 假设服务商数量 < 100] → 当前规模足够；超量时改为分页搜索框（后续变更，不在本范围）。
- [`provider_type` 下拉枚举与后端不同步] → `PROVIDER_TYPES` 作为前端常量手写，注释标注「与后端 `StorageProviderTypeEnum` 一致，新增类型须同步」；不引共享枚举包（前端不依赖后端包）。
- [编辑回显 SK 用掩码可能让用户误以为可改] → 占位符 `留空不修改` + label `(只写 / 留空不改)` 双重提示。
- [停用服务商的历史文件仍出现在列表] → 筛选下拉只列启用项，但列表展示历史文件的 `storageCode`（可能为已停用的 code）；筛选「全部」可查历史，符合「存量可读」语义。

## Migration Plan

1. 类型与 API（D1）→ 路由接线（D8）→ 文件页重写（D2/D3/D4/D5）→ 服务商页新建（D6/D7）
2. 自检：`cd frontend/blog-admin && npm run lint && npm run type-check`（CLAUDE.md §3.2）
3. 联调验收（依赖后端已部署）：上传选服务商 → 列表按服务商筛选 → 缩略图直显公开 URL → 预览下载用行内 url → 服务商页 CRUD/测试连通/设默认/启停/删除保护 → SK 掩码
4. 回滚：纯前端文件改动，回滚 PR 即可；无 DB/配置影响

## Open Questions

- 无（后端契约已冻结，前端按 §7 落地，无悬空裁决项）
