# Spec: rbac-authorization

## ADDED Requirements

### Requirement: 权限数据按系统本地自治

各业务系统 SHALL 在自身数据库维护角色、权限及关联关系；SHALL NOT 依赖认证中心存储角色/权限；SHALL NOT 直接读写认证中心数据库。

#### Scenario: 系统内创建角色

- **WHEN** 在系统 A 创建角色
- **THEN** 角色仅落入系统 A 库，并可携带 system_code=A；系统 B 不受该角色影响

#### Scenario: 跨系统不串权

- **WHEN** 用户仅在系统 A 被授予角色
- **THEN** 访问系统 B 受控接口时无权

### Requirement: 用户与角色仅逻辑关联

`sys_user_role` SHALL 仅保存 `user_id`（及 role_id 等），逻辑关联认证中心用户；SHALL NOT 在业务库存密码或完整账号权威数据。

#### Scenario: 为用户分配角色

- **WHEN** 管理员将系统内角色授予用户
- **THEN** 写入 user_id + role_id 关联，赋值幂等；不写入用户密码等凭证字段

#### Scenario: 移除角色

- **WHEN** 移除用户与角色关联
- **THEN** 该用户失去此角色权限，其他角色不受影响

### Requirement: 菜单按钮与 API 权限模型

系统 SHALL 支持 menu、button、api 三类权限；权限码 SHALL 在系统内唯一；菜单为树形结构。

#### Scenario: 权限码唯一

- **WHEN** 在同一系统创建重复 permission_code
- **THEN** 拒绝写入并返回业务错误

#### Scenario: 返回可访问菜单

- **WHEN** 查询当前用户菜单/按钮
- **THEN** 仅返回该用户在本系统有权的 menu 与 button

### Requirement: 服务细粒度鉴权

业务服务 SHALL 基于当前用户在本系统的权限码或角色进行方法/接口级鉴权；网关 SHALL NOT 实现业务 RBAC 规则。

#### Scenario: 无权限访问

- **WHEN** 用户请求需要权限码 P 的接口且无 P
- **THEN** 返回 403 语义失败

#### Scenario: 网关只验身份

- **WHEN** 携带有效 JWT 经系统网关访问
- **THEN** 网关放行并透传用户身份，由业务服务决定是否授权

### Requirement: 基础表与 ID 规范

RBAC 相关表 SHALL 使用 14 位业务主键、审计五件套、无物理外键；关联删除 SHALL 优先物理删除以避免逻辑删除与唯一键冲突。

#### Scenario: 落库关联后删除

- **WHEN** 删除用户-角色关联
- **THEN** 该关联行物理删除或以不与 UK 冲突的方式移除，可再次分配同一角色
