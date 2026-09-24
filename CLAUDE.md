# 项目全局规范

> 本文件是项目级**稳定契约与工程约定**的事实来源，面向所有 AI 编码工具
> （Cursor、Claude Code、Windsurf、Codex、Trae 等）与人类协作者。
> 所有 AI 工具在编写、修改代码前必须先读取并遵循本文件。
>
> **文档分工（避免双源真理）：**
> | 文档 | 职责 | 不要放什么 |
> |---|---|---|
> | `CLAUDE.md`（本文件） | 稳定契约、项目边界、协作红线 | 服务清单、业务词表、实现代码、依赖清单、**生产**密钥 |
> | `AGENTS.md` | 指针文件，仅指向本文件 | 复制规范内容（防双源漂移） |
> | `openspec/` | 单次变更的 proposal / specs / design / tasks | 通用编码教程 |
> | 后端枚举 / Controller / Swagger | 字段级接口契约真相 | 在文档里复述枚举全表 |
> | `pom.xml` / `package.json` | 依赖落地锁定（版本按 `docs/version-baseline.md` 取用） | 在本文件复制依赖清单 |
> | `docs/` | 流程、工具与环境信息（如 Jenkins 流水线、`test-env.md`、`port-registry.md`） | 业务契约、短期任务 |
> | `docs/port-registry.md` | 前后端/中间件**端口**查询与规划（唯一端口登记处） | 账密、生产密钥 |
> | `docs/test-env.md` | 测试环境组件接入与账密真相源（无生产密钥） | 业务契约、生产密钥 |
> | `docs/version-baseline.md` | 组件版本基线（固定登记、不可删除；各系统版本唯一取用处） | 业务契约、生产密钥 |
> | `docs/template/` | 设计/库表等参考模板 | 当作契约真相；覆盖 openspec 制品 |
> | `deploy/` | 部署配置（nginx.conf、compose、Jenkins 共享脚本等） | 业务契约、生产密钥 |
> | 会话 `notes.md` / issue | 未决问题、备忘 | 写入本文件当“规范” |

---

## 1. 项目概述

- 项目名称：`project_ai`（仓库目录名；对外产品名**待确认**后回填，见第 9 节）
- 业务目标：在同一仓库内交付多个**相互独立**的后台系统（各自 Vue3 前端 + Spring Cloud 微服务后端）
  （业务范围以已归档 openspec 变更为准）。
- 目标用户：企业内部运营与管理人员（角色与权限以后端权限模型为准，不在前端硬编码）。
- 项目边界（明确不做，防功能蔓延）：
  - 不做 C 端独立 App / 小程序原生壳（若需要，另开变更）
  - 不把本仓库做成无关工具集合或一次性脚本堆场
  - 不在仓库内存放**生产**密钥/密码/Token、真实生产数据；测试/开发环境账号密码可写入仓库文档（见 6.7 与 `docs/test-env.md`）
  - 未走 SDD（第 8 节）且未写清验收标准的功能，不直接落代码
  - 不引入与第 2 节技术栈冲突的平行框架（如再叠一套 UI 库、ORM、网关）
  - 不预先锁死服务拆分与库表清单；拆分随变更提案确定
  - **暂不做**：Excel 导入导出、分布式定时任务、多语言强制交付（见 2.2）

---

## 2. 技术栈

- 前端：Vue3 + Vue Router + TypeScript + Pinia + Element Plus
- 后端：Java 21 + Spring Boot + MyBatis-Plus + **Spring Cloud Alibaba**（Nacos 注册/配置、OpenFeign 调用）+ Spring Cloud Gateway
- 鉴权组件：**仅网关与认证中心**使用 Spring Security（网关 JWT 资源服务器、auth-service 登录/SAS）；业务服务**不引入** Spring Security（纯网关鉴权，见 5.2）
- 接入层：Nginx（静态资源、反向代理、TLS 终结、负载均衡）
- 数据库：MySQL；连接池 **HikariCP**（Boot 默认，禁止 Druid 等平行选型）
- 缓存与协调：Redis（Spring Data Redis + **Redisson**）
- 服务间调用：Spring Cloud OpenFeign（含 Fallback）
- 网关服务发现路由：`lb://{serviceId}` 时，**网关模块必须显式依赖 Spring Cloud LoadBalancer**（Gateway starter 不保证传递；缺失则注册中心有实例仍 HTTP 503）
- 接口限流：`framework` 统一 `@RateLimit`，底层 **Redisson `RRateLimiter`**（不用 Redis+Lua 自研、不用 Sentinel、不用 Gateway `RequestRateLimiter` 作主路径）
- 写幂等：`framework` 统一 `@Idempotent`，`Idempotent-Key`（或 `X-Request-Id`）+ Redis（**禁止业务唯一键做幂等**）
- 消息队列：**RocketMQ**（异步、重试、死信；客户端与 starter 版本见 `docs/version-baseline.md`；收发与消费重试/死信统一 `framework` 封装，禁止业务直接绑死客户端 API）
- 对象存储：MinIO（S3 兼容）
- 认证中心：**Spring Authorization Server**（OAuth2/OIDC 协议内核，仅认证系统引入）；认证令牌：JWT（实现库 **spring-security-oauth2-jose（Nimbus）**，随 Boot BOM）；令牌时长见 6.7
- 监控：Spring Boot Actuator（健康、存活、指标）；不引入独立监控产品
- 链路追踪：Micrometer Tracing（Brave / OpenTelemetry 桥）；后端 OTLP / Zipkin 兼容
- 构建：前端 Vite / 后端 Maven；包管理 npm
- CI/CD：Jenkins（流水线见 `docs/jenkins-pipeline-guide.md`）
- 统一业务时区：`Asia/Shanghai`（发号日切、审计时间戳均按此）

