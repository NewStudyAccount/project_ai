# Tasks · align-blog-admin-proto

## 1. 通用骨架（CrudPage / CrudForm）

- [x] 1.1 新增 `src/types/crud.ts`：PageSchema / ColumnDef / FilterDef / ActionDef / RowActionDef / FormFieldDef 类型（对齐 `generic-admin-page-design.md` §3，`prop` 与 `src/types` VO 字段一致、主键一律 string）
- [x] 1.2 新增 `src/components/common/CrudForm.vue`（D-Form）：label 右对齐 96px、必填红字校验、编辑回显、不可变字段 disabled、底栏「取消 + 保存（胶囊，loading 防重）」
- [x] 1.3 新增 `src/components/common/CrudPage.vue`：toolbar / el-table border / 分页（current 从 1 起）/ 内建 create/update/delete / `hasPermission` 整钮不渲染 / 危险确认 + toast ≤ 2s + 刷新（删空回退一页）
- [x] 1.4 扩展点：自定义 primaryActions / rowActions（显式权限全串 + confirm 文案）、列类型 enum/pill/datetime/copyable/tooltip/slot；样式只用云白 token

## 2. 低风险页收敛（行为零变化）

- [x] 2.1 改造 `views/content/CategoryList.vue`：关键词/状态筛选、分页、D-Form 必填校验、删除二次确认 + 后端 `msg` 展示
- [x] 2.2 改造 `views/content/TagList.vue`：同 2.1 形态
- [x] 2.3 改造 `views/audit/AuditList.vue`：时间范围 + 操作人/模块/结果筛选、长文本省略 + title、详情 D-Panel 键值对（TraceId 仅出参有则展示）
- [x] 2.4 改造 `views/Dashboard.vue`：说明卡 + 快捷入口（文章/分类/标签/文件/服务商）；统计卡片有接口则展示、无则降级

## 3. 文章列表形态收敛（不碰编辑器）

- [x] 3.1 改造 `views/content/ArticleList.vue` 列表形态：标题/分类/状态筛选、分页、状态软胶囊（映射后端枚举 desc）
- [x] 3.2 行内操作（编辑/发布/下线/删除等，按现有契约）接线二次确认 + toast + 刷新；**不改**编辑器跳转链路与内容域逻辑
- [x] 3.3 对照验收：改造前后请求参数与结果处理一致（行为零变化）

## 4. 文件 / 服务商形态收敛（与在途变更协调后执行）

- [x] 4.1 与 `blog-object-storage-frontend` 持有者确认 apply 时机与文件分工（业务字段冲突以该变更为准）
- [x] 4.2 改造 `views/file/FileList.vue` 形态层：筛选条/表格/分页/删除确认接 CrudPage；上传、引用检查、storageCode 逻辑逐行保持
- [x] 4.3 改造 `views/storage/StorageProviderList.vue` 形态层：D-Form/确认/软胶囊对齐；测试连通、设默认、SK 只写掩码回显逻辑保持
- [x] 4.4 动作手测：上传 / 测试连通 / 设默认 / 删除 / SK 留空保存，确认请求参数与结果处理与改造前一致

## 5. 自检

- [ ] 5.1 `cd frontend/blog-admin && npm run lint` 通过
- [ ] 5.2 `cd frontend/blog-admin && npm run type-check` 通过
- [ ] 5.3 权限与契约走查：无权限按钮不渲染、无角色名硬编码、id 一律 string、无 console.log 残留、密钥/敏感字段无明文回显
- [ ] 5.4 提交自查：改动仅限 `frontend/blog-admin` 且不含编辑器/内容域文件；每页独立提交（`feat(blog-admin): …`）；与在途变更无字段级冲突
