# 数据库迁移目录

| 路径 | 用途 |
|------|------|
| `init/` | **先执行**：实例级 `CREATE DATABASE`（auth_db/file_db/blog_db/system_db） |
| `migration/auth_db/` | `auth_db` 版本化 up（Flyway 命名 `V*.sql`） |
| `migration/file_db/` | `file_db` 版本化 up（file-service 独占） |
| `migration/blog_db/` | `blog_db` 版本化 up（blog 服务独占） |
| `migration/system_db/` | 各业务库 RBAC/投影 up（模板，按系统复制或改库名执行） |
| `rollback/` | 配套 down（非 Flyway 自动；人工/流水线回滚用 `U*.sql`） |
| `seed/local/` | 仅 local/test 种子（发号初始化、角色权限等）；禁止生产执行 |
| `checks/` | 建库后校验 SQL |

**执行顺序：**

1. `init/00_create_databases.sql` — 建库（幂等，可重复执行）
2. `migration/<db>/V*.sql` — 建表（按库分别对目标库执行）
3. `seed/local/*.sql` — 仅 local/test 种子
4. 需要时用 `rollback/<db>/U*.sql` 回滚表

约定：

- 字符集 `utf8mb4`；无物理外键；主键 `id BIGINT` **禁止 AUTO_INCREMENT**（14 位由应用发号）。
- 业务主表含审计五件套与 `deleted`；`sys_user_role` / `sys_role_permission` **物理删**。
- 索引名：`uk_表_字段` / `idx_表_字段`；单表索引 ≤ 5。
- 字段权威：`docs/database-design.md`；对齐变更：`add-foundation-db-schema`。