禁止引入与上表冲突的平行框架。确需替换或新增选型时，先改本节并走变更流程。

### 2.1 系统基础依赖

- 组件版本基线的**唯一取用处**是 `docs/version-baseline.md`（固定登记、不可删除）；各系统父 `pom.xml` 的 `dependencyManagement` 按其锁定落地，禁止各系统各锁各的版本；版本号**不在本文件**复制。
- **落点原则（与 5.4 一致）：** 需连接中间件或额外配置才能工作的依赖（ORM、Redis/Redisson、Feign、JWT、MQ/MinIO 客户端、限流/幂等等），**只**在 `{system}-framework` 封装一次；纯编译期工具（Lombok、MapStruct）可随模块引入。
- `common` **禁止**依赖 MyBatis-Plus、Redis/Redisson、数据源、Actuator、MQ/MinIO 客户端、JWT 库、Hutool。
- **网关模块（`{system}-gateway`）凡使用 `lb://` 路由，必须显式声明 Spring Cloud LoadBalancer**，禁止依赖 Gateway starter 的偶然传递；本地联调若见网关对已注册服务仍 503，优先查该依赖（见 `docs/template/fixbug/2026-09-25-unify-login-sso-gateway.md` §5.1）。
- 禁止同类平行选型、禁止业务自建横切实现；反模式**唯一清单**见 **7.1**，明确不引入的组件**唯一清单**见 **2.2**。

### 2.2 明确不引入（当前）

> 本节是「明确不引入」组件的**唯一清单**；7.1 反模式表只指向本节，不另行维护清单。

| 项 | 约定 |
|----|------|
| **Hutool** 及同类大杂烩工具集 | 禁止（含 hutool-all / hutool-json 等） |
| **Excel**（EasyExcel、POI 等） | **暂不做**；需要时另开变更 |
| **分布式定时**（Quartz、XXL-Job、ShedLock 等） | **不考虑**；清理类需求走运维脚本或后续变更 |
| **独立监控面**（Spring Boot Admin 等） | 监控以 Actuator 为准 |
| **Sentinel 作限流** | 限流用 Redisson `RRateLimiter`；将来若引 Sentinel 仅可为熔断等治理，且须先改本节 |
| **DB 迁移框架**（Flyway、Liquibase） | **暂不引入**；表结构 SQL 随 openspec 变更提供并人工执行；不可逆 migration 须先征询（见 7） |
| **独立熔断组件**（Resilience4j 等） | **暂不引入**；Feign/网关必须配置连接与读取超时（具体值由网关/`framework` 配置固定，不在本文件写死）；需要熔断时再开变更 |
| **多语言强制交付** | 用户可见文案集中管理即可；`en-US` 不作当前门禁 |
| **测试框架栈**（JUnit/Mockito 等） | **不锁定、不作提交门禁**；若将来引入测试再写入变更与本文件 |

---

## 3. 环境配置与启动

### 3.1 开发环境要求

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21+ | 后端 |
| Node.js | 24+ | 前端（统一用 24） |
| npm | 9+ | 前端包管理 |
| Nginx | 1.20+ | 接入层 |
| MySQL | 8.0+ | 数据库 |
| Redis | 6.0+ | 缓存 |
| Nacos | 2.2+ | 注册/配置 |

### 3.2 启动与自检命令

> `frontend/`、`backend/` 多系统容器目录已建，工程内容随系统脚手架落地。下列为**目标约定**；
> 脚手架落地后必须以真实 `package.json` scripts / Maven 模块为准跑通，再允许提交业务代码。

```bash
# 后端
cd backend/<system> && mvn clean install -DskipTests
cd backend/<system> && mvn spring-boot:run -pl <module>

# 前端
cd frontend/<system> && npm install && npm run dev
```

**提交前自检（按改动涉及的系统分别跑；脚手架落地后必须配置并跑通）：**

```bash
cd frontend/<system> && npm run lint && npm run type-check
cd backend/<system> && mvn -q compile
```

- lint / type-check 命令缺失时，先在脚手架变更中补上，不得用“跳过检查”换提交速度。
- **不锁定测试栈、不以测试命令作提交门禁**（见 2.2）；编译与静态检查必须通过。
- 各组件接入信息一律查 `docs/test-env.md`；本文件不写死具体值。
- 端口、网关前缀、环境差异以 `docs/test-env.md` 与 `application.yml` / `application-local.yml` / `application-test.yml` 为准；生产敏感项以 Nacos / 密钥管理为准。

### 3.3 环境变量与本地配置

- **前端**本地环境变量统一通过 `.env`（本地、gitignore）管理，命名 `SERVICE_NAME_ENV_KEY` 大写下划线；禁止把配置散落硬编码在业务逻辑中。
- **后端不使用 `.env`**：配置只经 `application-*.yml` 与 Nacos（见 6.10）。
- 组件接入信息真相源：`docs/test-env.md`（跨协作者共享、入仓）；个人差异写 `docs/test-env.local.md`（gitignore；参考 `docs/test-env.local.example.md`）。禁止把个人差异写进共享文档。
- 测试/开发密码优先写入 6.7 允许的文档或测试配置，不必强依赖 `.env`。
- 敏感配置优先走 Nacos；**生产必须走配置中心或密钥管理**。密钥入仓分级见 **6.7**（唯一权威）。

---

## 4. 目录结构（目标）

`frontend/` 与 `backend/` 是**多系统容器**，其下按系统名分目录，各系统独立、可单独构建与部署。

