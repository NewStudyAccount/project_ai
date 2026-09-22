# Spec: identity-schema

## ADDED Requirements

### Requirement: auth 身份库表集合

`auth_db` SHALL 包含发号表 `sys_sequence` 与用户权威表 `sys_user`、部门表 `sys_dept`，并可包含 `sys_login_log`、`sys_sso_client`；这些表 SHALL NOT 存放角色或业务权限数据。

#### Scenario: 建立身份库

- **WHEN** 执行 auth 库基础迁移
- **THEN** 存在 `sys_sequence`、`sys_user`、`sys_dept`，且不存在 `sys_role` / `sys_permission`

### Requirement: 发号表约束

`sys_sequence` SHALL 以 `(seq_name, seq_date)` 唯一，保存 `current_val`，用于生成 14 位主键；主键生成 SHALL NOT 依赖数据库自增列。

#### Scenario: 按日唯一序号

- **WHEN** 同一 `seq_name` 与 `seq_date` 组合重复写入
- **THEN** 唯一约束拒绝，取号仅在既有行上原子递增

### Requirement: 用户表身份与凭证字段

`sys_user` SHALL 含全局唯一 `username`、`password_hash`、展示字段、`dept_id`、`status` 与审计五件套；`password_hash` SHALL 仅存在于该表。

#### Scenario: 用户名唯一

- **WHEN** 创建重复 `username` 的用户
- **THEN** 唯一约束 `uk_sys_user_username` 拒绝写入

#### Scenario: 凭证字段边界

- **WHEN** 审视 `sys_user` 表结构
- **THEN** 包含 `password_hash`，且不包含角色/权限关联列

### Requirement: 部门树与用户部门关联

`sys_dept` SHALL 支持树形（`parent_id`、`ancestors`）；`sys_user.dept_id` SHALL 逻辑关联部门，不使用物理外键。

#### Scenario: 部门父子

- **WHEN** 创建子部门
- **THEN** `parent_id` 指向父部门 id，根为 0，且无 REFERENCES 约束

### Requirement: 登录日志与 SSO 客户端表（可选）

系统 SHALL 允许在 `auth_db` 存在 `sys_login_log` 与 `sys_sso_client`；登录日志关联 `user_id`，SSO 客户端以 `client_id` 唯一。

#### Scenario: 登录日志关联用户

- **WHEN** 写入一条登录日志
- **THEN** `user_id` 逻辑指向 `sys_user.id`，表上无物理外键

#### Scenario: SSO 客户端唯一

- **WHEN** 配置相同 `client_id` 的两行
- **THEN** 唯一约束拒绝第二行
