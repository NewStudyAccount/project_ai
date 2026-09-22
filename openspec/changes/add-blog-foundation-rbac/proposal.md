# Proposal: add-blog-foundation-rbac

## Why

`system_code=blog` 作为首个业务系统，需要工程骨架、独立网关/库，以及**本系统本地 RBAC**（角色/权限/用户-角色），并接入统一认证 JWT 与用户投影。  
无基础层则无法安全进入具体文章/评论业务。  
同时必须按 `CLAUDE.md` §6.4 落地 **`blog-common` 基础契约**，避免业务模块自建返回体/异常/分页，防止与 auth/file 再复制第三套。

## What Changes

- 创建 `backend/blog/{blog-common,blog-gateway,blog-content-service}` 与 **`frontend/blog-admin|blog-portal`**（结构见 design D5：api/router/stores/types/utils、统一 axios 与令牌策略、权限码前端对齐）。
- **admin 壳**：登录态布局 + RBAC 管理页骨架（角色/权限/用户-角色）；**portal 壳**：匿名可读骨架。本变更**不含**文章编辑器与评论 UI。
- **`blog-common` 按 §6.4 做全**：统一返回体与分页、错误码、全局异常、分页入参与排序白名单、审计五件套与 14 位发号、Long→String 序列化、JWT 验签（与 auth 同 `iss`/密钥）、权限注解/切面、Swagger 公共配置。  
  **不依赖** `platform-common`；各系统自持 common。
- **`blog_db`** 建 RBAC：`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`sys_user_ref`、`sys_sequence`。
- 网关验 JWT 透传 uid；服务方法级 `permission_code` 鉴权。
- **统一认证 SSO**：blog-admin **不自建登录页**，走 `auth-portal` / `/auth/sso/authorize` + `POST /auth/sso/token` 换 JWT（对齐 unified-auth 单点登录）。
- **Redis**：`blog-content-service` 权限码缓存（赋权失效）；对接 CLAUDE.md 技术栈，禁止未使用。
- 用户仅 `user_id` 引用 + `sys_user_ref` 投影；选人调 auth 用户 API。
- 初始化 `system_code=blog` 权限码与种子角色（local/test）。
- **不包含**：文章/分类/标签/评论业务 API、file 服务实现、ES、数据范围过滤引擎。

## Capabilities

### New Capabilities

- `blog-foundation`: blog 工程与网关、`blog-common` 基础契约、**admin/portal 前端壳**、认证对接、投影同步触发点。
- `blog-rbac`: 本地角色权限模型、赋权、权限码鉴权。

### Modified Capabilities

（无。）

## Impact

- **服务**：blog 三模块；`blog-common` 自持 §6.4 与 JWT 验签；选人依赖 auth 用户 API。
- **前端**：`blog-admin`（EP + Pinia + RBAC 页骨架）、`blog-portal`（匿名壳）；登录走统一认证。
- **数据**：`blog_db` RBAC + 投影表 + `sys_sequence`。
- **约定**：业务模块禁止自建 Result/异常/分页；不 import 其他系统代码。
