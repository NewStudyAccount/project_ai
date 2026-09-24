# user-account · 用户账号主数据与生命周期

## ADDED Requirements

### Requirement: 创建用户账号
系统 SHALL 支持创建用户账号：`username` 全局唯一，`id` 由框架统一发号（16 位 = `yyyyMMdd` + 8 位日序列，`Asia/Shanghai` 日切），出参 `id` 序列化为 String；初始 `status` 为「正常」。本系统 MUST NOT 存储 `password` / `password_hash` 或任何凭证密文字段。

#### Scenario: 成功创建账号
- **WHEN** 管理员提交合法的创建请求（username、姓名等资料字段通过 Bean Validation）
- **THEN** 系统生成 16 位 `id` 落库 `sys_user`，初始状态「正常」，返回体 `{code,msg,data}` 中 `data.id` 为 String，并记录操作审计（操作人/时间/动作/结果）

#### Scenario: username 重复被拒绝
- **WHEN** 管理员提交的 `username` 已存在
- **THEN** 系统返回 HTTP 200 + 业务段错误码（`2xxxxx`，进 `UserErrorCodeEnum`）与明确 `msg`，不落库、不吞异常

#### Scenario: 入参校验失败
- **WHEN** 创建请求缺少必填字段或格式非法
- **THEN** 系统返回 HTTP 200 + 校验段错误码 `30001`，字段细节放 `data`

### Requirement: 资料维护
系统 SHALL 支持更新用户资料（姓名/昵称/头像/邮箱/手机/备注）与扩展资料（`sys_user_profile`：性别/生日/地址/`extra_json`，与 `sys_user` 1:1）；手机号、邮箱出参 MUST 脱敏。

#### Scenario: 成功更新资料
- **WHEN** 管理员提交合法的资料更新请求
- **THEN** 系统更新 `sys_user` / `sys_user_profile` 并刷新 `update_time` / `update_by`，记录操作审计

#### Scenario: 出参脱敏
- **WHEN** 任何接口返回用户资料（管理列表、详情、对内 profile）
- **THEN** 手机号与邮箱按脱敏规则返回（不得回显完整值）

### Requirement: 账号启停与锁定
系统 SHALL 支持账号状态变更：`status` 1 正常 / 0 停用 / 2 锁定；状态变更记录操作审计。即时令牌吊销属认证中心，本系统不做。

#### Scenario: 成功启停/锁定
- **WHEN** 管理员对某账号提交合法状态变更请求
- **THEN** 系统更新 `status` 并记录审计（动作 = 状态变更类，含目标用户）

#### Scenario: 对不存在账号操作
- **WHEN** 管理员对不存在（或已逻辑删除）的 `id` 提交状态变更
- **THEN** 系统返回 HTTP 200 + 业务段错误码与明确 `msg`，不产生部分写入

### Requirement: 用户分页列表
系统 SHALL 提供用户分页列表（`current` 从 1 起、`size` 有默认值，出参 `{records,total,size,current}`），支持按 `username` / `status` 筛选；排序字段 MUST 白名单校验，禁止拼进 SQL。

#### Scenario: 分页筛选查询
- **WHEN** 管理员按 username 关键字与状态发起分页查询
- **THEN** 返回符合筛选条件的分页结果，`id` 均为 String，不 `SELECT *`

### Requirement: 管理审计
账号关键写操作（创建/更新/状态变更/RBAC 相关变更）SHALL 记录操作审计（操作人/时间/动作/结果），落 `user_audit_log`；审计内容 MUST NOT 含密码或完整令牌。

#### Scenario: 关键写留痕
- **WHEN** 任一关键写操作成功或失败结束
- **THEN** 审计记录含 `action`、操作者（= 网关注入 `X-User-Id` / 验签 `sub`）、目标、时间；`detail` 无敏感信息

### Requirement: 管理端账号管理页
`user-admin` SHALL 提供账号管理页（列表/详情/新建/启停）：请求只经 `src/api`，`id` 一律 `string`，按统一返回体解包并按 `CLAUDE.md` §5.3 分流（401 跳登录、403 提示、业务失败展示 `msg`）。

#### Scenario: 页面发起创建
- **WHEN** 管理员在账号管理页提交新建表单
- **THEN** 前端经 `src/api` 调创建接口，成功后刷新列表；失败按 `code` 分流提示

#### Scenario: 未登录访问
- **WHEN** 用户未登录访问账号管理页或接口返回 401（`10001`）
- **THEN** 前端跳转登录