```
.
├── frontend/                    # 多系统前端容器
│   └── <system>/                # 一个独立前端工程（Vue3 + TS + Pinia + Vite）
│       ├── package.json
│       └── src/
├── backend/                     # 多系统后端容器
│   └── <system>/                # 一个独立后端系统（Maven 多模块）
│       ├── pom.xml              # 父 POM（聚合模块、锁依赖版本）
│       ├── <system>-common/     # 必选：纯基础（见 5.4）
│       ├── <system>-framework/  # 必选：基础设施（见 5.4）
│       ├── <system>-gateway/    # 建议有：边缘治理（见 5.2）
│       ├── <system>-{domain}-api/   # 按需：DTO/VO + Feign 契约（无 Entity）
│       └── <system>-…-service/  # 业务模块（自带本域 Entity）
├── docs/                        # 流程 + 环境信息（test-env.md 为组件账密真相源；无生产密钥）
├── deploy/                      # 部署配置（nginx.conf、compose、Jenkins 共享脚本等）
├── openspec/                    # SDD 制品（见第 8 节）
├── .claude/                     # Claude Code 命令与技能
├── .trae/                       # Trae 等价技能
├── .agents/                     # 通用代理技能镜像（openspec / 迁移命令）
├── .mimocode/                   # MimoCode 配置（instructions 注入本规范）
├── AGENTS.md                    # 指针文件，指向 CLAUDE.md
└── CLAUDE.md                    # 本文件（唯一事实来源）
```

**约定：**

- 新增系统 = 新增 `frontend/<system>/` 与（如需后端）`backend/<system>/`；跨系统禁止直接 `import` 业务代码，只走接口或已说明的薄基础库。
- 系统名小写连字符；新建/删除顶层或系统级目录时同步更新本节。
- Java 包名前缀：**`com.qjj`**（已裁决）；模块内包结构见 5.6.2。
- **脚手架落地顺序（每个系统内）：** `common` → `framework` → 网关与认证 → 前端壳 → 再按 openspec 扩业务。
- **脚手架变更必须确保存在：** `docs/test-env.md`、`docs/jenkins-pipeline-guide.md`（或明确其权威路径）。

---

## 5. 架构设计

### 5.1 目标架构总览

仓库承载**多个相互独立的系统**；每个系统一条完整链路，互不嵌套。

```
浏览器/客户端
  ┌──────────────────────────────────────────────────────────┐
  │                    多系统容器（同一仓库）                    │
  │            frontend/<system> × N · backend/<system> × N    │
  └──────────────────────────────────────────────────────────┘
                               │
       每个系统逻辑链路（组件可独立部署，禁止跨系统直连）：
                               │
┌──────────────┐   HTTPS    ┌──────────────┐   反向代理   ┌──────────────────┐
│  前端 Vue3    │ ─────────► │    Nginx      │ ──────────► │  网关 Gateway     │
│  （管理端）   │  静态/入口  │  接入层        │             │  边缘治理层       │
└──────────────┘            └──────────────┘             └────────┬─────────┘
                                                                 │ 内网调用
                                                      ┌──────────▼───────────┐
                                                      │  业务服务集群         │
                                                      │  按业务域微服务拆分    │
                                                      │  依赖 framework+common│
                                                      └──────────┬───────────┘
                                                                 │
                                              ┌──────────────────┼──────────────────┐
                                              ▼                  ▼                  ▼
                                          MySQL（分库）        Redis              Nacos
                                          每服务数据自治        缓存/会话/幂等/限流   注册与配置
```

**硬约束：**

- 前端只访问**本系统对外入口（Nginx）**，由 Nginx 反代到本网关；禁止直连业务服务。
- 禁止把 A 系统的页面/服务/库表嵌进 B 系统；跨系统只走显式接口（且须走变更）。
- 服务按业务域拆分：单一职责、高内聚、低耦合、独立部署、数据自治。
- 服务命名：`{domain}-service`；**具体服务列表随变更确定，不在此预定。**
- 服务间只通过 API（Feign）通信，禁止跨服务读库、共享 Entity（见 5.6.1）。
- 跨服务一致性优先最终一致（消息/补偿），禁止随意引入强一致分布式事务。

### 5.2 接入层 Nginx 与网关职责边界

**分层原则：** Nginx 做**接入层**（流量入口与静态），网关做**边缘治理**（应用层路由与安全策略）。
二者不抢职责：Nginx 不写业务/鉴权规则，网关不管 TLS 与静态资源。

| 能力 | Nginx（接入层） | 网关 Gateway（边缘治理） | 业务服务 |
|------|-----------------|--------------------------|----------|
| 静态资源 | **托管**前端构建产物、缓存与 gzip | 不处理 | 不处理 |
| TLS / 证书 | **终结 HTTPS**、HTTP→HTTPS | 内网 HTTP 或透传 | — |
| 负载均衡 | 对网关实例轮询/权重 | 对下游路由与灰度 | — |
| 反向代理 | `/api` 等反代到网关；可按系统/域名分流 | 路径/断言转到具体微服务 | — |
| 路由 | 域名/前缀级分流 | **应用级**路由到服务 | — |
| 鉴权 | 仅粗防护（IP 黑名单、基础限速） | **鉴权唯一归属**：JWT/会话认证、登录态拦截、路由级放行/拒绝、身份注入 | **不做**用户鉴权/`@PreAuthorize`；只信网关注入的 `X-User-*`（`/internal/**` 靠网络隔离） |
| 限流 | 连接级/基础限速 | **不**作限流主路径；可保留超时（熔断暂不引入，见 2.2） | **限流主路径**：`@RateLimit` + Redisson |
| 日志 | 访问日志、错误日志 | 应用访问日志、TraceId 透传 | 业务操作/领域审计 |
| 其他 | 路径改写 | **CORS 策略**（唯一归属）、用户身份注入/透传 | 参数校验、事务、业务规则 |

