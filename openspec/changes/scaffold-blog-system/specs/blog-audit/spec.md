# blog-audit · 操作审计

## ADDED Requirements

### Requirement: 关键写操作审计
系统 SHALL 在关键写（RBAC 菜单/角色/授权、文件上传/删除等）**事务提交成功后**写入 `blog_audit_log`（或统一 AOP `@Audit`，二者只选一种）：记录操作人、时间、动作、结果、目标；失败记 `result=FAIL` 且 MUST NOT 吞业务异常。审计内容 MUST NOT 含密码或完整令牌。

#### Scenario: 成功写审计
- **WHEN** 关键写业务提交成功
- **THEN** 落一条审计（`action` 如 `MENU_CREATE`/`ROLE_UPDATE`/`ROLE_ASSIGN`/`FILE_UPLOAD`/`FILE_DELETE`，`actor_user_id` = 网关注入 `X-User-Id`，`target_type`/`target_id`，`detail` 摘要已脱敏）

#### Scenario: 业务失败仍留痕
- **WHEN** 关键写业务失败
- **THEN** 记录 `result=FAIL` 类审计（或按实现约定），原始业务错误仍返回给调用方

### Requirement: 审计分页查询
系统 SHALL 提供审计分页（`GET /api/v1/audit-logs`）：支持 `action?`、`beginTime?`、`endTime?` 筛选；`detail` 出参已脱敏；排序白名单。

#### Scenario: 按动作与时间筛选
- **WHEN** 管理员按 action 与时间范围分页查询
- **THEN** 返回 `{records,total,size,current}`，`id` 为 String，`detail` 无密码/完整令牌

### Requirement: 管理端审计页
`blog-admin` SHALL 提供审计日志分页查询页（按 action/时间筛选）。

#### Scenario: 查询展示
- **WHEN** 管理员打开审计页并筛选
- **THEN** 列表展示与接口契约一致，失败展示 `msg`
