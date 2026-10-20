# Tasks · align-user-admin-proto

## 1. 通用骨架（CrudPage / CrudForm）

- [ ] 1.1 新增 `src/types/crud.ts`：PageSchema / ColumnDef / FilterDef / ActionDef / RowActionDef / FormFieldDef 类型（对齐 `generic-admin-page-design.md` §3，`prop` 与 `src/types` VO 字段一致、主键一律 string）
- [ ] 1.2 新增 `src/components/common/CrudForm.vue`（D-Form）：label 右对齐 96px、必填红字校验、编辑回显、不可变字段 disabled、底栏「取消 + 保存（胶囊，loading 防重）」
- [ ] 1.3 新增 `src/components/common/CrudPage.vue`：toolbar（主操作靠左 + 筛选右置换行）、`el-table border`、分页（current 从 1 起）、内建 create/update/delete、`hasPermission` 无权限整钮不渲染、危险操作二次确认 + toast ≤ 2s + 刷新当前页（删空回退一页）
- [ ] 1.4 预留列类型扩展点（enum / pill / datetime / copyable / tooltip，pillTone 仅 success/warning/danger），样式只用云白 token

## 2. 账号管理（UserList）

- [ ] 2.1 改造 `views/user/UserList.vue` 为 PageSchema 驱动：筛选（用户名/姓名关键词、角色、状态、创建时间范围），变更/回车即查且页码重置 1
- [ ] 2.2 表格列对齐原型：用户 id（string）、用户名、姓名、部门、角色、手机号（出参脱敏值）、状态软胶囊、创建时间（不换行）
- [ ] 2.3 D-Form 新建/编辑：用户名/姓名必填红字校验、编辑锁定用户名、id 不暴露、状态胶囊单选、保存 loading 防重
- [ ] 2.4 行内操作：编辑 / 重置密码（二次确认 + toast）/ 删除（danger 二次确认 + toast + 刷新 + 删空回退一页）

## 3. 审计列表（AuditList）

- [ ] 3.1 改造 `views/audit/AuditList.vue`：操作人/模块/结果筛选 + 时间范围（beginTime/endTime），长文本省略 + title 提示
- [ ] 3.2 审计详情 D-Panel：键值对展示全量字段；TraceId 仅当后端出参提供时渲染

## 4. 首页与全局

- [ ] 4.1 改造 `views/Dashboard.vue`：系统说明卡 + 快捷入口（账号/审计/菜单/角色）
- [ ] 4.2 统计卡片接入：确认现有接口可提供的计数，有则展示、无则降级（禁止虚构数据、禁止新增接口）
- [ ] 4.3 全局走查：状态软胶囊、主按钮胶囊、行内 link、弹窗圆角 12，对齐 `admin-ui-design.md` §5

## 5. 自检

- [ ] 5.1 `cd frontend/user-admin && npm run lint` 通过
- [ ] 5.2 `cd frontend/user-admin && npm run type-check` 通过
- [ ] 5.3 权限与契约走查：无权限按钮不渲染、无角色名硬编码、id 一律 string、无 console.log 残留、无密码明文处理
- [ ] 5.4 提交自查：改动仅限 `frontend/user-admin`（不碰 menu/role 视图与后端）；一个提交只做一件事（`feat(user-admin): …`）
