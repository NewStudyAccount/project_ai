## Why

全仓库多系统（认证中心、example 及后续业务系统）需要统一的**用户账号权威**：账号创建、启停/锁定、资料维护与对内用户查询。按前期已确认设计（`docs/template/user-center-design.md`、`user-center-database.md`）与认证中心**拆分**落地：用户主数据独立成系统，凭证/令牌归认证中心。这是首批系统脚手架的依赖起点（认证中心后续变更依赖本系统产出的 `user-api` 契约构件）。

## What Changes

- 新增独立系统 `backend/user/`（Maven 多模块：`user-common` / `user-framework` / `user-api` / `user-service`）与 `frontend/user-admin/`（Vue3 管理端壳），作为用户中心系统的完整落地
- 新建独立库 `user_db`（4 张表，结构见 `design.md`），建表 SQL 随本变更提供、**人工执行**（不引迁移框架，CLAUDE.md 2.2）
- 交付**账号管理**能力：创建账号（16 位发号）、资料维护、启停/锁定、管理审计
- 交付**对内查询契约** `user-api`（Feign + DTO/VO + Fallback，无 Entity）：by-id / by-username / profile
- 交付 `user-admin` 管理端壳（用户列表/详情/新建/启停的页面骨架，登录走认证中心 OIDC，本变更内可先本地放行联调）
- 版本基线落地：父 pom `dependencyManagement` 按 `docs/version-baseline.md` 锁定；管理端按 `CLAUDE.md` §5.5 结构
- 文档登记：`docs/error-code-ranges.md` 登记本系统业务错误码系统号

Non-goals（项目边界，对齐 `CLAUDE.md` §1 与设计稿 §7，防功能蔓延）：

- **不做**密码/凭证存储与登录验密（归认证中心）；本库**禁止** `password_hash` 或任何凭证密文字段
- **不做** RBAC / 权限码、组织架构（`dept_id` 仅预留字段）
- **不做** C 端注册/找回密码、SSO、令牌签发、OAuth Client 管理
- **不做**与认证中心的建号/凭证联动（最终一致编排留后续变更；本变更只交付「初始化凭证」所需的账号数据）
- **不做**跨库分布式事务、跨系统共享 Entity；**不做** Excel 导入导出、分布式定时、多语言强制交付（`CLAUDE.md` 2.2）

## Capabilities

### New Capabilities

- `user-account`: 用户账号主数据与生命周期（创建/资料维护/启停/锁定/管理审计），含 `user-admin` 管理端行为
- `user-query`: 对内用户查询契约（by-id / by-username / profile），供认证中心登录解析与业务系统投影使用

### Modified Capabilities

（无——`openspec/specs/` 尚无既有能力）

## Impact

- **新增代码**：`backend/user/**`、`frontend/user-admin/**`（多系统容器下新增系统；不触碰其他系统源码，`CLAUDE.md` §5.1 硬约束）
- **跨系统契约**：新增 `user-api` 薄构件（仅 DTO/VO + Feign + Fallback，无 Entity/Mapper/实现，`CLAUDE.md` §5.4）；后续 `scaffold-auth-center` 变更将依赖它（Feign 调用 + Fallback，超时由 `user-framework`/网关固定）
- **数据层**：新建独立库 `user_db`（仅 `user-service` 直连）；`sys_user.id` 全局唯一（16 位发号，出参 String），后续作为 OIDC `sub`
- **依赖**：全部按 `docs/version-baseline.md`（Spring Boot 3.5.16 线）；**无新增选型**，不改 `CLAUDE.md` §2
- **配置**：`application.yml` + `application-local.yml` + `application-test.yml`（`CLAUDE.md` §6.10）；连接信息登记 `docs/test-env.md`
- **错误码**：先在 `docs/error-code-ranges.md` 登记系统号，再进 `XxxErrorCodeEnum`（`CLAUDE.md` §6.4.2）
