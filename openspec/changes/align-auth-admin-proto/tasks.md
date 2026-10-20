# Tasks · align-auth-admin-proto

## 1. 通用骨架（CrudPage / CrudForm）

- [x] 1.1 新增 `src/types/crud.ts`：PageSchema / ColumnDef / FilterDef / ActionDef / RowActionDef / FormFieldDef 类型（对齐 `generic-admin-page-design.md` §3，`prop` 与 `src/types` VO 字段一致、主键一律 string）
- [x] 1.2 新增 `src/components/common/CrudForm.vue`（D-Form）：label 右对齐 96px、必填红字校验、编辑回显、不可变字段 disabled、底栏「取消 + 保存（胶囊，loading 防重）」
- [x] 1.3 新增 `src/components/common/CrudPage.vue`：toolbar（主操作靠左 + 筛选右置换行）、`el-table border`、分页（`layout="total, prev, pager, next, sizes"`，current 从 1 起）、内建 create/update/delete 动作、`hasPermission` 无权限整钮不渲染、危险操作二次确认 + toast ≤ 2s + 刷新当前页（删空回退一页）
- [x] 1.4 CrudPage 行内操作与列渲染预留扩展点（enum/pill/datetime/copyable/tooltip 列类型，pillTone 仅 success/warning/danger），骨架样式只用云白 token、不新增色值

## 2. 业务页收敛（Client / Grant / 用户角色）

- [x] 2.1 改造 `views/client/ClientList.vue` 为 PageSchema 驱动：筛选（关键词 + 状态）、分页、新建/编辑 D-Form（Client ID 编辑锁定）、删除二次确认
- [x] 2.2 新增 Client 密钥 D-Panel：默认打码、显示/隐藏、复制、轮换（danger 二次确认 + 旧密钥宽限文案）；密钥不入 localStorage / console
- [x] 2.3 改造 `views/grant/GrantList.vue` 为 PageSchema 驱动 P-List（筛选/分页/行内操作/确认反馈）
- [x] 2.4 改造 `views/user/UserRoleList.vue` 为 PageSchema 驱动 P-List（绑定/解绑动作走确认；解绑文案含用户名）

## 3. 审计与登录尝试

- [x] 3.1 改造 `views/audit/AuditLogList.vue`：时间范围筛选（beginTime/endTime）+ 操作人/模块/结果筛选，长文本省略 + title 提示
- [x] 3.2 审计详情 D-Panel：键值对展示全量字段；TraceId 仅当后端出参提供时渲染
- [x] 3.3 改造 `views/audit/LoginAttemptList.vue` 同审计形态（时间范围 + 结果筛选 + 详情 D-Panel）

## 4. 首页与全局

- [x] 4.1 改造 `views/Dashboard.vue`：系统说明卡 + 快捷入口（跳转 Client/Grant/审计/登录尝试/用户角色页）
- [x] 4.2 统计卡片接入：先确认现有接口可提供的计数；有则按 stat-card 区块展示，无则降级不渲染（禁止虚构数据、禁止新增接口）
- [x] 4.3 全局走查：所有列表状态用软胶囊（启用/停用/失败）、主按钮胶囊、行内操作 link、弹窗圆角 12；与 `admin-ui-design.md` §5 组件约定一致

## 5. 自检

- [x] 5.1 `cd frontend/auth-admin && npm run lint` 通过
- [x] 5.2 `cd frontend/auth-admin && npm run type-check` 通过
- [x] 5.3 权限走查：无 `system:resource:action` 权限的按钮不渲染；无角色名硬编码；id 读写一律 string；无 console.log 调试残留
- [x] 5.4 提交自查：改动仅限 `frontend/auth-admin`（不触碰 menu/role 视图与后端）；无生产密钥；一个提交只做一件事（`feat(auth-admin): …`）
