# Design: align-user-rbac-ui

## Context

见 `proposal.md` Why。现状：`user-service` 的 `RbacServiceImpl.createMenu/createRole` 使用 `IdGenerator.nextId()`（16 位），与 `rbac-design.md` §2.0 及种子短号不一致；非 admin 的 `myMenuList` 未过滤 `status=1`、未做祖先闭包；结构校验不足。前端 `user-admin` 具备菜单/角色/账号/审计骨架，布局未完全对齐 `admin-ui-design.md`。约束：纯网关鉴权（业务服务不 `@PreAuthorize`）；不改表结构；不触碰 auth 系统。

## Goals / Non-Goals

**Goals:**

- `createMenu` / `createRole` 主键按 §2.0 分配
- 创建路径补齐深度/挂载/parent 校验与 `UserErrorCodeEnum` 业务码
- `/me/*`：admin 全量保持；非 admin 过滤 `status=1` + 祖先闭包组树
- `user-admin` 菜单/角色/账号页对齐布局模式

**Non-Goals:**

- 扩表、迁移历史 id、改账号/`user-api` 语义
- 组织模型、`@DataScope`、集中 RBAC
- 与 auth 系统共享代码或跨库调用

## Decisions

| 决策 | 选择 | 理由 | 备选 |
|------|------|------|------|
| 菜单/角色 id | 同 auth：应用层短号 `max+1` | §2.0 同构；本库独立 | 16 位（违反裁决） |
| 关联表 id | 运行时 framework 16 位可接受 | §2.0.3；业务键判重 | 短号段（容量不够） |
| 非 admin 过滤 | `selectBatchIds` 后滤 `status=1`，再补祖先 | 与 admin 路径对齐；可测 | SQL 递归（MySQL 成本） |
| 校验错误码 | `UserErrorCodeEnum` 201014–201016+ | 系统号 01 规划 201xxx | 复用 201012 语义（过载） |
| UI | tokens + P-Tree/P-List/D-Form/D-Panel | `admin-ui-design.md` | 大改组件结构（范围膨胀） |

**与 auth 变更的关系：** 同构算法、不同落点模块；**禁止**抽跨系统公共库（`CLAUDE.md` §5.1）。允许设计/任务写法平行复制。

## Risks / Trade-offs

- [与 auth 双份实现漂移] → 以 `rbac-design.md` §2.0 为唯一口径；两变更任务清单对齐
- [非 admin 闭包后树变“多”] → 符合可导航语义；测试以祖先链存在为准
- [系统号 01 未在 error-code-ranges 正式登记] → 实现前先补登记一行（文档）；码段仍用 201xxx
- [UI 仅改两页] → 其它页可后续按模式渐进，不阻塞本变更

## Migration Plan

1. 补 `docs/error-code-ranges.md` 系统号 01 登记（若缺失）
2. 后端短号 + 校验 + `/me/*` 修正
3. 前端壳与菜单/角色页布局收敛
4. 冒烟：短号形态、非法父拒绝、非 admin 不含停用节点、闭包组树
5. 回滚：revert；无 SQL 迁移

## Open Questions

- 无（扩展列、createRole 以外关联表短号强制化另案）
