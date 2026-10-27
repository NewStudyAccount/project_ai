# Proposal: blog-server-module-split

## Why

`backend/blog/blog-service` 当前身兼两职：既是**启动入口**（`BlogApplication` + 三份 `application-*.yml` + `logback-spring.xml` + `Dockerfile`），又是**全部业务实现载体**（Article/Category/Tag/File/Audit/Rbac/SiteConfig/StorageProvider/Menu/Role 等全部 Controller/Service/Mapper/Entity）。启动职责与业务代码耦合在同一模块，后续新增业务模块（如 `docs/knowledge-base-design/` 规划的 `knowledge-service` 等独立部署服务）无统一启动范式可循。

需将启动职责上移到独立 `blog-server` 模块，`blog-service` 退化为纯业务模块（`blog-server` 依赖 `blog-service`），建立「启动模块 + 业务模块分离」工程范式，并沉淀到各基础设计文档，供后续新增模块遵循。

## What Changes

- **新建 `blog-server` 模块**（`backend/blog/blog-server/`）
  - `pom.xml`：`packaging=jar`；`spring-boot-maven-plugin`（repackage，`<finalName>blog-service</finalName>` 保持产物 jar 名兼容运维）；依赖 `blog-service`（传递带入 framework/common/object-storage/api/user-api）+ `spring-cloud-starter-alibaba-nacos-discovery` + `mysql-connector-j`(runtime)
  - `BlogApplication.java`：由 `com.qjj.blog.service.BlogApplication` 迁至 `com.qjj.blog.server.BlogApplication`；`@SpringBootApplication(scanBasePackages = "com.qjj.blog")` / `@EnableFeignClients(clients = UserQueryClient.class)` / `@MapperScan(basePackages = "com.qjj.blog.service.mapper")` 三项装配**保持不变**
  - 迁入 `application.yml` / `application-local.yml` / `application-test.yml` / `logback-spring.xml` / `Dockerfile`（内容原样搬迁，`spring.application.name: blog-service` 与日志文件名 `blog-service.log` 保持不变）
- **`blog-service` 退化为纯业务模块**
  - `pom.xml` 移除 `spring-cloud-starter-alibaba-nacos-discovery` 与 `mysql-connector-j`（已上移 `blog-server`）；保留 blog-common/framework/object-storage/api/user-api/mapstruct/lombok + compiler 插件（annotation processor）
  - 删除 `BlogApplication.java`、`application.yml`、`application-local.yml`、`application-test.yml`、`logback-spring.xml`、`Dockerfile`
  - 业务代码（Controller/Service/ServiceImpl/Mapper/Entity/DTO/VO/enums/converter）**原地不动**
- **`blog-parent` 父 pom**：`<modules>` 增加 `blog-server`；`<dependencyManagement>` 增加 `blog-server` 版本声明
- **文档沉淀**（建立「启动模块 + 业务模块分离」范式）：
  - `CLAUDE.md` §4 目录结构（目标树增加 `{system}-server`）、§5.4 模块关系（分层图与职责表增加 `{system}-server` 行）、§3.2 启动命令（`-pl {system}-server`）
  - `docs/template/example-system-design.md` §3 模块划分（五模块 → 六模块，新增 `example-server` 启动模块）、§5.3 配置契约（标题与归属由 `example-service` 改为 `example-server`）
  - `docs/blog-system/business-design.md` §8 架构落点（补充 `blog-server` 启动模块一行）
  - `docs/jenkins-pipeline-guide.md`（`blog-service-cd` Job 构建模块由 `blog-service` 切换为 `blog-server`）

Non-goals（项目边界，对齐 `CLAUDE.md` §1 / §7.8）：

- **不动 `blog-gateway`**：独立部署单元，业务极薄（1 启动类 + 2 config），拆分收益低（已确认选 A）
- **不动 `auth` / `user` 系统**：本次拆分只针对 `backend/blog`（用户明确要求）；`docs/template/base-system/architecture-design.md` 待各系统改造时同步，不在本次范围
- **不改服务名 `blog-service`**：这是 Nacos 注册名 + 网关 `lb://blog-service` 路由契约，模块名 ≠ 服务名
- **不改任何业务代码**：Controller/Service/Mapper/Entity/DTO/VO/enums/converter 原地不动；无接口、字段、错误码变化
- **不动 `blog-common` / `blog-framework` / `blog-object-storage` / `blog-api`** 模块边界与依赖
- **不改端口 19100**、不引入/移除任何业务依赖
- **不做** blog-gateway 同步拆分、不做 auth/user 启动模块统一

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `backend-code-structure`: 新增「启动模块 `{system}-server` 与业务模块 `{domain}-service` 分离」约定——启动类/三份 yml/logback/Dockerfile/spring-boot-maven-plugin/服务注册依赖归属启动模块；业务模块只承载业务代码（Controller/Service/Mapper/Entity/DTO/VO）。契约正文沉淀至 `CLAUDE.md` §4/§5.4，模板范本沉淀至 `docs/template/example-system-design.md` §3/§5.3。

## Impact

- **代码**：新建 `blog-server`（pom + 6 迁入文件）；`blog-service` 删除 6 文件 + pom 移除 2 依赖；`blog-parent` pom 增 2 处。**无业务代码改动**
- **数据**：无表结构变更
- **API**：对外接口路径、出参、`{code,msg,data}` 契约完全不变
- **构建**：Docker 构建上下文 `backend/blog/blog-service` → `backend/blog/blog-server`；`mvn spring-boot:run -pl blog-server`；产物 jar 名仍为 `blog-service.jar`（`finalName` 锁定）
- **CI/CD**：`docs/jenkins-pipeline-guide.md` 登记 `blog-service-cd` Job 构建模块切换为 `blog-server`
- **协调**：`docs/knowledge-base-design/` 规划的 `knowledge-service` 是未来 blog 系统内第 2 个业务服务，本次建立的启动模块范式供其遵循；`blog-content-domain` 等既有变更已归档，无在途冲突