**操作审计：** 关键写操作须记录操作审计（操作人 / 时间 / 动作 / 结果）；载体（审计表或日志）与留存要求随相关变更裁决。

**跨域（CORS）：** 只在**网关**配置，具体信任域名在 `docs/test-env.md` 或变更中登记（见 6.7）；Nginx 不写跨域响应头。

**身份透传 Header（契约）：**

| Header | 含义 | 谁写入 |
|--------|------|--------|
| `X-User-Id` | 用户主键（字符串化 Long） | 网关认证后注入 |
| `X-User-Name` | 登录名/显示名 | 网关 |
| `Idempotent-Key` 或 `X-Request-Id` | 幂等/请求标识 | 客户端；网关可补 `X-Request-Id` |
| `traceparent`（或 B3） | 分布式追踪 | framework/代理自动；禁止业务手拼 |

业务服务**不得**信任可伪造的客户端 `X-User-Id`；服务内以网关注入（覆盖写入）的值为准。

**部署约定：**

- 生产/测试：浏览器 → Nginx（静态 + 反代 `/api`）→ 网关 → 服务。
- 本地开发可由 Vite 代理直打网关，**不得**因此认为生产可绕过 Nginx。
- 多系统：优先「一域名多前缀」或「多子域名」在 Nginx 分流。
- Nginx 配置纳入版本库（`deploy/nginx/` 或各系统 `deploy/`）。

**纯网关鉴权模型（已裁决）：**

1. 网关：**唯一鉴权点**——认证（JWT/会话）、登录态与路由级放行/拒绝、身份注入 `X-User-*`。
2. 业务服务：**不做**登录/验签/`@PreAuthorize` 等用户鉴权；以网关注入的 `X-User-Id` / `X-User-Name` 为身份来源（覆盖客户端同名头）。
3. `/internal/**`：仅服务间可达，不进网关、无用户登录态，靠网络隔离与部署边界防护。
4. **例外：认证中心 `auth-service`** 自身是登录与 OIDC 发行方（单体、无网关），保留自管登录与管理端 RBAC，不套用本条到其它业务系统。

**权限标识格式：** `system:resource:action`（全小写，如 `order:order:list`）；
前端路由/按钮与后端下发的 permission 使用同一字符串；**业务服务不再用 `@PreAuthorize` 拦接口**；禁止前端硬编码角色名。

**权限模型（已裁决）：** 权限模型采用 **RBAC**（用户 → 角色 → 菜单/按钮权限）；
**菜单与动态路由由后端下发，前端动态生成**；数据权限（数据范围）语义随首个权限相关变更提案锁定。

### 5.3 请求链路（一次同步调用）

```
浏览器/客户端
  → Nginx：TLS、静态资源、反代 /api → 网关、写接入访问日志
  → 网关：鉴权（唯一归属）、写应用访问日志、注入 X-User-* 与 TraceId（限流在业务服务侧）
  → 业务服务：MDC 输出 traceId（见 6.11.1）
  → Controller：参数校验（DTO + Bean Validation）；关键写可 @Idempotent
  → Service：业务规则、事务边界；必要处 @RateLimit
  → Mapper/DB 或 Feign 下游（Feign 必须在事务外）
  → 统一 Result{code,msg,data} + 全局异常处理（common）
  → 网关/前端：按 code / HTTP 状态分流
```

**HTTP 与返回码分流（契约）：**

| 场景 | HTTP | `code` 语义 | 前端 |
|------|------|-------------|------|
| 未登录 / Token 无效 | 401 | 系统段（`1xxxx`）「未认证」 | 跳转登录 |
| 无权限 | 403 | 系统段「无权限」 | 提示 |
| 业务失败 / 校验失败 | 200 | `2xxxxx` 业务 / `3xxxx` 校验 | 展示 `msg` |
| 限流 | 429 | 系统段「请求过于频繁」 | 稍后重试 |
| 服务端错误 | 5xx | 系统段「系统异常」 | 通用错误；不暴露堆栈 |

- **业务失败与校验失败统一 HTTP 200**，语义由 `code` 表达（前端只按 `code` 分流，避免双通道）。
- 同步链路短；耗时/可重试工作走异步（MQ/线程池），必须有重试与死信。
- 失败语义：网络/5xx 可重试；4xx 不重试；关键写幂等（见 6.11）。

### 5.4 系统内部模块关系（后端）

**模块分层原则：**
- **`common` 只放「零额外配置」的纯基础**（类型/契约/工具/注解）。
- **凡是需要额外配置或中间件才能工作的一律放 `framework`**。
- `common` **禁止**依赖 MyBatis-Plus、Redis、数据源等；`framework` 可依赖 `common`。
- **实体（Entity）只属于拥有该表的 `{domain}-service`**；跨服务只走 `{domain}-api`，**禁止**共享 Entity。

```
        ┌──────────────────────────────────────────┐
        │              {system}-common              │
        │  Result/错误码/异常/分页契约/通用枚举/     │
        │  常量/工具/注解（零额外配置）              │
        └───────────────────┬──────────────────────┘
                            │ 被依赖
        ┌───────────────────▼──────────────────────┐
        │            {system}-framework             │
        │  MyBatis-Plus/Redis/Redisson/OpenFeign 装配│
        │  Swagger/ID与审计/Long序列化/@RateLimit/   │
        │  @Idempotent/JWT/MinIO/MQ客户端/配置绑定   │
        └───────────────────┬──────────────────────┘
                            │ 被依赖
        ┌───────────────────┼──────────────────────┐
        ▼                   ▼                      ▼
 {system}-gateway    {domain}-service        {domain}-service
 （可仅依赖 common）  （自带 Entity）           （自带 Entity）
                            │
                            ▼
                     {domain}-api（DTO/VO + Feign）
```

