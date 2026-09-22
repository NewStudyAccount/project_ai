# Spec: blog-foundation

## ADDED Requirements

### Requirement: blog 工程与系统标识

系统 SHALL 以 `system_code=blog` 提供独立前后端与网关；SHALL 复用统一认证 JWT，不自建用户密码库。

#### Scenario: 登录接入

- **WHEN** 用户访问 blog 管理端
- **THEN** 使用统一认证令牌，网关校验后透传用户 id

### Requirement: 统一认证单点登录

blog 各前端 SHALL 通过认证中心 SSO 获取 JWT（`/auth/sso/authorize` → 回跳 `code` → `POST /auth/sso/token`）；SHALL NOT 提供本系统账密登录页；SHALL NOT 本地保存密码。

#### Scenario: 未登录进入管理端

- **WHEN** 用户未登录访问 blog-admin 受保护路由
- **THEN** 跳转认证中心 SSO/登录，成功后携 `code` 回跳并换取 JWT，全程无 blog 账密表单

#### Scenario: 已有全局会话免密

- **WHEN** 用户已在认证中心建立有效 sid
- **THEN** 进入 blog 无需再次输入密码即可获得 JWT

### Requirement: 基础代码契约（blog-common）

blog SHALL 提供本系统 `{system}-common`（`blog-common`），业务模块 MUST 复用其统一返回体、分页结构、错误码、全局异常、审计与发号、Long 序列化契约，SHALL NOT 另建平行实现。`blog-common` SHALL NOT 依赖其他系统代码模块。

#### Scenario: 统一返回体与分页

- **WHEN** 业务接口返回数据或分页列表
- **THEN** 使用 `{code,msg,data}` 与 `{records,total,size,current}` 结构

#### Scenario: Long 主键序列化

- **WHEN** 接口出参包含 Long 型 id
- **THEN** 以字符串形式序列化，避免前端精度丢失

#### Scenario: 业务模块不自建基础能力

- **WHEN** blog 业务模块需要返回体/异常/分页/发号
- **THEN** 引用 `blog-common`，不得在模块内复制实现

### Requirement: 用户引用与投影

blog SHALL 仅保存 `user_id` 引用与可选 `sys_user_ref` 投影，SHALL NOT 存储 password_hash。

#### Scenario: 展示作者

- **WHEN** 展示文章作者或评论人
- **THEN** 通过投影或 auth 批量查询获得用户名/姓名

### Requirement: 管理端与前台前端壳

blog SHALL 提供 `frontend/blog-admin` 与 `frontend/blog-portal`：组合式 Vue3 + TS + Pinia，请求统一走 `src/api`；主键字段类型 SHALL 为 string；admin SHALL 含 RBAC 管理页骨架，portal SHALL 支持匿名浏览骨架。SHALL NOT 自建密码登录。

#### Scenario: 管理端鉴权进入

- **WHEN** 未登录访问 blog-admin 受保护路由
- **THEN** 引导至统一认证获取 JWT，携带 Bearer 调用 blog API

#### Scenario: 前端对齐返回体

- **WHEN** 前端解析 blog 接口
- **THEN** 使用 `{code,msg,data}` 与分页 `{records,total,size,current}`，id 按字符串处理

#### Scenario: 权限码前端一致

- **WHEN** admin 按钮或菜单依赖权限
- **THEN** 使用与 `sys_permission.permission_code` / `BlogPermissions` 相同的权限码

### Requirement: Redis 缓存

blog SHALL 使用 Redis 缓存用户权限码并在赋权变更后失效；SHALL NOT 在 Redis 存储密码或生产密钥。

#### Scenario: 权限缓存

- **WHEN** 鉴权查询用户权限码
- **THEN** 优先读 Redis，未命中回源 DB 并回填；角色/权限变更后删除该用户缓存
