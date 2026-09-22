# Spec: blog-foundation

## ADDED Requirements

### Requirement: blog 工程与系统标识

系统 SHALL 以 `system_code=blog` 提供独立前后端与网关；SHALL 复用统一认证 JWT，不自建用户密码库。

#### Scenario: 登录接入

- **WHEN** 用户访问 blog 管理端
- **THEN** 使用统一认证令牌，网关校验后透传用户 id

### Requirement: 用户引用与投影

blog SHALL 仅保存 `user_id` 引用与可选 `sys_user_ref` 投影，SHALL NOT 存储 password_hash。

#### Scenario: 展示作者

- **WHEN** 展示文章作者或评论人
- **THEN** 通过投影或 auth 批量查询获得用户名/姓名
