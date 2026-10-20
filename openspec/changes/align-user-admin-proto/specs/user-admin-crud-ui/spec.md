# user-admin-crud-ui · 用户中心管理端账号/审计列表与 CrudPage 骨架

## Purpose

约束 user-admin 账号管理与审计列表的全量形态与配置驱动 CrudPage 通用骨架，交互对齐 `docs/admin-system-prototype/user-list.html` / `audit-log.html` 样张，工程契约以 `docs/template/generic-admin-page-design.md` 为准。

## ADDED Requirements

### Requirement: 配置驱动的 CrudPage 通用骨架
user-admin SHALL 提供系统内唯一的 `components/common/CrudPage.vue` 与 `CrudForm.vue`，消费声明式 PageSchema 渲染 P-List 全量形态（page-card + toolbar + el-table + pagination + D-Form）。业务页面文件 SHALL 只声明差异（config + ≤ 30 行模板接线），MUST NOT 各自重写骨架。骨架样式 SHALL 只用「云白」token，MUST NOT 新增色值。

#### Scenario: 业务页由 schema 驱动渲染
- **WHEN** 业务页传入合法 PageSchema（columns / filters / form / api / permissionPrefix）
- **THEN** CrudPage 渲染工具栏筛选、数据表、分页与新建/编辑弹窗，业务页无重复骨架代码

#### Scenario: 无权限按钮整钮不渲染
- **WHEN** 当前用户权限集不包含某操作的权限标识（`system:resource:action`）
- **THEN** 对应按钮不渲染（非 disabled），页面其余部分正常可用

#### Scenario: 页面文件差异受控
- **WHEN** 开发者新增一个业务列表页
- **THEN** 页面文件不超过 30 行（不含 config），差异全部声明在 PageSchema 配置中

### Requirement: 账号管理列表全量形态
账号管理页 SHALL 为 P-List 全量形态：支持用户名/姓名关键词、角色、状态与创建时间范围（beginTime/endTime）筛选，筛选变更或回车即查且页码重置 1；分页入参 `current` 从 1 起、按 `{records,total,size,current}` 消费；表格展示用户 id（string）、用户名、姓名、部门、角色、手机号（脱敏展示）、状态（软胶囊）、创建时间；长文本与时间列 MUST NOT 换行挤压。

#### Scenario: 多条件组合筛选
- **WHEN** 用户输入关键词并选择角色/状态/时间范围
- **THEN** 表格仅返回同时满足全部条件的账号，页码重置为第 1 页；无结果显示空态

#### Scenario: 手机号脱敏展示
- **WHEN** 列表渲染账号行
- **THEN** 手机号按后端出参脱敏值展示（前端不做二次加工、不展示完整号码）

### Requirement: 账号表单校验与编辑锁定
新建/编辑账号 SHALL 走 D-Form（圆角 12、label 右对齐 90–100px、底栏「取消 + 保存（primary 胶囊，loading 防重）」）：用户名/姓名必填，校验失败在字段下方红字提示且不关闭弹窗；编辑模式下用户名 SHALL 锁定不可改，其余字段按行数据回显；MUST NOT 暴露 id 输入。

#### Scenario: 必填校验失败
- **WHEN** 用户提交时用户名或姓名为空
- **THEN** 对应字段下方显示红字，弹窗不关闭、不发请求

#### Scenario: 编辑账号
- **WHEN** 用户点击行内「编辑」
- **THEN** 弹窗按行数据回显，用户名字段 disabled，保存后 toast 成功并刷新当前页

### Requirement: 重置密码与删除保护
重置密码与删除账号 MUST 经二次确认（danger 文案含账号名/用户名）；确认后 toast 成功（≤ 2s）并刷新当前页；当前页删空时 SHALL 自动回退一页。前端 MUST NOT 自行实现密码生成/加密逻辑，仅接线后端动作。

#### Scenario: 重置密码确认
- **WHEN** 用户点击「重置密码」并确认
- **THEN** 调用后端重置动作并 toast 反馈；取消则不发请求

#### Scenario: 删除账号并回退页码
- **WHEN** 用户确认删除当前页最后一条账号
- **THEN** 行删除、toast「删除成功」，列表自动回退到上一页（若存在）

### Requirement: 审计列表与详情面板
审计列表 SHALL 为审计形态 P-List：支持操作人/模块/结果筛选与时间范围筛选；长文本详情列省略显示并 hover 全文；行内「详情」打开 D-Panel 键值对展示记录全量字段；TraceId 仅当后端出参提供时展示，MUST NOT 前端自造。

#### Scenario: 时间范围筛选审计记录
- **WHEN** 用户选择起止日期并查询
- **THEN** 列表仅返回范围内的记录，分页重算

#### Scenario: 审计详情展示
- **WHEN** 用户点击行内「详情」
- **THEN** D-Panel 以键值对展示该记录字段；后端未提供 TraceId 时该项不展示

### Requirement: 危险操作反馈与首页概览
删除/重置类操作反馈 SHALL 统一（二次确认 + toast ≤ 2s + 刷新）；状态列 SHALL 使用软胶囊（启用/停用）。Dashboard SHALL 呈现系统说明卡与快捷入口（跳转账号/审计/菜单/角色页）；统计卡片仅当现有接口可提供数据时展示，无则降级不渲染，MUST NOT 虚构数据或新增统计接口，MUST NOT 引入图表库。

#### Scenario: 取消危险操作
- **WHEN** 用户在二次确认中点击取消
- **THEN** 数据不变、不发请求

#### Scenario: 首页降级
- **WHEN** 无可用统计接口
- **THEN** 首页仅展示说明卡与快捷入口
