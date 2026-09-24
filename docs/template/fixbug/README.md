# fixbug 记录

> 本目录存放**已定位并修复**的缺陷记录，供后续排查对照与新人避坑。
>
> - 不写未验证的猜测；每条尽量带**现象 → 根因 → 改动 → 验证**。
> - 不承载业务契约（契约在 `CLAUDE.md` / openspec）；不写生产密钥。
> - 文件名建议：`YYYY-MM-DD-<范围>.md`。

| 文件 | 范围 |
|------|------|
| [2026-09-24-startup-auth-oidc.md](2026-09-24-startup-auth-oidc.md) | 启动配置、鉴权模型、MapperScan、OIDC/Token、权限装载 |

**关联种子 SQL（冒烟）：** [`deploy/db/seed/2026-09-24-smoke-admin-auth-portal.sql`](../../../deploy/db/seed/2026-09-24-smoke-admin-auth-portal.sql)（admin/admin123、auth-portal-spa）
