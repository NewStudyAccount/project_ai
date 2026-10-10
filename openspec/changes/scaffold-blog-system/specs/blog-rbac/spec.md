# blog-rbac · 本系统自持 RBAC

## ADDED Requirements

### Requirement: 菜单管理（资源树 CRUD）
系统 SHALL 提供本系统菜单/权限资源树管理：节点 `type` 1 目录 / 2 菜单 / 3 按钮 / 4 接口（不下发前端），含 `path` / `component` / `permission?` / `sort` 等；`permission` 标识格式 `blog:resource:action`（全小写、本系统内唯一，应用层校验；目录可留空）。表结构按 `rbac-design.md` §2 同构建于 `blog_db`。关键写 SHALL 走写幂等（`@Idempotent`）并记录操作审计。

#### Scenario: 创建菜单节点
- **WHEN** 管理员提交合法的菜单节点（type、permission、排序等通过校验）
- **THEN** 系统 16 位发号落库并记录审计；出参 `id` 为 String

#### Scenario: permission 非法
- **WHEN** 提交的 `permission` 首段非 `blog` 或系统内已存在同标识
- **THEN** 返回 HTTP 200 + 业务段错误码（`2xxxxx`）与明确 `msg`，不落库

#### Scenario: 树形查询
- **WHEN** 管理员查询菜单树
- **THEN** 按 `parent_id` 层级与 `sort` 返回树结构

#### Scenario: 更新与删除节点
- **WHEN** 管理员更新节点，或删除节点（存在子节点时按实现约定拒绝或级联）
- **THEN** 更新/逻辑删除成功并记录审计；重复幂等键请求返回首次 `Result`，不重复写入

### Requirement: 角色管理与授权
系统 SHALL 提供角色分页（`current`/`size`、`name?` 筛选、排序白名单）与角色 CRUD、角色-菜单授权；关键写 SHALL `@Idempotent` 并记录审计。列表类查询可 `@RateLimit`。

#### Scenario: 角色分页查询
- **WHEN** 管理员按名称筛选并分页查询角色
- **THEN** 返回 `{records,total,size,current}`，`id` 均为 String；非法 `orderBy` 拒绝或回落默认 `create_time`

#### Scenario: 创建角色并授权
- **WHEN** 管理员创建角色并提交角色-菜单授权
- **THEN** 落库 `sys_role` / `sys_role_menu` 并记录审计；重复幂等键返回首次 `Result`

#### Scenario: 角色 code 重复
- **WHEN** 提交已存在的角色 `code`
- **THEN** 返回业务段错误码与明确 `msg`，不落库

### Requirement: 用户-角色分配
系统 SHALL 支持用户-角色分配/回收（`sys_user_role`，`user_id` 为用户中心 `sys_user.id` 逻辑引用、无物理外键）；「选人」SHALL 经用户中心 `user-api` 只读查询（Feign + Fallback），MUST NOT 直连用户库表。关键写 `@Idempotent` + 审计。

#### Scenario: 分配与回收角色
- **WHEN** 管理员为某 `user_id` 分配或回收角色
- **THEN** 写入/逻辑删除 `sys_user_role` 并记录审计；重复分配不产生重复行

#### Scenario: 选人查询降级
- **WHEN** 经 `user-api` 选人失败（Fallback）
- **THEN** 授权写主流程不被阻断（可手工输入 `user_id` 或提示重试），不抛裸异常

### Requirement: 动态路由与权限集下发
系统 SHALL 按当前操作员下发：`GET /api/v1/me/menus`（type 1/2 子树，含 `path`/`component`/`icon`/`hidden`/`requires_auth`，按 `sort`）与 `GET /api/v1/me/permissions`（type 3/4 的 `permission` 集合）；本库本地联查。运行时身份以网关注入 `X-User-Id`（或已验签 `sub`）为准，MUST NOT 信任可伪造客户端头。

#### Scenario: 登录后取菜单树
- **WHEN** 已登录操作员请求 `/me/menus`
- **THEN** 返回其角色可达的目录/菜单子树，前端据此动态生成路由

#### Scenario: 取权限集
- **WHEN** 已登录操作员请求 `/me/permissions`
- **THEN** 返回 `permission` 字符串集合，与前端按钮显隐使用同一字符串（软校验同源）

#### Scenario: 未登录访问
- **WHEN** 无有效登录态请求 `/me/*`
- **THEN** 网关返回 HTTP 401 + 系统段 `10001`

### Requirement: 管理端 RBAC 管理页
`blog-admin` SHALL 提供「菜单管理」（树 CRUD）与「角色管理」（分页、角色-菜单授权、用户-角色分配）页；登录后经 `/me/menus` 动态生成路由、`/me/permissions` 控制按钮显隐；MUST NOT 硬编码角色名。

#### Scenario: 按权限显隐按钮
- **WHEN** 操作员权限集不含某按钮对应 `permission`
- **THEN** 该按钮不显示

#### Scenario: 菜单树表单
- **WHEN** 管理员在菜单管理页完成 type 1/2/3/4 节点的新建/编辑/删除
- **THEN** 行为与后端契约一致，`id` 一律 `string`
