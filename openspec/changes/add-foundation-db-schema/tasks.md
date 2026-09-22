# Tasks: add-foundation-db-schema

## 1. 迁移脚本骨架

- [x] 1.1 建立版本化迁移目录（Flyway/SQL）并约定命名 `V1__auth_db_foundation.sql`、`V2__system_rbac_foundation.sql`
- [x] 1.2 编写 `auth_db` up/down：`sys_sequence`、`sys_user`、`sys_dept`、`sys_login_log`、`sys_sso_client`
- [x] 1.3 编写 `{system}_db` up/down：`sys_sequence`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`sys_user_ref`

## 2. 约束与索引落地

- [x] 2.1 全表 14 位主键列定义与注释；禁自增
- [x] 2.2 审计五件套与 `deleted`（主表）；关联表物理删约定写入脚本注释
- [x] 2.3 创建 UK/IDX，命名 `uk_/idx_表_字段`，单表 ≤5；无物理外键

## 3. 对齐与验证

- [x] 3.1 与 `docs/database-design.md` 字段级对照，修正偏差
- [ ] 3.2 在测试库执行 up/down 回归；校验唯一键拒绝重复
- [x] 3.3 输出 Entity 映射检查清单（`password_hash` 不进业务模块；`user_id` 类型 string 出参约定）

## 4. 收尾

- [x] 4.1 测试种子脚本（可选，仅 local/test）：发号初始化
- [x] 4.2 确认无生产密钥/真实数据入仓；迁移可重复评审
