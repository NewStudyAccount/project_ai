# Spec: blog-rbac

## ADDED Requirements

### Requirement: 本地角色权限

blog SHALL 在 `blog_db` 维护 `sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`；角色与权限 MUST 含 `system_code=blog`。

#### Scenario: 授予 blog 角色

- **WHEN** 将 blog 角色授予某用户
- **THEN** 仅影响 blog 系统权限，不改变其他系统

### Requirement: 权限码鉴权

受控写接口 SHALL 校验 `permission_code`；无权限 SHALL 返回 403 语义。

#### Scenario: 无发布权限

- **WHEN** 用户无 `blog:post:publish` 调用发布接口
- **THEN** 拒绝执行
