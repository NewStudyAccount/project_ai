# user_db SQL 执行记录

- 变更：scaffold-user-center
- SQL：01_user_tables.sql、02_rbac_tables.sql
- 目标库：user_db（utf8mb4 / InnoDB）
- 状态：测试环境已执行。不得由代码或流水线自动执行生产 SQL。
- 生产执行前：必须按 CLAUDE.md §7 先征询。

| 环境 | 执行时间 | 执行人 | 结果 | 备注 |
|------|----------|--------|------|------|
| test | 2026-09-24 | MiMo（用户确认） | 成功 | 建库 user_db + 执行 01/02；同日导入 seed 2026-09-24-smoke-admin-auth-portal.sql |