| 模块 | 必选 | 职责 |
|------|------|------|
| `{system}-common` | **必须** | 返回体、错误码、异常、分页契约、通用枚举/常量/工具/注解 |
| `{system}-framework` | **必须** | 需配置组件统一装配与横切实现（见 2.1 / 6.4 / 6.11） |
| `{system}-gateway` | 建议有 | 仅 5.2 边缘治理 |
| `{system}-{domain}-service` | 按需 | 本域 Entity/Mapper；业务规则 |
| `{system}-{domain}-api` | 按需 | DTO/VO、Feign、Fallback、常量；**无** Entity/Mapper/实现 |

**common 边界：** 打开即用的纯逻辑/类型。  
**framework 边界：** 与业务无关的封装；业务规则禁止下沉；变更须走变更流程。  
**实体归属：** 见 **5.6**。

### 5.5 前端结构约定

```
src/
├── api/          # 按业务模块分包 + 统一 axios 封装
├── views/        # 页面（按业务模块）
├── components/   # layout / common / business
├── stores/       # Pinia（按模块）
├── router/
├── types/        # api / model / common
├── utils/
├── styles/
└── locales/      # 文案资源（多语言不强制）
```

| 原则 | 说明 |
|------|------|
| 单一职责 | 复杂组件拆子组件 |
| Props 向下 / Events 向上 | 禁止直接改子组件状态 |
| 组合式优先 | `<script setup lang="ts">`，禁止 Options API |
| 状态 | 跨组件共享一律 Pinia |
| 请求 | 业务组件只调 `src/api`，不直接 axios |
| UI | 统一 Element Plus；样式 scoped |
| 路由 meta | `title` / `icon` / `hidden` / `requiresAuth`；name 用 PascalCase |
| 权限 | 使用 5.2 权限标识；菜单/动态路由后端下发、前端动态生成（见 5.2）；禁止硬编码角色 |

### 5.6 后端模块与包结构

#### 5.6.1 实体（Entity）归属

| 场景 | 实体放哪 | 依赖方式 |
|------|----------|----------|
| **默认** | `{domain}-service` 的 `entity/` | 其他服务只依赖 `{domain}-api` |
| 同库、同限界、多模块共用表 | 可选 `{domain}-dao` | 仅限本限界；依赖 `framework` |
| 跨服务只读展示 | **不共享 Entity** | 薄投影表或 Feign 查 DTO |

**硬规则：** Entity 不进 `common`/`framework`；禁止跨服务 import Entity/Mapper；`{domain}-api` 无 Entity/Mapper/Service 实现。

#### 5.6.2 业务模块内包结构

```
src/main/java/com.qjj.{system}.{module}/
├── controller/
├── dto/
├── vo/
├── service/      # XxxService + XxxServiceImpl
├── bo/
├── converter/    # MapStruct 只放这里
├── mapper/
├── entity/
├── feign/
├── config/
├── constants/
├── enums/
└── util/
```

- 小模块可将 `dto/` `vo/` 并入 `controller/dto`、`controller/vo`；**`entity` / `converter` / `bo` 边界不变**。
- 调用链：`Controller → Service → Mapper`；**禁止** Controller 直调 Mapper。
- 转换：MapStruct 放 `converter/`（**唯一**转换落点；不设第二套「集中转换层」）。
- Service 接口 + 实现分离；业务规则只在 Service。
- **Feign：** 接口与 Fallback 优先在 `{domain}-api`；必须 Fallback；必须配置连接/读取超时（具体值由 `framework`/网关配置固定）。

| 对象 | 职责 | 落点 |
|------|------|------|
| `Entity` | 表映射 | 拥有表的 service/dao；禁止出参 |
| `DTO` | 入参 | `dto/` 或 `{domain}-api` |
| `VO` | 出参 | `vo/` 或 `{domain}-api` |
| `BO` | 服务内部对象 | `bo/`；不对外 |

同一概念禁止 DTO/VO/BO/Entity 字段名多套。

---

## 6. 开发规范

### 6.1 编码与命名

- 前端：变量/函数 `camelCase`，组件 `PascalCase`，常量 `UPPER_SNAKE`。
- 后端：类 `PascalCase`，方法/变量 `camelCase`，常量 `UPPER_SNAKE`。
- 注释与提交说明使用中文。
- 禁止吞异常；统一走 6.4 全局异常处理。
- 前端 Prettier + ESLint；后端遵循 Spring 官方代码风格。

### 6.2 接口与数据模型

- 契约来源：后端真实 Controller 与 OpenAPI；前端按真实字段取值。
- 枚举/字段以后端枚举与表结构为准，禁止前端自造枚举值。
- RESTful：URL 小写 + 连字符；资源名词；GET 查 / POST 增 / PUT 改 / DELETE 删。
- 路径版本前缀 `/api/v1/...`；破坏性变更才升版本。
- 方法名：`getXxx / listXxx / createXxx / updateXxx / deleteXxx`。

### 6.3 提交规范

- 格式：`type(scope): subject`，type ∈ `feat / fix / refactor / docs / chore / test / perf / ci / style`。
- subject 中文祈使句、≤ 50 字符、不写句号。
- 单人开发直接提交 `master`（本仓库主干）；较大功能可开 `feature-*` 分支。
- 一个提交只做一件事。

