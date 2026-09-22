# Spec: rbac-schema

## ADDED Requirements

### Requirement: 业务系统 RBAC 表集合

各业务库 SHALL 包含 `sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` 与本库 `sys_sequence`；SHALL NOT 在业务库存放 `password_hash`。

#### Scenario: 建立 RBAC 表

- **WHEN** 执行业务库 RBAC 迁移
- **THEN** 上述表存在，且无密码字段

### Requirement: 角色表唯一键与系统维度

`sys_role` SHALL 以 `(system_code, role_code)` 唯一，并包含 `role_name`、`status`、`data_scope` 与审计字段。

#### Scenario: 角色编码系统内唯一

- **WHEN** 同一 `system_code` 下插入重复 `role_code`
- **THEN** 唯一约束拒绝

### Requirement: 权限表类型与树

`sys_permission` SHALL 以 `(system_code, permission_code)` 唯一；`permission_type` SHALL 区分 menu、button、api；菜单/权限层级 SHALL 使用 `parent_id` 树。

#### Scenario: 权限码唯一

- **WHEN** 同一 `system_code` 下插入重复 `permission_code`
- **THEN** 唯一约束拒绝

#### Scenario: 三类权限同表

- **WHEN** 分别创建 menu、button、api 权限
- **THEN** 均落入 `sys_permission`，以 `permission_type` 区分

### Requirement: 用户-角色关联仅存引用

`sys_user_role` SHALL 仅保存 `user_id`、`role_id` 及 `system_code` 等关联字段；`user_id` SHALL 逻辑指向认证中心 `sys_user.id`；删除关联 SHALL 优先物理删除。

#### Scenario: 用户角色唯一配对

- **WHEN** 对同一 `(user_id, role_id)` 再次插入
- **THEN** 唯一约束拒绝，赋值幂等

#### Scenario: 无物理外键

- **WHEN** 检查 `sys_user_role` 表定义
- **THEN** 不存在指向 auth 库或 `sys_role` 的物理外键

### Requirement: 角色-权限关联

`sys_role_permission` SHALL 以 `(role_id, permission_id)` 唯一，关联本系统角色与权限，并可冗余 `system_code`；删除 SHALL 优先物理删除。

#### Scenario: 角色权限唯一配对

- **WHEN** 对同一 `(role_id, permission_id)` 再次插入
- **THEN** 唯一约束拒绝

### Requirement: 索引与命名规范

RBAC 相关表 SHALL 使用 `idx_表_字段` / `uk_表_字段` 命名；单表索引数量 SHALL 不超过 5。

#### Scenario: 索引命名

- **WHEN** 创建用户-角色查询索引
- **THEN** 索引名符合约定（如 `idx_sys_user_role_user_id`）且单表索引不超过 5 个
