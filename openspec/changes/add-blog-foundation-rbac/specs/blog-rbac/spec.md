# Spec: blog-rbac

## ADDED Requirements

### Requirement: 本地角色权限

blog SHALL 在 `blog_db` 维护 `sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`；角色与权限 MUST 含 `system_code=blog`。

#### Scenario: 授予 blog 角色

- **WHEN** 将 blog 角色授予某用户
- **THEN** 仅影响 blog 系统权限，不改变其他系统

### Requirement: 权限码鉴权

受控写接口 SHALL 校验 `permission_code`；无权限 SHALL 返回 403 语义。权限注解/切面与权限码常量 SHALL 落在 `blog-common`，与 `sys_permission.permission_code` 对齐。

#### Scenario: 无发布权限

- **WHEN** 用户无 `blog:post:publish` 调用发布接口
- **THEN** 拒绝执行

#### Scenario: 权限码统一常量

- **WHEN** 服务方法声明所需权限
- **THEN** 使用 `blog-common` 中的权限码常量/注解，禁止魔法字符串散落