提交前自查：

- [ ] 编译通过，lint / type-check 通过
- [ ] 无 `console.log` / `System.out.println` 调试残留
- [ ] 无**生产**密钥泄露；测试/开发密码仅在允许位置
- [ ] 符合本文件规范
- [ ] 改动范围与缺陷/已批准变更一一对应，无顺手重构或扩大范围（见 7.8）
- [ ] 若为修 bug：已基于具体报错信息定位根因并针对性修改（见 7.9）

示例：`feat(user): 新增用户分页查询接口` / `fix(order): 修复订单状态未回滚问题`

### 6.4 基础代码契约

> 约定「必须长成什么样」，不附实现。脚手架**第一批**落入 `common` / `framework`。

**落点规则：** 纯契约/类型/工具 → `common`；需配置或中间件 → `framework`。

#### 6.4.1 统一返回体

- 所有接口统一 `{ code, msg, data }`（结构在 `common`）。
- 分页统一 `{ records, total, size, current }`；与 MP `Page` 互转在 `framework`。
- 前端 axios 拦截器统一解包。

#### 6.4.2 错误码

| 分段 | 含义 | 约定 |
|------|------|------|
| `1xxxx` | 系统异常 | **5 位定长**；语义由各 `{system}-common` **对齐维护同一套码值**（未认证、无权限、限流、系统异常等） |
| `2xxxxx` | 业务异常 | **6 位定长**：`2` + 系统号（2 位）+ 业务序号（3 位），如 `201001`；系统号必须先在 `docs/error-code-ranges.md` 分配登记；禁止跨系统同码不同义 |
| `3xxxx` | 参数校验 | **5 位定长**；由 `common` + 全局异常统一；字段细节放 `data` |

- 系统段 `1xxxx` 与校验段 `3xxxx` 的固定码值以 `docs/error-code-ranges.md` 为**唯一登记处**，各系统原样对齐。
- `code` 类型为 `number`；错误码与文案用枚举/常量维护，禁止硬编码魔法字符串。
- 前端只按 `code` 具体值分流，不自行解析号段含义。
- 新增业务码先在本系统序号内分配，再进 `XxxErrorCodeEnum`。

#### 6.4.3 全局异常处理

- `@RestControllerAdvice` 分类处理业务 / 校验 / 系统异常（类型在 `common`）。
- 禁止吞异常；禁止向前端返回堆栈；系统异常友好提示并记日志（带 traceId）。
- 前端按 5.3 表分流。

#### 6.4.4 数据校验

- 入参 Bean Validation（`@Valid`），禁止 Controller/Service 手写重复 if。
- 校验失败走全局异常处理（`3xxxx`）。

#### 6.4.5 实体与审计字段

所有实体必须包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| `createTime` | `LocalDateTime` | 创建时间 |
| `updateTime` | `LocalDateTime` | 更新时间 |
| `createBy` | `Long` | 创建人（序列化为 String，见 6.4.7） |
| `updateBy` | `Long` | 更新人 |
| `deleted` | `Integer` | 0 未删 / 1 已删 |

- `@TableLogic`、`MetaObjectHandler` 等装配在 `framework`；禁止业务手写审计/逻辑删除赋值。
- 主键 `id`：`Long` / `BIGINT`，**统一由 `framework` 发号**；禁止数据库自增、禁止业务雪花/UUID 主键（对象存储文件名可用 UUID）。
- 发号构成（已裁决）：**16 位定长** = 业务日期 `yyyyMMdd`（8 位，`Asia/Shanghai` 业务日）+ 当日序列（8 位，按业务日日切）。
- 并发安全等实现细节由 `framework` 实现并在变更中登记，不在本文件展开。

#### 6.4.6 分页入参

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `current` | `long` | 1 | 页码从 1 起 |
| `size` | `long` | 10 | 每页条数 |

`orderBy` / `order` 必须白名单，禁止拼进 SQL。

#### 6.4.7 Long 序列化

- 出参 `Long`（含 `id`）一律序列化为 `String`。
- 全局 `ObjectMapper` 在 `framework`；禁止字段上散落 `@JsonSerialize`。
- 前端对应类型 `string`；入参传字符串。

### 6.5 数据库规范

- 表名、字段名 `snake_case`，表名单数；禁止物理外键。
- 业务表含 6.4.5 审计字段。
- 金额 `DECIMAL`、状态 `TINYINT`、时间 `DATETIME`、文本合理 `VARCHAR`。
- 字符串默认 `NOT NULL` + 默认值；逻辑删除默认 `0`。
- 索引：`idx_表名_字段` / `uk_表名_字段`；单表索引 ≤ 5。
- **`uk_` 只用于数据完整性/查询去重，禁止用作写幂等**（幂等见 6.11）。
- 查询禁止 `SELECT *`。
- 表结构变更：SQL 随 openspec 变更提供；**暂不引入** Flyway/Liquibase（见 2.2）；生产执行前必须征询（见第 7 节）。

### 6.6 事务规范

- `@Transactional` 只加在 Service。
- 默认 `RuntimeException` 回滚；需要时显式 `rollbackFor`。
- **禁止在事务内调 Feign/HTTP**。
- 只读：`@Transactional(readOnly = true)`。

### 6.7 安全规范

- SQL 参数化（`#{}`）；动态表名/字段白名单。
- 前端用户输入用 `v-text`；非明确需要禁用 `v-html`。
- 密码 BCrypt/Argon2；手机号/身份证等脱敏。
- **密钥入仓分级（唯一权威）：**
  - **禁止入仓**：生产密码、私钥、Token、连接串 → 只进 Nacos / 密钥管理 / Jenkins Credentials。
  - **允许入仓**：开发/测试账号密码 → `docs/test-env.md`、`application-local.yml` / `application-test.yml`；须标注环境，禁止与生产同账号。
  - 生产密钥误入仓：立即改密、清理历史（需征询），并记为严重问题。
