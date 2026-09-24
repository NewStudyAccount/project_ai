# fixbug 记录

> 本目录存放**已定位并修复**的缺陷记录，供后续排查对照与新人避坑。
>
> - 不写未验证的猜测；每条尽量带**现象 → 根因 → 改动 → 验证**。
> - 不承载业务契约（契约在 `CLAUDE.md` / openspec）；不写生产密钥。
> - 文件名建议：`YYYY-MM-DD-<范围>.md`。

| 文件 | 范围 |
|------|------|
| [2026-09-24-startup-auth-oidc.md](2026-09-24-startup-auth-oidc.md) | 启动配置、鉴权模型、MapperScan、OIDC/Token、权限装载 |
| [2026-09-25-unify-login-sso-gateway.md](2026-09-25-unify-login-sso-gateway.md) | 唯一登录门面、SSO Cookie/过滤器顺序、token CORS、统一登出/RT、网关 lb 503、RBAC `/me/*` 空与 menu component |

**关联种子 SQL（冒烟）：**

- [`deploy/db/seed/2026-09-24-smoke-admin-auth-portal.sql`](../../../deploy/db/seed/2026-09-24-smoke-admin-auth-portal.sql)（admin/admin123、auth-portal-spa）
- [`deploy/db/seed/2026-09-24-unify-login-facade-clients.sql`](../../../deploy/db/seed/2026-09-24-unify-login-facade-clients.sql)（user-admin-spa、auth-admin-spa）
- [`deploy/db/seed/2026-09-25-user-db-rbac-framework.sql`](../../../deploy/db/seed/2026-09-25-user-db-rbac-framework.sql)（user_db L2+域+admin）
- [`deploy/db/seed/rbac-framework-template.sql`](../../../deploy/db/seed/rbac-framework-template.sql)（新系统复制模板，见 `rbac-design.md` §5）
