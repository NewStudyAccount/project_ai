> 实施铁律：只按本清单进行；`proposal / specs / design` 为唯一指令源，不擅自修改（CLAUDE.md §8）。
> 设计决策编号（D1–D8）见 `design.md`。

## 1. 类型与 API 扩展（D1）

- [x] 1.1 `src/types/index.ts`：`FileMeta` 增加可选 `storageCode?: string`；新增 `StorageProviderVO`（出参含 `secretKeyMasked`、`isDefault`、`status`、`pathStyle` 等）、`StorageProviderPageQuery`（`keyword`/`providerType`/`status`/`current`/`size`）、`StorageProviderInput`（入参，`secretKey` 可选）；新增 `PROVIDER_TYPES` 常量（六类，与后端 `StorageProviderTypeEnum` 一致，注释标注同步要求）
- [x] 1.2 `src/api/file.ts`：`pageFiles` 签名增加可选 `storageCode`；`uploadFile` 增加可选 `storageCode`（multipart form 字段）
- [x] 1.3 新建 `src/api/storageProvider.ts`：`storageProviderApi.page/get/create/update/delete/testConnection/setDefault/updateStatus/listEnabled`；全部经 `src/api/request` 的 `http`/`request` 统一解包；`listEnabled` 调 `GET /storage/providers?status=1&size=100` 返回简化数组（取 `records`）

## 2. 文件管理展示页重写（D2/D3/D4/D5，设计 §7.2）

- [x] 2.1 `src/views/file/FileList.vue` 统计条：增加「默认服务商」卡（从启用列表取 `isDefault===1` 展示 `code · 桶 bucket`；无则「未配置」）
- [x] 2.2 筛选栏：增加「服务商」下拉（全部 + 启用项 `code`，`onMounted` 调 `listEnabled` 拉取）；保留原 keyword/contentType；查询时带 `storageCode` 精确筛选
- [x] 2.3 列表：增加「缩略图」列（`image/*` 用 `row.url` 作 `<img src>`，`@error` 降级类型占位；非图片显示 `PDF`/`MD`/`—`）；增加「服务商 / 桶」列（`storageCode` + `bucket`）
- [x] 2.4 预览/下载：优先用 `row.url`；缺省回退 `getFileUrl(row.id)`；预览 `window.open(url)`，下载用 `<a download>` 触发落盘（行为由后端 `Content-Disposition` 决定，前端不改写）
- [x] 2.5 上传对话框：增加「目标服务商」下拉（启用项，默认选中默认服务商）；无启用项时禁用上传按钮 + 提示「无可用服务商」；提交 `uploadFile(file, bizType, bizId, storageCode)`
- [x] 2.6 删除确认文案：强调「逻辑删除」「公开 URL 在物理清理前可能仍可访问」「删除前会做引用检查，被引用时拒绝并返回引用摘要」；被拒绝时由 `request.ts` 统一 `ElMessage.error(msg)` 展示

## 3. 存储服务商配置页新建（D6/D7，设计 §7.3）

- [x] 3.1 新建 `src/views/storage/StorageProviderList.vue`：P-List 布局；`onMounted` 调 `storageProviderApi.page()` 加载；分页条
- [x] 3.2 筛选栏：关键字（code/名称）、`provider_type` 下拉（`PROVIDER_TYPES`）、状态下拉（启用/停用）；「查询/重置」
- [x] 3.3 列表列：编码/名称（`code`/`name`）、类型标签（`providerType`）、endpoint/桶（截断+悬浮）、region/path-style 摘要、密钥掩码（`secretKeyMasked` 只读）、状态胶囊+默认标记、操作列
- [x] 3.4 操作列按钮：测试连通（`blog:storage-provider:test`）、编辑（`blog:storage-provider:update`）、设默认（非默认行，`blog:storage-provider:update`）、启用/停用切换（`blog:storage-provider:update`）、删除（`blog:storage-provider:delete`）；按钮按 `hasPermission` 显隐
- [x] 3.5 新建/编辑对话框（`el-dialog` 560px + `el-form`）：字段按 D6；`provider_type` 切换时按推荐默认调 `path_style` 与 `region` 必填提示；`secret_key` 密码框（`autocomplete="new-password"`，编辑留空=不修改，占位符 `留空不修改`）；`is_default` 开启时确认框；表单校验（必填、`code` 小写连字符、`endpoint` http(s)、`public_base_url` 无尾斜杠）
- [x] 3.6 测试连通：调 `testConnection(id)`；成功/失败 toast（`msg`）；不阻塞列表
- [x] 3.7 设默认：确认框（「仅影响新上传，历史文件仍按 `storage_code` 回源原服务商」）→ `setDefault(id)` → 刷新
- [x] 3.8 启停：停用前提示「禁止新上传，存量可读」→ `updateStatus(id, status)` → 刷新
- [x] 3.9 删除：`deleteProvider(id)`；失败（`203084`）由 `request.ts` 统一 toast `msg`（含建议停用）；不重试

## 4. 路由接线（D8）

- [x] 4.1 `src/router/index.ts`：`import StorageProviderList from '@/views/storage/StorageProviderList.vue'`
- [x] 4.2 `componentByPath` 增加 `'views/storage/StorageProviderList.vue': StorageProviderList`
- [x] 4.3 `staticRoutes` 的 `Home` children 增加 `{ path: 'storage/providers', name: 'StorageProviders', component: StorageProviderList }`

## 5. 自检与验收（对照 specs 场景）

- [x] 5.1 编译与静态自检：`cd frontend/blog-admin && npm run lint && npm run type-check`（CLAUDE.md §3.2；不锁定测试栈 §2.2）
- [ ] 5.2 行为验收（按 `specs/blog-file-admin` 与 `specs/blog-storage-provider-admin` 场景，依赖后端已部署）：
  > **PAUSED**：运行时点击验证需起活体前后端栈（MySQL/Redis/Nacos/MinIO + blog 后端服务 + vite dev + OIDC 登录 + 已种子 RBAC 菜单）。本会话仅完成代码级实现 + 静态自检（lint/type-check/安全 grep），未执行浏览器点击验证。代码已覆盖全部 specs 场景的对应分支，待活体栈联调后勾选。
  - 文件页：上传选服务商 → 列表按服务商筛选 → 缩略图直显公开 URL → 预览下载用行内 url → 无可用服务商时上传禁用 → 删除被引用拒绝展示 msg
  - 服务商页：列表掩码 → 新建/编辑（SK 留空不改）→ 测试连通成功/失败可读原因 → 设默认确认文案 → 启停提示 → 删除保护提示建议停用
- [x] 5.3 安全自检：SK 不入 localStorage/控制台；编辑回显只用掩码；`id` 一律 String；无硬编码 endpoint/AK/SK
- [x] 5.4 提交规范：`feat(blog-admin): 对象存储管理端双页（文件页服务商维度 + 服务商配置页）`（CLAUDE.md §6.3 单提交一事；提交前自查清单）