- Token：JWT（实现库 **spring-security-oauth2-jose / Nimbus**），Access **默认 10 分钟（5–15 分钟可配）**，Refresh 7 天（轮转）；**纯网关鉴权**（业务服务不重复验签/登录，见 5.2）；签发由认证中心（Spring Authorization Server）负责；网关验签与解析统一 `framework` 封装，禁止业务自写 JWT 解析。
- 登录失败限制；敏感接口二次验证（敏感操作清单与二次验证方式随相关变更裁决）；关键接口限流见 6.11；CORS 仅信任域名（**配置在网关**，见 5.2；具体域名在 `docs/test-env.md` 或变更中登记）。

### 6.8 API 文档

- OpenAPI 注解（`@Operation` / `@Parameter` / `@Schema`）；配置只在 `framework`。
- 文档随代码，禁止另手维护接口文档文件。

### 6.9 性能规范

- 普通查询 ≤ 200ms；复杂查询/报表 ≤ 1s。
- 缓存 key：`{服务名}:{模块}:{业务标识}`；必须 TTL；更新优先「更新 DB 再删缓存」。
- 批量 batch；深度分页用游标或覆盖索引。
- 耗时异步（MQ/线程池）+ 重试与死信；客户端与策略经 `framework`；**禁止业务自建线程池**（含 `Executors` 裸用），统一由 `framework` 装配。

### 6.10 配置规范

可运行模块必须且仅有：

| 文件 | profile | 用途 |
|------|---------|------|
| `application.yml` | 公共 | 无主机/账号密码 |
| `application-local.yml` | `local` | 本地开发 |
| `application-test.yml` | `test` | 测试环境 |

- 连接信息真相源：`docs/test-env.md`；与 local/test 配置一致。
- 本地默认 `local`；联调切 `test`。
- 配置项 `kebab-case` + `@ConfigurationProperties`，禁止散落 `@Value`。
- 生产敏感项只走 Nacos/密钥管理。
- Jenkins 按环境发布；Jenkinsfile 禁止生产密钥（用 Credentials）。

### 6.11 横切能力：幂等、限流、上传、重试

**写幂等（强制关键写）：**

- 客户端携带 `Idempotent-Key`（无则 `X-Request-Id`）。
- `framework` 提供 `@Idempotent`（AOP）：Redis `SET NX`（或等价）占位成功才执行业务；占位时写入首次 `Result`。
- TTL 默认值与注解参数名由 `framework` 固定后写入变更；业务可经注解参数覆盖 TTL。
- 重复请求：返回**首次 `Result`**；禁止裸抛异常、禁止回笼统失败码。
- **禁止**业务自写 setnx、**禁止**仅靠前端防重、**禁止**用 `uk_` 做幂等。

**接口限流（关键接口）：**

- `@RateLimit`（维度：用户/IP/接口等；参数由 `framework` 固定）。
- 底层 **Redisson `RRateLimiter`**（明确不用 Redis+Lua 自研）。
- 超限：HTTP 429 + 系统错误码「请求过于频繁」。
- **禁止** Sentinel、Gateway `RequestRateLimiter` 作限流主路径。

**上传：**

- 单文件 ≤ 10MB，批量 ≤ 50MB；类型白名单；MinIO/S3；文件名 UUID；文件逻辑删除。
- 清理不建定时任务（见 2.2）。

**重试：**

- 指数退避最多 3 次（间隔由 `framework` 固定）；超时与 5xx 可重试，4xx 不重试；必须打日志。
- MQ（RocketMQ）消费重试与死信同节奏，由 `framework` 统一。

**依赖工程：**

- 锁定 `package-lock.json` / `pom.xml` 版本。
- 定期 `npm audit` / `mvn dependency-check`，高危必修。
- Actuator 监控见 2 与 6.11.1。

### 6.11.1 日志与追踪

**选型：** SLF4J + Logback；**Micrometer Tracing**；**禁止 Sleuth**。  
TraceId/SpanId 进 MDC；传播头由 `framework` 配置；禁止业务手拼 Header。

**建议日志模式（须含 traceId）：**

```text
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{traceId:-}] [%X{spanId:-}] %logger{40} - %msg%n
```

| 约定 | 要求 |
|------|------|
| TraceId | 每条应用日志可关联 |
| 级别 | `error` 仅故障；业务失败 `warn`；禁止滥用 error |
| 敏感信息 | 禁密码/完整 Token/身份证银行卡；手机号脱敏 |
| 调试残留 | 提交前清干净 |
| 文件 | Logback 按天滚动；禁止业务旁路写独立日志 |
| 跨线程 / MQ | 透传或记录 traceId |
| 检索 | 按 traceId 与 OTLP/Zipkin 对照（平台不限，格式必须符合） |

### 6.12 测试（当前不锁定栈）

- **不锁定测试框架、不作提交门禁**（见 2.2）。
- 若变更引入测试：数据自包含、禁止无断言空测试；框架与命令写入变更并回填本节。
- 缺陷修复建议补充回归用例，但不以测试栈为前置条件。

### 6.13 枚举与常量

- 枚举实现 `BaseEnum<T>`（`code` + `desc`）；类名 `XxxEnum`。
- 常量在 `constants`，类名 `XxxConstants`；禁止魔法值。
- 库里存 `code`，界面显示 `desc`；接口只传 `code`。

### 6.14 文案

