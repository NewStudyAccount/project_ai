# 数据库迁移目录

| 路径 | 用途 |
|------|------|
| `migration/auth_db/` | `auth_db` 版本化 up（Flyway 命名 `V*.sql`） |
| `migration/system_db/` | 各业务库 RBAC/投影 up（模板，按系统复制或改库名执行） |
| `rollback/` | 配套 down（非 Flyway 自动；人工/流水线回滚用 `U*.sql`） |
| `seed/local/` | 仅 local/test 种子（发号初始化等）；禁止生产执行 |
| `checks/` | 建库后校验 SQL |

约定：

- 字符集 `utf8mb4`；无物理外键；主键 `id BIGINT` **禁止 AUTO_INCREMENT**（14 位由应用发号）。
- 业务主表含审计五件套与 `deleted`；`sys_user_role` / `sys_role_permission` **物理删**。
- 索引名：`uk_表_字段` / `idx_表_字段`；单表索引 ≤ 5。
- 字段权威：`docs/database-design.md`；对齐变更：`add-foundation-db-schema`。
