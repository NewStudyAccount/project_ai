# Tasks: service-impl-package-split

> 依据 `CLAUDE.md` §8：只按本清单实施，完成一项勾一项；不擅自修改 proposal/specs/design。

## 1. 前置核验

- [x] 1.1 全仓复核字符串引用：`grep -rn "service.service" backend --include=*.{java,yml,yaml,xml,properties}`，确认除接口所在包声明与 `@MapperScan`（mapper 包）外，无显式引用会被迁移破坏的条目；发现异常回填 `design.md` D3（结论：仅接口引用，无破坏点，已回填 D3）
- [x] 1.2 确认 `blog-content-domain` 状态：若仍未归档，与实施窗口协调（design D4），`blog-service` 部分排在其后（结论：`blog-content-domain` 31/33 未归档，且 `article-content-markdown-only` 11/14 进行中并持有 blog-service 同包未提交改动 → **第 4 节 blog-service 迁移延后**，待相关变更归档后实施）

## 2. auth-service 迁移

- [x] 2.1 `git mv` 5 个实现类至 `src/main/java/com/qjj/auth/service/service/impl/`：`AuditServiceImpl`、`ClientServiceImpl`、`CredentialServiceImpl`、`GrantServiceImpl`、`RbacServiceImpl`
- [x] 2.2 各文件 `package` 声明改为 `com.qjj.auth.service.service.impl`；按编译结果最小修正 import（补接口 import：Audit/Client/Credential/Grant/Rbac/TokenManagement）
- [x] 2.3 核验 `AuthApplication` `scanBasePackages` 覆盖新子包（预计零改动），异常则最小修正（`com.qjj.auth` 根包扫描，零改动）
- [x] 2.4 `cd backend/auth && mvn -q compile` 通过；一次提交（`refactor(auth): Rbac等实现类迁入service/impl子包` → `20a6592`）

## 3. user-service 迁移

- [x] 3.1 `git mv` 3 个实现类至 `src/main/java/com/qjj/user/service/service/impl/`：`AuditServiceImpl`、`RbacServiceImpl`、`UserServiceImpl`
- [x] 3.2 各文件 `package` 声明改为 `com.qjj.user.service.service.impl`；按编译结果最小修正 import（补接口 import：Audit/User/Rbac）
- [x] 3.3 核验 `UserApplication` `scanBasePackages` 覆盖新子包（预计零改动），异常则最小修正（`com.qjj.user` 根包扫描，零改动）
- [x] 3.4 `cd backend/user && mvn -q compile` 通过；一次提交（`refactor(user): 实现类迁入service/impl子包` → `794f2f1`）

## 4. blog-service 迁移（须在任务 1.2 协调完成后执行）

- [x] 4.1 `git mv` 3 个实现类至 `src/main/java/com/qjj/blog/service/service/impl/`：`AuditServiceImpl`、`RbacServiceImpl`、`FileServiceImpl`（`FileServiceImpl` 有在途未提交改动：备份 → 还原干净版迁移 → 提交后恢复在途内容并适配包声明）
- [x] 4.2 各文件 `package` 声明改为 `com.qjj.blog.service.service.impl`；按编译结果最小修正 import（补接口 import：Audit/Rbac/File）
- [x] 4.3 核验 `BlogApplication` `scanBasePackages` 覆盖新子包（预计零改动），异常则最小修正（`com.qjj.blog` 根包扫描，零改动）
- [x] 4.4 `cd backend/blog && mvn -q compile` 通过；一次提交（`refactor(blog): 实现类迁入service/impl子包` → `42dcacd`，纯 rename 未夹带在途改动；编译为迁移+在途变更组合态验证）

## 5. 收尾验收

- [x] 5.1 全仓检查：`backend` 下不存在包声明为 `com.qjj.{system}.service.service` 的 `*ServiceImpl.java`；每个 `{system}-service` 的 `service/` 目录仅剩接口（旧包声明 0 个；`service/` 直下无 Impl 残留；11 个实现类全部位于 `service/impl/`）
- [x] 5.2 三系统 `mvn -q compile` 最终复跑全部通过（`CLAUDE.md` §3.2）：auth / user / blog 均 EXIT=0
- [x] 5.3 确认未引入任何未要求的改动（无顺手重构、无依赖变化、无配置漂移）；`design.md` D3 如有核验结论回填（三次提交仅含 11 个文件的 rename + 包声明/import；blog 在途变更的 FileServiceImpl 增量保留在新路径、未提交状态不变；D3 已回填）