- 用户可见文案集中（常量或 i18n key），禁止散落魔法字符串。
- 多语言（`en-US` 等）**当前不强制**；需要时再开变更引入 `vue-i18n` / `MessageSource`。

---

## 7. 协作红线（AI 工具必须遵守）

1. 不可逆操作（数据库写、migration、seed、改写 git 历史、改依赖锁文件）必须先征询。
2. 不擅自修改 `openspec/` 制品定义（proposal / specs / design）；`tasks` 只允许勾选状态位。
3. 不添加未被要求的功能、抽象或重构。
4. 复用接口/枚举/字段前，先确认真实契约，不凭推导实现。
5. 不把**生产**密钥写入任何将提交的文件；测试/开发密码仅按 6.7。
6. 文档与代码不一致时：以代码与契约为准改文档，或停下来指出冲突。
7. 不在本文件写入服务清单、库表明细、完整实现代码或短期任务。
8. **不可随意修改代码（硬性）**：改动前必须明确目标与影响面；禁止顺手优化、无关重构、扩大改动范围、未要求的“清理/美化”。只改与当前缺陷或已批准变更直接相关的最小代码集；拿不准时停下来征询，不得擅自替业务做裁决。
9. **修 Bug 硬性要求**：必须先取得并分析**具体报错信息**（完整堆栈/错误消息、HTTP 状态与响应 `code`/`msg`、相关日志与 traceId、复现步骤），定位到确切根因后，才做**针对性**修改。禁止未看清报错就改代码、禁止凭猜测批量“试错式”修改、禁止只掩盖表象而不修根因（见 7.1）。

### 7.1 常见反模式（禁止）

| 反模式 | 正确做法 |
|--------|----------|
| 前端 `number` 接主键 `id` | 一律 `string` |
| `Controller` 注入 `Mapper` | 只走 `Service` |
| 事务内调 Feign | 远程调用放事务外 |
| `${}` 拼 SQL | 只用 `#{}` |
| 吞异常 / 空测试凑数 | 显式失败并修根因 |
| 未分析具体报错就改代码 / 凭猜测批量试错修 bug | 先取得堆栈·错误消息·HTTP/`code`·日志并定位根因，再针对性修改 |
| 修 bug 顺手重构、扩大改动或掩盖表象 | 最小范围修根因；改动与缺陷一一对应 |
| 预写未提案的服务名与表结构 | 先 SDD |
| 假设仓库只有一个前后端工程 | 多系统并存 |
| 业务模块自建返回体/异常/分页/ORM·Redis 配置 | `common` 契约 + `framework` 装配 |
| 把需配置组件塞进 `common` | `common` 只放零配置纯基础 |
| Entity 放错模块或跨服务共享 | Entity 只在拥有表的服务 |
| 只有单一 `application.yml` | 三份配置 |
| 业务规则塞进网关/Nginx | 见 5.2 职责表 |
| 业务服务自建登录/`@PreAuthorize` 用户鉴权 | 纯网关鉴权（5.2）；身份只用 `X-User-*` |
| 生产静态由后端或网关托管 | 静态走 Nginx |
| 网关或 Sentinel 作限流主路径 / Redis+Lua 自研限流 | `@RateLimit` + Redisson `RRateLimiter` |
| 幂等靠前端、自写 setnx、或 `uk_` | `@Idempotent` + Redis `Idempotent-Key` |
| 引入 2.2 明确不引入的组件，或违反第 2 节技术栈的平行选型（如 Druid、Jedis） | 遵 2.2 与第 2 节；确需引入/替换先改对应章节再落码 |
| 自建 JWT 解析、自建 RedisTemplate | 统一 `framework` |
| 业务自建线程池 / `Executors` 裸用 | 统一 `framework` 装配 |
| 错误码跨系统撞号 | 按 `docs/error-code-ranges.md` 登记系统号 |
| 业务自建雪花/UUID 主键 | 统一 `framework` 发号 |
| 网关 `lb://` 路由未显式引 LoadBalancer（Nacos 有实例仍 503） | `pom` 显式 `spring-cloud-starter-loadbalancer`（§2 / §2.1） |
| 流水线跳过检查/吞错过门 | 质量门失败即失败 |
| 本文件粘贴版本号、端口、生产密钥、长代码 | 见文末维护约定 |

---

## 8. 规范驱动开发（SDD）

本项目采用 OpenSpec（schema：`spec-driven`）。
入口：`.claude/commands/opsx/`（Trae 侧：`.trae/skills/openspec-*`）。

- 制品权威性：`proposal / specs / design / tasks` 为变更指令源。
- 工作流：`/opsx:explore` → `/opsx:propose <change>` → `/opsx:apply <change>` → `/opsx:archive <change>`。
- 状态：`openspec list --json` / `openspec status --change "<name>" --json`。
- 铁律：代码实施**只按 `tasks` 清单**进行，完成后勾选对应状态位；**不擅自修改** `proposal / specs / design` 定义；未归档前以该 change 制品为唯一指令源。
- 阅读顺序：本文件 → 当前 change 制品 → 代码 → 枚举与 Controller。

---

## 9. 待裁决事项

> 悬空占位的**唯一清单**。裁决后：回填对应章节/代码/openspec 制品，并删除本表对应行。
> AI 工具遇到相关实现点**停下来征询**，不得擅自替这些事项做终裁。

| 事项 | 现状 | 裁决后落点 |
|------|------|-----------|
| 对外产品名 | 待确认 | 第 1 节 |

---

> 维护约定：本文件变更随对应约定调整一并提交。
> 只保留稳定契约；服务名、端口、依赖版本、参数默认值、迭代任务、完整实现一律不写入。
