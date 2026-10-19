# Design: service-impl-package-split

## Context

三系统脚手架落地时，`XxxService` 接口与 `XxxServiceImpl` 实现类同包同目录（`com.qjj.{system}.service.service`）。规范（`CLAUDE.md` §5.6.2、`architecture-design.md` §1.4.1/§1.4.2）已修订为接口与实现分离。本变更纯包结构调整，无行为变化。

现状盘点（2026-10-19 排查）：

| 系统 | 实现类（现包 `com.qjj.{system}.service.service`） |
|------|--------------------------------------------------|
| auth | `AuditServiceImpl`、`ClientServiceImpl`、`CredentialServiceImpl`、`GrantServiceImpl`、`RbacServiceImpl` |
| user | `AuditServiceImpl`、`RbacServiceImpl`、`UserServiceImpl` |
| blog | `AuditServiceImpl`、`RbacServiceImpl`、`FileServiceImpl` |

全仓 Java 源码中无任何文件直接 import 实现类；依赖注入一律面向接口。

## Goals / Non-Goals

- Goals：实现类迁入 `service/impl/` 子包；包声明同步；编译通过；与规范一致
- Non-Goals：见 proposal Non-goals；不改任何业务逻辑、注解语义、事务边界

## Decisions

### D1. 目标包结构

```text
com.qjj.{system}.service.service/          # 仅接口 XxxService
com.qjj.{system}.service.service.impl/     # 仅实现类 XxxServiceImpl
```

与 `CLAUDE.md` §5.6.2 包结构树一致；`service/impl` 为不可合并边界（小模块也不得把实现类并回 `service/`）。

### D2. 迁移方式：git mv + 仅改 package 声明

- 目录与文件用 `git mv` 保留历史
- 实现类文件仅修改第一行 `package` 声明；对接口的 `import`/继承（同模块内可直接引用）按编译结果最小修正
- **不**修改 `@Service`、`@Transactional`、`@RequiredArgsConstructor` 等任何注解与成员

### D3. 扫描配置：预计零修改，实施时核验

- `@SpringBootApplication(scanBasePackages = "com.qjj.{system}")`（auth/user/blog 三处均为根包扫描）→ `service.service.impl` 为其子包，组件扫描天然覆盖
- `@MapperScan(basePackages = "com.qjj.{system}.service.mapper")` 与 service 层无关，不受影响
- framework 模块的 `FrameworkConfiguration` 靠应用侧根包扫描注册，不受影响
- 结论：**无扫描配置改动项**；tasks 中保留逐系统核验步骤，若发现任何显式列出 `…service.service` 的配置（yml/注解/Manifold 等），按实际情况最小修正并回填本节
- **核验回填（2026-10-19，任务 1.1）**：全仓 `grep -rn "service.service" backend --include=*.{java,yml,yaml,xml,properties}` 复核完毕——全部命中为①接口/实现类自身的包声明；②Controller/Config/Repository/Security 对**接口**的 import 与全限定引用（接口留在原包，不受迁移影响）。无任何位置按**实现类全限定名**引用，无 yml/xml/properties 字符串引用。迁移仅需改实现类自身的 `package` 声明并补接口 import

### D4. 实施顺序与冲突协调

- 顺序：`auth-service` → `user-service` → `blog-service`（每系统独立提交，编译通过再进下一个）
- `blog-content-domain`（in-progress）涉及 `blog-service` 同包区域：`blog-service` 的迁移须在其归档或与其负责人确认无并行改动后执行
- 单系统内一次提交完成「移动 + 包声明 + 编译自检」，不留中间态

### D5. 后续新增实现类的约定

落地后，新增 Service 一律：接口写 `service/XxxService.java`，实现写 `service/impl/XxxServiceImpl.java`（作为 `backend-code-structure` capability 的验收项，防止回退）。

## Risks / Trade-offs

- 风险：若存在未排查到的字符串引用（如 SpEL、日志配置中的类名）→ 缓解：实施时 `grep -rn "service.service.ImplName"` 全仓复核
- 风险：与 in-progress 变更并行造成冲突 → 缓解：D4 顺序约束
- Trade-off：包层级多一层 `impl`，目录稍深；换来接口/实现边界在文件系统层面可见，符合已修订规范

## Migration Plan

每系统一次提交：`git mv` 11 个文件到位 → 改 `package` 声明 → `mvn -q compile` → 下一系统。无数据迁移、无 API 变化、可独立回滚（revert 对应提交）。

## Open Questions

（无）
