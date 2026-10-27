# Tasks: blog-server-module-split

> 依据 `CLAUDE.md` §8：只按本清单实施，完成一项勾一项；不擅自修改 proposal/specs/design。

## 1. 前置核验

- [x] 1.1 确认 `blog-service` 无 Mapper XML：`find backend/blog/blog-service/src -name "*.xml"` 应只返回 `logback-spring.xml`（无 mapper xml）；若发现 xml，需在迁移时一并搬到 blog-server 并核验 `classpath*:` 合并扫描
- [x] 1.2 全仓复核 `BlogApplication` 引用：`grep -rn "BlogApplication" backend/blog`，确认仅 blog-service 自身启动类与 target 编译产物，无其他源码显式引用
- [x] 1.3 确认无在途变更冲突：`blog-service` 业务代码本次原地不动，与 `blog-content-domain` 等已归档变更无冲突

## 2. 新建 blog-server 模块

- [x] 2.1 创建 `backend/blog/blog-server/pom.xml`：parent 指向 `blog-parent`；`packaging=jar`（默认，不显式声明）；依赖 `blog-service` + `spring-cloud-starter-alibaba-nacos-discovery` + `mysql-connector-j`(runtime) + `lombok`(provided)；`spring-boot-maven-plugin` 配 `<finalName>blog-service</finalName>`
- [x] 2.2 创建 `backend/blog/blog-server/src/main/java/com/qjj/blog/server/BlogApplication.java`：package `com.qjj.blog.server`；`@SpringBootApplication(scanBasePackages = "com.qjj.blog")` + `@EnableFeignClients(clients = UserQueryClient.class)` + `@MapperScan(basePackages = "com.qjj.blog.service.mapper")`；main 方法照搬
- [x] 2.3 迁移配置文件：将 `blog-service/src/main/resources/{application.yml,application-local.yml,application-test.yml,logback-spring.xml}` 内容原样写入 `blog-server/src/main/resources/`（不改文件名、不改 `spring.application.name`、不改日志文件名）
- [x] 2.4 迁移 `Dockerfile`：将 `blog-service/Dockerfile` 写入 `blog-server/Dockerfile`，构建上下文注释由 `backend/blog/blog-service` 改为 `backend/blog/blog-server`

## 3. blog-service pom 调整与清理

- [x] 3.1 `blog-service/pom.xml` 移除依赖：`spring-cloud-starter-alibaba-nacos-discovery`、`mysql-connector-j`；保留 blog-common/framework/object-storage/api/user-api/mapstruct/lombok + compiler 插件（annotation processor 配置不动）
- [x] 3.2 删除 `blog-service/src/main/java/com/qjj/blog/service/BlogApplication.java`
- [x] 3.3 删除 `blog-service/src/main/resources/{application.yml,application-local.yml,application-test.yml,logback-spring.xml}`
- [x] 3.4 删除 `blog-service/Dockerfile`
- [x] 3.5 清理 `blog-service/target` 编译产物（旧 BlogApplication.class 等）

## 4. blog-parent 父 pom 调整

- [x] 4.1 `backend/blog/pom.xml` `<modules>` 增加 `<module>blog-server</module>`
- [x] 4.2 `<dependencyManagement>` 增加 `blog-server` 版本声明（`com.qjj:blog-server:${project.version}`）

## 5. 编译验证（CLAUDE.md §3.2）

- [x] 5.1 `cd backend/blog && mvn clean install -DskipTests` 全量通过（EXIT=0）
- [x] 5.2 确认 `blog-server/target/blog-service.jar` 产出（finalName 生效）
- [x] 5.3 确认 `blog-service/target/classes` 不再含 `BlogApplication.class` 与 application-*.yml
- [x] 5.4 grep 复核业务代码无改动：`git diff --stat` 仅含 pom + 新建/删除的启动相关文件

## 6. 文档沉淀

- [x] 6.1 `CLAUDE.md` §4 目录结构：目标树增加 `{system}-server`（启动模块）注释行
- [x] 6.2 `CLAUDE.md` §5.4 模块关系：分层图增加 `{system}-server` 层；职责表增加一行；补「启动职责与业务职责分离」原则一句
- [x] 6.3 `CLAUDE.md` §3.2 启动命令：`mvn spring-boot:run -pl <module>` 示例改为 `-pl {system}-server`
- [x] 6.4 `docs/template/example-system-design.md` §3：目录树五模块 → 六模块（新增 `example-server`）；模块表新增 `example-server` 行，`example-service` 行调整职责与依赖（剥离 nacos/mysql runtime 至 server）
- [x] 6.5 `docs/template/example-system-design.md` §5.3：标题与三份 yml 归属由 `example-service` 改为 `example-server`
- [x] 6.6 `docs/blog-system/business-design.md` §8 架构落点：落点代码块补充 `blog-server` 启动模块一行
- [x] 6.7 `docs/jenkins-pipeline-guide.md`：`blog-service-cd` Job 构建模块由 `blog-service` 切换为 `blog-server`（服务名/端口不变）

## 7. 收尾验收

- [x] 7.1 全仓复核：`blog-service` 下无 `BlogApplication.java`、无 application-*.yml、无 Dockerfile；`blog-server` 下齐全
- [x] 7.2 确认未引入未要求的改动（无业务代码改动、无 gateway/auth/user 改动、无端口/服务名变化）
- [x] 7.3 回填本 tasks.md 全部勾选；记入 `.workbuddy/memory/2026-10-27.md`
