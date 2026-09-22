# Spec: user-projection-schema

## ADDED Requirements

### Requirement: 投影表无凭证

`sys_user_ref` SHALL NOT 包含 `password_hash` 或等价凭证字段；SHALL NOT 作为用户权威源。

#### Scenario: 字段边界

- **WHEN** 检查 `sys_user_ref` 表结构
- **THEN** 不含密码类字段，主键为 `user_id`

### Requirement: 与中心用户的引用对齐

`sys_user_ref.user_id` SHALL 与认证中心 `sys_user.id` 同语义；该表 SHALL 可删除数据后由中心重建。

#### Scenario: 主键即中心用户 id

- **WHEN** 投影一行 `user_id = 26092200000001`
- **THEN** 对应认证中心同一 `sys_user.id`，本地不自造用户主键

### Requirement: 仅展示字段

`sys_user_ref` SHALL 包含 `username`、`real_name`、`status`、可选 `dept_id`/`dept_name`、`sync_time` 等展示与对账字段；`status` SHALL 仅用于展示。

#### Scenario: 展示字段齐全

- **WHEN** 读取投影行
- **THEN** 可获用户名、姓名、状态与同步时间，无需密码

#### Scenario: 状态不参与放行约束

- **WHEN** 在 schema 层定义 `status`
- **THEN** 该字段类型为 TINYINT 且无“鉴权通过”语义约束，放行由令牌与 RBAC 表承担
