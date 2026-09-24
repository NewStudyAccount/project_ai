# user-rbac · 本系统自持 RBAC

## ADDED Requirements

### Requirement: 菜单管理（资源树 CRUD）
系统 SHALL 提供本系统菜单/权限资源树管理：节点 `type` 1 目录 / 2 菜单 / 3 按钮 / 4 接口（不下发前端），含 `path` / `component` / `icon` / `hidden` / `requires_auth` / `sort` / `status`；`permission` 标识格式 `user:resource:action`（全小写、本系统内唯一，应用层校验；目录留空）。表结构按 `rbac-design.md` §2.1 同构建于 `user_db`。

#### Scenario: 创建菜单节点
- **WHEN** 管理员提交合法的菜单节点（含 type、permission、排序等）
- **THEN** 系统发号落库并记录审计；`permission` 首段非 `user` 或系统内重复时返回业务段错误码

#### Scenario: 树形查询
- **WHEN** 管理员查询菜单树
- **THEN** 按 `parent_id` 层级与 `sort` 返回树结构

#### Scenario: 删除节点
- **WHEN** 管理员删除某节点
- **THEN** 逻辑删除（`deleted=1`）；存在未处理子节点时按约定拒绝或级联处理并记录审计（实施定一种，验收按其实现）

### Requirement: 角色管理与授权
系统 SHALL 提供角色管理（`role_code` 本系统内唯一、`data_scope` 1 全部 / 2 本部门（预留）/ 3 仅本人、`sort`、`status`）与角色-菜单授权；关键写 SHALL 走统一写幂等（`@Idempotent`，重复请求返回首次 `Result`）。

#### Scenario: 创建角色并授权
- **WHEN** 管理员创建角色并勾选菜单/按钮权限提交
- **THEN** 落库 `sys_role` / `sys_role_menu` 并记录审计；重复幂等键请求返回首次 `Result`，不重复写入

#### Scenario: role_code 重复
- **WHEN** 管理员提交已存在的 `role_code`
- **THEN** 返回业务段错误码与明确 `msg`，不落库

### Requirement: 用户-角色分配
系统 SHALL 支持用户-角色分配/回收（`sys_user_role`，`user_id` = 用户中心 `sys_user.id` 逻辑引用、无物理外键）；分配时"选人"可经 `user-api` 查询用户（跨系统只读契约）。

#### Scenario: 分配与回收角色
- **WHEN** 管理员为某 `user_id` 分配或回收角色
- **THEN** 写入/逻辑删除 `sys_user_role` 对应关系并记录审计；重复分配不产生重复行（唯一键 `user_id,role_id` + 应用层幂等）

#### Scenario: 选人查询降级
- **WHEN** 经 `user-api` 选人查询失败（Fallback 降级）
- **THEN** 授权写主流程不被阻断（可手工输入 `user_id` 或提示后重试），不抛裸异常

### Requirement: 动态路由与权限集下发
系统 SHALL 按当前操作员下发：`/me/menus`（type 1/2 子树，含 `path/component/icon/hidden/requires_auth`，按 `sort` 排序）与 `/me/permissions`（type 3/4 的 `permission` 集合）；查询为本库本地联查（用户-角色-菜单），不跨系统调用。运行时 `user_id` 以网关注入 `X-User-Id`（或已验签令牌 `sub`）为准。

#### Scenario: 登录后取菜单树
- **WHEN** 已登录操作员请求 `/me/menus`
- **THEN** 返回其角色可达的目录/菜单子树（停用与 `hidden` 语义正确），前端据此动态生成路由

#### Scenario: 取权限集
- **WHEN** 已登录操作员请求 `/me/permissions`
- **THEN** 返回 `permission` 字符串集合，与后端 `@PreAuthorize` 使用同一字符串（按钮显隐同源）

#### Scenario: 细粒度鉴权
- **WHEN** 操作员访问无权限的管理接口
- **THEN** 返回 HTTP 403 + 系统段错误码 `10002`

### Requirement: 管理端 RBAC 管理页
`user-admin` SHALL 提供「菜单管理」「角色管理」（含用户-角色分配）页；前端路由/按钮使用 `user:resource:action` 权限标识，MUST NOT 硬编码角色名（`CLAUDE.md` §5.2 / §5.5）。

#### Scenario: 按权限显隐按钮
- **WHEN** 操作员权限集不含某按钮对应 `permission`
- **THEN** 该按钮不显示；直接调接口也被服务端 `@PreAuthorize` 拒绝（403）
