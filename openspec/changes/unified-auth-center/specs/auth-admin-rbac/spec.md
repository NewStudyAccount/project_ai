## ADDED Requirements

### Requirement: 认证中心拥有自持 RBAC
系统 SHALL 在 auth_db 内提供 sys_menu、sys_role、sys_user_role、sys_role_menu 四张 RBAC 表，并以它们控制 auth-admin 的菜单和管理权限。

#### Scenario: 初始化管理角色
- **WHEN** RBAC 表和初始数据安装完成
- **THEN** 管理员可登录 auth-admin 并按角色看到允许的菜单

### Requirement: 菜单和角色可管理
系统 SHALL 提供菜单树和角色的创建、更新、删除、查询接口，菜单删除时保持父子关系和引用完整性校验。

#### Scenario: 创建角色
- **WHEN** 管理员创建角色并分配菜单
- **THEN** 系统保存角色与菜单关系，返回创建成功并写入审计

### Requirement: 用户角色关系可分配和回收
系统 SHALL 提供 /users/{id}/roles 的 POST/DELETE，允许管理员为 auth-admin 用户分配和回收角色。

#### Scenario: 分配角色
- **WHEN** 管理员为用户分配一个或多个角色
- **THEN** 系统写入 sys_user_role，后续该用户获得对应菜单和权限

### Requirement: 角色菜单关系可维护
系统 SHALL 允许管理员为角色批量设置菜单，未授权的菜单不能通过直接请求管理接口绕过。

#### Scenario: 更新角色菜单
- **WHEN** 管理员更新角色的菜单集合
- **THEN** 旧关系按设置结果同步，用户菜单随角色关系变化

### Requirement: 当前操作员可查询菜单树和权限集
系统 SHALL 提供 /me/menus 和 /me/permissions，按已验签身份和 RBAC 关系返回当前操作员可用的菜单树和权限标识。

#### Scenario: 查询当前权限
- **WHEN** 已登录运营人员请求 /me/permissions
- **THEN** 系统只返回其角色拥有的权限，不返回其他角色或越权权限
