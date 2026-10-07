# Design: align-auth-rbac-ui

## Context

见 `proposal.md` Why。现状：`auth-service` 的 `RbacServiceImpl.createMenu/createRole` 使用 `IdGenerator.nextId()`（16 位），与 `rbac-design.md` §2.0 短号规范及种子数据（1001/2001/90001…）不一致；结构校验不足；`/me/*` 对 status/祖先闭包处理不完整。前端 `auth-admin` 已有基本列表/树表单，但未按 `admin-ui-design.md` 收敛。约束：纯网关鉴权（`CLAUDE.md` §5.2）；auth-service 例外可 `@PreAuthorize`；不改表结构。

## Goals / Non-Goals

**Goals:**

- `createMenu` / `createRole` 主键按 §2.0 分配，消除同表双 ID 空间
- 创建路径补齐深度/挂载/parent 校验与业务错误码
- `/me/menus` / `/me/permissions` 行为与 §3/§6.4 一致（status 过滤、祖先闭包、admin 全量）
- `auth-admin` 页面骨架对齐 `admin-ui-design.md` 四模式

**Non-Goals:**

- 扩表、数据迁移历史 16 位 id、改 API 路径
- 改登录/OIDC/Client/Token 逻辑
- 集中式 RBAC、组织模型、`@DataScope`

## Decisions

| 决策 | 选择 | 理由 | 备选 |
|------|------|------|------|
| 菜单 id 分配 | 应用层按深度/号段 `max+1`，`synchronized` 与插入同临界区 | 符合 §2.0；管理写频率低 | 发号器短号模式（超范围）；DB 自增（禁止） |
| 角色 id | 段内 `9001–9999` `max+1` | §2.0.3；种子 9001=admin 可对照 | 继续 16 位（违反已裁决） |
| 关联表 id | 运行时可继续 framework 16 位 | §2.0.3 已裁决代理键无语义 | 强制短号（容量不足 999） |
| 校验落点 | `RbacServiceImpl` 创建路径 + BizException | Service 业务规则（§5.6）；Controller 只 Bean Validation | AOP 注解（过度） |
| 祖先闭包 | 查询授权 menuIds 后向上补 parent 链再过滤 type/status 组树 | 断链防护可测；仅本库 | 只挂根（弱） |
| UI 收敛 | 样式类对齐 tokens；不引新组件库 | `admin-ui-design.md`；CLAUDE.md §5.5 | 自绘组件（禁止平行 UI） |
| 错误码 | `AuthErrorCodeEnum` 追加 202016–202018 等 | 系统号 02 已登记 | 新增系统号（禁止） |

**`createMenu` 分配算法（与 `rbac-design.md` §2.0.5 一致）：**

```text
type=1/2: depth = 深度(parent_id)+1; reject depth>4
          id = depth*1000 + (本层 max+1 相对序号)
type=3:   require parent.type==2; id ∈ [90001,100000) max+1
type=4:   require parent.type==2; id ∈ [5001,6000) max+1
```

## Risks / Trade-offs

- [同层并发创建撞 id] → 同步块 + 主键冲突重试一次；管理台并发极低
- [历史 16 位菜单行仍在] → 不迁移（禁止改 id）；新旧 id 可并存于树（parent_id 引用仍有效）；文档已认
- [type=4 号段 5001 与旧文 5000] → 以统一后的 `rbac-design.md` 为准（5001–5999）
- [UI 调整造成短暂样式差异] → 仅收敛壳与两页；其它页保持 P-List 渐进对齐

## Migration Plan

1. 合并后端短号分配与校验（无 SQL）
2. 调整 `/me/*` 过滤与闭包
3. 前端壳与菜单/角色页样式收敛
4. 本地冒烟：新建 d=1/2/按钮 → id 形态正确；非法挂载拒绝；/me 不含停用节点
5. 回滚：revert 提交即可（无数据迁移）

## Open Questions

- 无（扩展列建表、历史 id 清洗另案，不阻塞本变更）
