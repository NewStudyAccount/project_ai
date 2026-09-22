# Entity 映射检查清单

适用：`add-foundation-db-schema` 落地后的 MyBatis-Plus Entity / DTO / VO。  
提交前逐项确认。

## 通用

- [ ] 实体表名、字段 `snake_case` 与 `docs/database-design.md` 一致
- [ ] 主键 `Long id`，**无** `@TableId(type = AUTO)`；策略为应用赋值 14 位
- [ ] 审计字段：`createTime`/`updateTime`/`createBy`/`updateBy`/`deleted`
- [ ] 业务主表：`deleted` + `@TableLogic`
- [ ] 关联表 `sys_user_role` / `sys_role_permission`：删除用物理 DELETE，不走逻辑删
- [ ] 无物理外键映射为 `@TableField(exist = false)` 误写 JOIN 实体穿透

## auth_db

- [ ] `SysUser` 含 `passwordHash`，**仅** auth-service 可依赖该实体
- [ ] `SysUserVO` / 对外 DTO **不含** `passwordHash`
- [ ] `SysDept` / `SysSequence` / `SysLoginLog` / `SysSsoClient` 字段与脚本一致

## 业务库 RBAC

- [ ] **无** `passwordHash` 或等价字段
- [ ] `SysUserRole.userId` 为 `Long`；API 出参序列化为 **string**（Long 全局序列化）
- [ ] `SysUserRef` 仅展示字段；无角色/权限嵌套写回中心
- [ ] `SysRole.dataScope` 为 `TINYINT` 枚举码，前端不传枚举名

## 跨模块

- [ ] 业务模块不 `import` auth 的 User Entity；选人走 API/Feign
- [ ] 排序字段白名单，禁止把 `orderBy` 拼进 SQL
