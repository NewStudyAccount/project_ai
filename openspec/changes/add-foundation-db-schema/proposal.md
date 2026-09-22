# Proposal: add-foundation-db-schema

## Why

统一认证与多系统 RBAC 已定架构（见 `add-unified-auth-center` 与 `docs/database-design.md`），但库表尚未作为独立可实施变更落地。  
缺少可执行的表结构契约时，脚手架与迁移会各自发挥，导致主键策略、审计字段、跨库 `user_id` 关联与唯一键漂移。  
本变更**只固化基础库表结构与约束**，作为后续 `auth-service` 与各系统 RBAC 的唯一 schema 输入。

## What Changes

- 在 **`auth_db`** 落地身份与基础表：`sys_sequence`、`sys_user`、`sys_dept`、可选 `sys_login_log`、`sys_sso_client`。
- 在 **各业务库 `{system}_db`** 落地 RBAC 与投影表：`sys_sequence`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`sys_user_ref`。
- 统一约束：14 位业务主键（发号表）、审计五件套、无物理外键、逻辑删除（主表）/关联表物理删、索引命名与数量上限。
- 明确跨库唯一逻辑关联：`user_id` = 认证中心 `sys_user.id`；业务库禁止存 `password_hash`。
- **不包含**：登录/SSO/JWT 运行时实现、RBAC 管理 API、业务系统自有业务表、生产数据与密钥。

## Capabilities

### New Capabilities

- `identity-schema`: `auth_db` 身份与发号表结构——用户/部门/序列/登录日志/SSO 客户端的字段、唯一键与逻辑关联。
- `rbac-schema`: 各系统 RBAC 表结构——角色/权限/用户-角色/角色-权限的字段、关联键与权限树。
- `user-projection-schema`: 子系统 `sys_user_ref` 表结构——仅展示字段、与中心 `user_id` 对齐、无凭证。

### Modified Capabilities

（无——`openspec/specs/` 当前为空；行为类需求已在 `add-unified-auth-center` 的 specs 中，本变更只约束 schema。）

## Impact

- **数据**：`auth_db` 与各 `{system}_db` 新建/对齐上述表；版本化迁移脚本 + down。
- **对象映射**：MyBatis-Plus Entity（含 `@TableLogic`、审计填充）与 DTO/VO 不得暴露 `password_hash`。
- **文档**：字段级权威见 `docs/database-design.md`；本变更 design 复述决策与迁移要点。
- **依赖**：MySQL 8；与 `add-unified-auth-center` 并行，可先建表后接服务。
