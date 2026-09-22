# Proposal: add-blog-foundation-rbac

## Why

`system_code=blog` 作为首个业务系统，需要工程骨架、独立网关/库，以及**本系统本地 RBAC**（角色/权限/用户-角色），并接入统一认证 JWT 与用户投影。  
无基础层则无法安全进入具体文章/评论业务。

## What Changes

- 创建 `backend/blog/{blog-common,blog-gateway,blog-content-service}` 与 `frontend/blog-admin|blog-portal` 壳。
- **`blog_db`** 建 RBAC：`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`sys_user_ref`、`sys_sequence`。
- 网关验 JWT 透传 uid；服务方法级 `permission_code` 鉴权。
- 用户仅 `user_id` 引用 + `sys_user_ref` 投影；选人调 auth 用户 API。
- 初始化 `system_code=blog` 权限码与种子角色（local/test）。
- **不包含**：文章/分类/标签/评论业务 API、file 服务实现、ES。

## Capabilities

### New Capabilities

- `blog-foundation`: blog 工程与网关、认证对接、投影同步触发点。
- `blog-rbac`: 本地角色权限模型、赋权、权限码鉴权。

### Modified Capabilities

（无。）

## Impact

- **服务**：blog 三模块；依赖 platform-common JWT 验签、auth 用户 API。
- **数据**：`blog_db` RBAC + 投影表。
