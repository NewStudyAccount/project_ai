# Proposal: service-impl-package-split

## Why

根目录 `CLAUDE.md` §5.6.2 与 `docs/template/base-system/architecture-design.md` §1.4.1/§1.4.2 已修订：Service 接口放 `service/`，实现类固定放 `service/impl/`，禁止接口与实现混在同一目录。但现存三个后端系统的 Service 实现类仍与接口同包同目录（`com.qjj.{system}.service.service`），与规范不一致。需将各系统 `XxxServiceImpl` 迁入 `service/impl/` 子包，调整包声明，消除规范与代码漂移。

## What Changes

- **`auth-service`（`backend/auth/auth-service`）**
  - 以下 5 个实现类由 `com.qjj.auth.service.service` 迁至 `com.qjj.auth.service.service.impl`，目录同步为 `service/impl/`：
    `AuditServiceImpl`、`ClientServiceImpl`、`CredentialServiceImpl`、`GrantServiceImpl`、`RbacServiceImpl`
- **`user-service`（`backend/user/user-service`）**
  - 以下 3 个实现类由 `com.qjj.user.service.service` 迁至 `com.qjj.user.service.service.impl`：
    `AuditServiceImpl`、`RbacServiceImpl`、`UserServiceImpl`
- **`blog-service`（`backend/blog/blog-service`）**
  - 以下 3 个实现类由 `com.qjj.blog.service.service` 迁至 `com.qjj.blog.service.service.impl`：
    `AuditServiceImpl`、`RbacServiceImpl`、`FileServiceImpl`
- **包声明与导入**：仅实现类文件的 `package` 声明由 `…service.service` 改为 `…service.service.impl`；接口、Controller、Mapper 等其余文件不动（经排查，无任何文件直接 import 实现类，注入均走接口）
- **扫描配置核验**：各 `Application` 类 `@SpringBootApplication(scanBasePackages = "com.qjj.{system}")` 为根包扫描，子包迁移天然覆盖，**预计无需修改**；`@MapperScan` 指向 `…service.mapper`，不受影响。实施时按 tasks 逐项核验并回填结论
- **规范基线**：本变更落地的是已修订的 `CLAUDE.md` §5.6.2，不改动任何规范正文

Non-goals（项目边界，对齐 `CLAUDE.md` §1 / §7.8）：

- **不做**接口重命名、类名调整、字段/方法改动等任何顺手重构
- **不做** Entity/DTO/VO/BO 等其他包结构的迁移或合并
- **不做** Maven 模块结构调整（common/framework/gateway 边界不动）
- **不做**博客业务域未完成实现类（如 `ArticleServiceImpl` 等）的补齐——那是 `blog-content-domain` 的范围
- **不触碰** `frontend/`、`deploy/`、`openspec/` 既有制品

## Capabilities

### New Capabilities

- `backend-code-structure`: Service 层接口与实现类的包结构与目录约定（三系统同构，接口 `service/`、实现 `service/impl/`）

### Modified Capabilities

（无）

## Impact

- **代码**：仅 `backend/{auth,user,blog}/*-service` 下共 **11 个** `*ServiceImpl.java` 文件的目录位置与 `package` 声明；无 API、表结构、错误码、依赖变化
- **数据**：无表结构变更
- **API**：对外接口路径、出参、`{code,msg,data}` 契约完全不变
- **构建**：每个系统改动后须 `mvn -q compile` 通过（`CLAUDE.md` §3.2）
- **协调**：`blog-content-domain` 变更当前 in-progress 且涉及 `blog-service`，本变更若与其并行实施，须先合入或在其实施间隙执行，避免同包文件移动冲突
