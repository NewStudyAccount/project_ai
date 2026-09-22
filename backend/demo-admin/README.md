# demo-admin（示例系统）说明

- `system_code`：`demo-admin`
- 网关：`demo-admin-gateway` 仅验 JWT 透传 `X-User-Id`，**不做**业务 RBAC
- 权限表：执行 `deploy/db/migration/system_db/V2__system_rbac_foundation.sql` 到本系统库
- 选人/补洞：调用认证中心 `GET /auth/users`、`GET /auth/users/batch`
- 鉴权：服务内 `@RequiresPermission(code=...)` + `PermissionChecker`

完整业务服务可按 CLAUDE.md 在 `backend/demo-admin/` 下继续扩展 `demo-admin-*-service`。
