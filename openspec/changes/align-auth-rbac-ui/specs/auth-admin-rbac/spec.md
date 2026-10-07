# auth-admin-rbac · 认证中心自持 RBAC（对齐修订）

## MODIFIED Requirements

### Requirement: 认证中心拥有自持 RBAC
系统 SHALL 在 auth_db 内提供 sys_menu、sys_role、sys_user_role、sys_role_menu 四张 RBAC 表，并以它们控制 auth-admin 的菜单和管理权限。表结构按 `rbac-design.md` §2；四表主键 **SHALL NOT** 使用 framework 16 位发号（菜单/角色见下；关联表运行时可用 16 位代理键）。

#### Scenario: 初始化管理角色
- **WHEN** RBAC 表和初始数据安装完成
- **THEN** 管理员可登录 auth-admin 并按角色看到允许的菜单

#### Scenario: 主键不与 16 位发号混用
- **WHEN** 管理端新建菜单或角色
- **THEN** 落库主键为 `rbac-design.md` §2.0 短号（非 16 位），且与种子号段可对照

### Requirement: 菜单和角色可管理
系统 SHALL 提供菜单树和角色的创建、更新、删除、查询接口，菜单删除时保持父子关系和引用完整性校验。创建时主键 SHALL 由服务端按 `rbac-design.md` §2.0 分配：type=1/2 为层级短号 `id = depth×1000 + n`（depth≤4，n 为本层全局序号）、type=3 按钮段 `90001–99999`、type=4 接口点段 `5001–5999`；type=3/4 的 `parent_id` MUST 指向 type=2 菜单且 MUST NOT 为 0。MUST NOT 接受客户端传入 id，MUST NOT 数据库自增，MUST NOT 改写历史行 id。角色主键 SHALL 用 `sys_role` 段内短号 `9001–9999`（`max+1`）。

#### Scenario: 创建角色
- **WHEN** 管理员创建角色并分配菜单
- **THEN** 系统保存角色与菜单关系，返回创建成功并写入审计

#### Scenario: 创建目录或菜单节点
- **WHEN** 管理员创建 type=1/2 节点且树深度不超过 4
- **THEN** 服务端按所在深度段 `max+1` 分配 id 并落库；客户端传入的 id 被忽略或拒绝

#### Scenario: 菜单层级过深
- **WHEN** 新建节点将使深度超过 4
- **THEN** 返回业务段错误码「菜单层级过深」，不落库

#### Scenario: 按钮或接口点挂载非法
- **WHEN** 创建 type=3/4 且父节点不是 type=2 菜单（含 parent_id=0 或目录）
- **THEN** 返回业务段错误码「父节点非法」，不落库

#### Scenario: 本层或本段 id 用尽
- **WHEN** 目标号段已无可用 id
- **THEN** 返回业务段错误码「本层/本段 id 已用尽」，不落库

#### Scenario: 删除叶子菜单
- **WHEN** 删除无子节点的菜单
- **THEN** 逻辑删除自身并清理 `sys_role_menu` 引用

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
系统 SHALL 提供 /me/menus 和 /me/permissions，按已验签身份和 RBAC 关系返回当前操作员可用的菜单树和权限标识。/me/menus SHALL 仅包含 `status=1` 的 type∈{1,2} 节点；非 admin 授权集 SHALL 取祖先闭包后组树（禁止只下发孤叶子）。/me/permissions SHALL 返回非空 `permission` 去重集合（含 type 3/4）。拥有启用 `role_code=admin` 时 SHALL 下发本库全部启用菜单/权限，不依赖 `sys_role_menu` 逐条绑定。

#### Scenario: 查询当前权限
- **WHEN** 已登录运营人员请求 /me/permissions
- **THEN** 系统只返回其角色拥有的权限，不返回其他角色或越权权限

#### Scenario: 停用菜单不下发
- **WHEN** 操作员的角色包含已停用（status=0）菜单
- **THEN** /me/menus 与 /me/permissions 不包含该节点及其权限

#### Scenario: 授权树断链防护
- **WHEN** 操作员仅被授权子菜单/按钮而未含父节点
- **THEN** /me/menus 组树时补全祖先链（或等价可导航结构），不返回无法挂载的孤叶子
