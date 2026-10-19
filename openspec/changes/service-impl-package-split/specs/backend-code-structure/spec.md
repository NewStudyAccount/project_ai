# backend-code-structure · Service 层接口与实现分离

## Purpose

约束三系统（auth / user / blog）及后续新建系统中 Service 层的包结构与目录布局，使接口与实现类在文件系统层面分离，与 `CLAUDE.md` §5.6.2 一致，保证同构可复制。

## ADDED Requirements

### Requirement: Service 接口与实现目录分离
每个 `{system}-service` SHALL 将 Service 接口放在包 `com.qjj.{system}.service.service`（目录 `service/`），实现类放在包 `com.qjj.{system}.service.service.impl`（目录 `service/impl/`）；`service/` 目录下 MUST NOT 存在任何 `*ServiceImpl.java`。该边界为不可合并项：小模块亦 MUST NOT 将实现类并回 `service/` 目录。

#### Scenario: 现存实现类迁移
- **WHEN** 本变更实施完成
- **THEN** auth-service 的 `AuditServiceImpl`/`ClientServiceImpl`/`CredentialServiceImpl`/`GrantServiceImpl`/`RbacServiceImpl`、user-service 的 `AuditServiceImpl`/`RbacServiceImpl`/`UserServiceImpl`、blog-service 的 `AuditServiceImpl`/`RbacServiceImpl`/`FileServiceImpl` 均位于 `service/impl/` 且包声明为 `…service.service.impl`；各 `service/` 目录仅剩接口文件

#### Scenario: 新增 Service
- **WHEN** 任一系统新增业务能力（如 blog 的 `ArticleService` 实现落地）
- **THEN** 接口文件写 `service/XxxService.java`，实现类写 `service/impl/XxxServiceImpl.java`

### Requirement: 迁移零行为变化
实现类迁移 SHALL 不改变任何业务行为：`@Service`/`@Transactional` 等注解、方法签名、事务边界、对外 API 契约（路径、`{code,msg,data}`）MUST 保持不变；每个系统迁移后 `mvn -q compile` MUST 通过。

#### Scenario: 编译与契约校验
- **WHEN** 某系统完成迁移提交
- **THEN** `cd backend/{system} && mvn -q compile` 通过；无 API、依赖、配置文件的契约性改动（扫描配置若确需调整，仅限使其覆盖新子包的最小修正）

### Requirement: 组件扫描覆盖
各应用类 SHALL 保证组件扫描覆盖 `…service.service.impl` 子包。采用根包扫描 `scanBasePackages = "com.qjj.{system}"` 时 MUST NOT 出现实现类不被注册的情况；实施后各系统能正常启动并完成依赖注入。

#### Scenario: 启动注入验证
- **WHEN** 迁移后的服务以 local profile 启动
- **THEN** 各 `XxxServiceImpl` 被正常注册，Controller 经接口注入可用，无 `NoSuchBeanDefinitionException`
