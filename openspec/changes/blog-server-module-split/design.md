# Design: blog-server-module-split

> 对齐 `CLAUDE.md` §5.4 模块关系、§6.10 配置规范；组件接入信息以 `docs/test-env.md` 为准。

## D1. 改造前后模块依赖

### 改造前

```
blog-common ← blog-framework ← blog-service（启动 + 全部业务）
                            ← blog-object-storage ← blog-service
                            ← blog-api ← blog-service ← user-api
blog-common ← blog-gateway（独立启动）
```

`blog-service` 同时承载：启动类、三份 yml、logback、Dockerfile、全部 Controller/Service/Mapper/Entity/DTO/VO。

### 改造后

```
blog-common ← blog-framework ← blog-service（纯业务）← blog-server（启动）
                            ← blog-object-storage ← blog-service
                            ← blog-api ← blog-service ← user-api
blog-common ← blog-gateway（独立启动，不动）
```

`blog-server` 承载：启动类、三份 yml、logback、Dockerfile、spring-boot-maven-plugin、nacos-discovery、mysql runtime。
`blog-service` 承载：Controller/Service/ServiceImpl/Mapper/Entity/DTO/VO/enums/converter/业务 Feign 装配。

## D2. 关键决策

| 决策 | 结论 | 理由 |
|------|------|------|
| 服务名 | 保持 `blog-service`（`spring.application.name`） | Nacos 注册名 + 网关 `lb://blog-service` 路由契约；模块名 ≠ 服务名，不能随拆分而变 |
| 产物 jar 名 | `blog-service.jar`（`<finalName>blog-service</finalName>`） | 兼容 Dockerfile `COPY target/*.jar app.jar` 与历史运维/CI 脚本 |
| 启动类包名 | `com.qjj.blog.server.BlogApplication`（由 `com.qjj.blog.service` 迁入） | 与业务包 `com.qjj.blog.service` 区分；启动模块独立包 |
| 组件扫描 | `@SpringBootApplication(scanBasePackages = "com.qjj.blog")` **不变** | 覆盖 blog.common/framework/service/api/server 全部 @Component/@Configuration/@Controller；framework 装配类包名 `com.qjj.blog.framework.core.*` 天然被扫到 |
| MapperScan | `@MapperScan(basePackages = "com.qjj.blog.service.mapper")` **不变** | 业务 Mapper 仍在 blog-service，启动类迁走不影响扫描目标 |
| Feign 装配 | `@EnableFeignClients(clients = UserQueryClient.class)` **不变** | Feign 契约在 user-api，启动类迁走不影响 |
| nacos-discovery 依赖 | 由 blog-service 上移至 blog-server | 服务注册是启动期行为，归属启动模块；blog-service 不再持启动期依赖 |
| mysql-connector-j 依赖 | 由 blog-service（runtime）上移至 blog-server（runtime） | 数据源驱动是运行期依赖，归属启动模块；blog-service 纯业务不持 runtime 驱动 |
| logback 文件名 | `blog-service.log` 保持不变 | 日志归属服务名，不随模块名变 |

## D3. 风险与缓解

| 风险 | 核验结论 / 缓解 |
|------|----------------|
| Mapper XML 丢失 | `blog-service` 全注解，无 `mapper/*.xml` 文件（已核查）；`mybatis-plus.mapper-locations: classpath*:/mapper/**/*.xml` 配置迁 blog-server 后无 xml 可扫，无影响 |
| `blog.*` 业务配置失效 | `application.yml` 中 `blog.idgen/idempotent/rate-limit/object-storage/feign` 是 framework `@ConfigurationProperties` 绑定；迁 blog-server 后 framework 仍在 classpath（经 blog-service 传递依赖），配置照常生效 |
| framework 装配类未被扫描 | framework 装配类包名 `com.qjj.blog.framework.core.*`，`scanBasePackages = "com.qjj.blog"` 覆盖，已核验 |
| Jenkins/Docker 构建上下文漂移 | Dockerfile 迁 blog-server，构建上下文 `backend/blog/blog-service` → `backend/blog/blog-server`；同步改 `docs/jenkins-pipeline-guide.md` `blog-service-cd` Job 登记模块 |
| IDE 启动入口变更 | 开发者原从 blog-service 启动，改造后须从 blog-server 启动；在 `docs/template/example-system-design.md` 与 `CLAUDE.md` §3.2 启动命令示例中明确 `-pl {system}-server` |
| 业务代码误改 | tasks 严格限定只动 pom + 迁移启动相关文件，业务代码原地不动；编译验证后 grep 复核无业务文件改动 |

## D4. 文档沉淀落点（对齐 proposal「文档沉淀」）

| 文档 | 章节 | 改动 |
|------|------|------|
| `CLAUDE.md` | §4 目录结构 | 目标目录树注释增加 `{system}-server`（启动模块，必选） |
| `CLAUDE.md` | §5.4 模块关系 | 分层图增加 `{system}-server` 层；职责表增加一行；补「启动职责与业务职责分离」原则 |
| `CLAUDE.md` | §3.2 启动命令 | `mvn spring-boot:run -pl <module>` 示例明确为 `-pl {system}-server` |
| `docs/template/example-system-design.md` | §3 模块划分 | 目录树五模块 → 六模块（新增 `example-server`）；模块表新增 `example-server` 行，`example-service` 行调整为纯业务；`example-service` 依赖剥离 nacos/mysql runtime 至 `example-server` |
| `docs/template/example-system-design.md` | §5.3 配置契约 | 标题「（`example-service` / 可运行模块）」改为「（`example-server` / 启动模块）」；三份 yml 归属说明改 `example-server` |
| `docs/blog-system/business-design.md` | §8 架构落点 | 落点代码块补充 `blog-server` 启动模块一行（启动类 + 三份 yml + Dockerfile + 打包） |
| `docs/jenkins-pipeline-guide.md` | Job 清单 / 部署表 | `blog-service-cd` 构建模块由 `blog-service` 切换为 `blog-server`；服务名/端口 19100 不变 |

## D5. 不在本次范围

- `blog-gateway` 拆分（选 A，不动）
- `auth` / `user` 系统启动模块统一（本次只针对 blog）
- `docs/template/base-system/architecture-design.md` 同步（待 auth/user 改造时另行变更）
- `docs/port-registry.md`（登记服务名 + 端口，服务名不变，无需改）
- `docs/knowledge-base-design/`（服务名层面引用 blog-service，不涉及模块结构，无需改）
