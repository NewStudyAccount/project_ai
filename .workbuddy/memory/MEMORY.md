# 项目长期约定（project_ai）

## 后端模块结构：启动模块与业务模块分离（2026-10-27 裁决，blog 首落地）

- 每个系统的启动职责与业务职责分离：`{system}-server`（启动模块）依赖 `{domain}-service`（业务模块）
- **启动模块 `{system}-server` 承载**：启动类、三份 `application-*.yml`、`logback-spring.xml`、`Dockerfile`、`spring-boot-maven-plugin`（`finalName`=服务名）、nacos-discovery、mysql runtime
- **业务模块 `{domain}-service` 只承载**：Controller/Service/ServiceImpl/Mapper/Entity/DTO/VO/enums/converter
- **服务名（`spring.application.name`）保持 `{domain}-service` 不变**——模块名 ≠ 服务名，Nacos 注册名 + 网关 `lb://` 路由依赖此名；`finalName` 也锁为服务名以兼容运维 jar 名
- **启动类包名** `com.qjj.{system}.server`；`scanBasePackages="com.qjj.{system}"` + `@MapperScan("com.qjj.{system}.service.mapper")` 保持不变（业务 mapper 仍在 service 模块）
- 已落地：blog（blog-server 依赖 blog-service）；auth/user 待各自改造时对齐（本次不动）
- 模板范本：`docs/template/example-system-design.md` §3；契约正文：`CLAUDE.md` §4/§5.4
- openspec 变更：`openspec/changes/blog-server-module-split/`
