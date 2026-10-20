# auth-admin-crud-ui · 认证中心管理端业务列表页与 CrudPage 骨架

## Purpose

约束 auth-admin 业务列表页（Client / Grant / 审计 / 登录尝试 / 用户角色）的全量形态与配置驱动 CrudPage 通用骨架，使页面差异只落在 PageSchema 配置，交互与视觉对齐 `docs/admin-system-prototype/` 样张，工程契约以 `docs/template/generic-admin-page-design.md` 为准。

## ADDED Requirements

### Requirement: 配置驱动的 CrudPage 通用骨架
auth-admin SHALL 提供系统内唯一的 `components/common/CrudPage.vue` 与 `CrudForm.vue`（D-Form），消费声明式 PageSchema（列、筛选、主操作、行内操作、表单字段、权限前缀）渲染 P-List 全量形态：page-card + toolbar + el-table + pagination + D-Form 弹窗。业务页面文件 SHALL 只声明差异（config + ≤ 30 行模板接线），MUST NOT 各自重写骨架。骨架样式 SHALL 只用「云白」token，MUST NOT 新增色值。

#### Scenario: 业务页由 schema 驱动渲染
- **WHEN** 业务页传入合法 PageSchema（columns / filters / form / api / permissionPrefix）
- **THEN** CrudPage 渲染出工具栏筛选、数据表、分页与新建/编辑弹窗，业务页无重复骨架代码

#### Scenario: 无权限按钮整钮不渲染
- **WHEN** 当前用户权限集不包含某主操作或行内操作的权限标识（`system:resource:action`）
- **THEN** 对应按钮不渲染（非 disabled），页面其余部分正常可用

#### Scenario: 页面文件差异受控
- **WHEN** 开发者新增一个业务列表页
- **THEN** 页面文件不超过 30 行（不含 config），差异全部声明在 PageSchema 配置中

### Requirement: Client 管理列表全量形态
Client 管理页 SHALL 为 P-List 全量形态：支持按 Client ID / 名称关键词与状态筛选（回车或变更即查、页码重置 1），分页入参 `current` 从 1 起、出参按 `{records,total,size,current}` 消费；新建/编辑走 D-Form（圆角 12、label 右对齐 90–100px、底栏「取消 + 保存（primary 胶囊，提交中 loading）」），MUST NOT 暴露 id 输入；编辑模式下 Client ID 字段 SHALL 锁定不可改。

#### Scenario: 按关键词与状态筛选
- **WHEN** 用户输入 Client ID / 名称关键词或切换状态并回车/变更
- **THEN** 表格按条件刷新且页码重置为第 1 页；无数据显示空态「暂无数据」

#### Scenario: 新建 Client 校验
- **WHEN** 用户提交表单时 Client ID 或名称为空
- **THEN** 对应字段下方显示红字校验提示，弹窗不关闭、不发请求

#### Scenario: 编辑锁定标识字段
- **WHEN** 用户从行内「编辑」打开已存在 Client 的表单
- **THEN** Client ID 以 disabled 呈现，其余字段按行数据回显

### Requirement: Client 密钥查看与轮换面板
Client 密钥 SHALL 通过 D-Panel 弹窗查看：默认打码展示；提供「显示 / 隐藏」与「复制」动作；提供「轮换密钥」动作且 MUST 先经二次确认并说明旧密钥宽限语义。密钥值 MUST NOT 写入 localStorage、console 或持久化前端存储，MUST NOT 在列表页明文常驻。

#### Scenario: 默认打码查看
- **WHEN** 用户点击行内「查看密钥」
- **THEN** D-Panel 展示该 Client 的密钥字段为打码态，且提供显示/隐藏与复制按钮

#### Scenario: 轮换二次确认
- **WHEN** 用户点击「轮换密钥」
- **THEN** 弹出 danger 二次确认（说明旧密钥宽限期）；确认后展示新密钥并 toast 成功提示；取消则无任何变更

### Requirement: 审计与登录尝试列表
操作审计页与登录尝试页 SHALL 为审计形态 P-List：支持操作人/模块/结果筛选与时间范围（beginTime/endTime）筛选；行内提供「详情」打开 D-Panel，以键值对展示记录详情（含记录 id、时间、操作人、来源 IP、结果、详情描述；TraceId 仅当后端出参提供时展示，MUST NOT 前端自造）。长文本列 SHALL 省略显示并提供 hover 全文（title/tooltip）。

#### Scenario: 时间范围筛选
- **WHEN** 用户选择起止日期并查询
- **THEN** 列表仅返回时间范围内的记录，分页随筛选结果重算

#### Scenario: 查看审计详情
- **WHEN** 用户点击行内「详情」
- **THEN** D-Panel 以键值对展示该条记录全量字段；后端未提供 TraceId 时不展示该项

### Requirement: 危险操作确认与结果反馈
删除、轮换等危险/不可逆操作 MUST 经二次确认（danger 文案含对象名）；确认成功后 SHALL toast 成功提示（≤ 2s）并刷新当前页；当前页删空时 SHALL 自动回退一页。表单提交按钮 SHALL 防重（loading），成功后关闭弹窗并刷新。

#### Scenario: 删除行并刷新
- **WHEN** 用户确认删除某行
- **THEN** 行从列表消失，toast「删除成功」，当前页数据刷新；若本页为空则回退到上一页

#### Scenario: 取消删除
- **WHEN** 用户在二次确认中点击取消
- **THEN** 列表数据不变，不发删除请求

### Requirement: 首页概览
Dashboard SHALL 呈现系统说明卡（系统名 + 一句话说明）与快捷入口（跳转本系统主要管理页）；统计卡片仅当现有接口可提供数据时展示（单列 page-card / stat-card 区块堆叠，MUST NOT 为凑版面虚构数据或新增统计接口）。MUST NOT 引入图表库。

#### Scenario: 有统计数据时展示
- **WHEN** 现有接口返回可展示的计数
- **THEN** 首页展示对应统计卡片（标签 + 数值 + 简要趋势/说明）

#### Scenario: 无统计接口时降级
- **WHEN** 无可用统计接口
- **THEN** 首页仅展示说明卡与快捷入口，不展示虚假数字
