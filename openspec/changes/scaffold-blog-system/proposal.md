# Proposal: scaffold-blog-system

## Why

多系统容器需要一套可复制的业务系统落地样例。以已确认设计 `docs/template/example-system-design.md`（基础模板：完整标准链路 + 横切能力示范）为准，新建系统名 **`blog`**，交付工程骨架与横切能力（RBAC、文件、审计、对内契约、发号、幂等/限流），作为后续业务系统的标准参照；本变更**不预置博客业务表**，业务域随后续变更设计。

## What Changes

- 新增独立系统 `backend/blog/`（Maven 多模块：`blog-common` / `blog-framework` / `blog-api` / `blog-service` / `blog-gateway`）与 `frontend/blog-admin/`（Vue3 管理端），系统名与包名前缀为 `blog`（`com.qjj.blog.*`）
- 新建独立库 `blog_db`（7 张表：`sys_sequence` + RBAC 4 + `blog_audit_log` + `sys_file`），建表 SQL 随本变更提供、**人工执行**（`CLAUDE.md` §2.2）
- 交付 **RBAC 全套**（`rbac-design.md` §2 同构 4 表）：菜单树 CRUD、角色管理、用户-角色分配、`/me/menus` 动态路由、`/me/permissions` 权限集（软校验/按钮显隐，纯网关登录态）
- 交付 **文件管理**（MinIO，裁剪源 `object-storage-design.md`）：单文件上传、列表/元数据、预签名 URL、逻辑删除；管理端交互对齐 `example-system-design.md` §7.1 与 `assets/rbac-admin-proto.html`
- 交付 **操作审计**：`blog_audit_log`（通用 `target_type`/`target_id` 形态，覆盖 MENU/ROLE/FILE）
- 交付 **对内契约** `blog-api`（Feign + DTO/VO + Fallback，无 Entity）：`GET /internal/files/{id}` 文件元数据 + `GET /internal/ping` 探针
- 交付横切能力示范（`example-system-design.md` §3/§4）：16 位发号、`@Idempotent`、`@RateLimit`、统一返回体/错误码/分页/Long→String/审计字段
- 交付 `blog-admin` 管理端：布局壳（动态路由）+ 菜单/角色/授权 + 文件管理 + 审计日志查询
- 网关 `blog-gateway` 按 `gateway-design.md` 裁剪落地（路由/JWKS/`X-User-*`/CORS/错误体；本文只登记 blog 取值）
- 版本基线：父 pom `dependencyManagement` 按 `docs/version-baseline.md` 锁定；前端主框架版本先登记该表再锁 `package-lock.json`
- 文档登记：`docs/error-code-ranges.md` 业务系统号（实施时分配，不预占）；连接信息/端口/信任域名登记 `docs/test-env.md` / `docs/port-registry.md`

Non-goals（项目边界，对齐 `CLAUDE.md` §1 与设计稿 §1.2/§11）：

- **不做**博客业务域（文章、分类、标签、评论、阅读量等）——不预置示例业务表，业务表由后续变更设计
- **不做**用户主数据、密码/凭证、SSO、令牌、OAuth Client（归用户中心/认证中心）
- **不做**跨系统角色/菜单同步、集中式 RBAC（各系统自持）
- **不做**完整 OIDC/SSO 接线（壳阶段可 local 联调放行；接线清单随认证中心落地）
- **不做**批量上传、Excel、分布式定时、多语言强制交付、MinIO 对象定时清理（`CLAUDE.md` §2.2）
- **不做**跨库分布式事务、跨服务共享 Entity；**不做**生产密钥入仓

## Capabilities

### New Capabilities

- `blog-rbac`: 本系统自持 RBAC（菜单树/角色/用户-角色分配、`/me/menus` 动态路由、`/me/permissions` 权限码软校验；表结构同构 `rbac-design.md` §2）
- `blog-file`: MinIO 文件生命周期（上传白名单/大小校验、列表、预签名 URL、逻辑删除；策略以 `object-storage-design.md` 为准）
- `blog-audit`: 操作审计（`blog_audit_log`：RBAC 与文件关键写记录操作人/时间/动作/结果，`detail` 脱敏）
- `blog-internal-api`: 对内 Feign 契约（文件元数据查询 + ping 探针 + Fallback 降级，仅服务间可达）

### Modified Capabilities

（无——`openspec/specs/` 尚无既有能力）

## Impact

- **新增代码**：`backend/blog/**`、`frontend/blog-admin/**`（多系统容器下新增系统；不触碰其他系统源码，`CLAUDE.md` §5.1）
- **跨系统契约**：新增 `blog-api` 薄构件（DTO/VO + Feign + Fallback，无 Entity/Mapper/实现）；角色分配「选人」只读调用用户中心 `user-api`（须 Fallback，禁止直连其库表）
- **数据层**：新建独立库 `blog_db`（仅 `blog-service` 直连）；发号唯一域 = 本库
- **依赖**：全部按 `docs/version-baseline.md`；**无新增选型**，不改 `CLAUDE.md` §2（MinIO SDK 封装进 `blog-framework`，契约按 `object-storage-design.md` §3，版本见基线）
- **配置**：`application.yml` + `application-local.yml` + `application-test.yml`（`CLAUDE.md` §6.10）；连接信息 `docs/test-env.md`
- **错误码**：实施时先在 `docs/error-code-ranges.md` 登记系统号，再进 `BlogErrorCodeEnum`；系统段 `1xxxx`/校验段 `3xxxx` 对齐固定码值
- **网关**：`blog-gateway` 设计裁剪源 = `gateway-design.md`；`lb://` 必须显式依赖 LoadBalancer
