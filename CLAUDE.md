# 项目全局规范

> 本文件是项目级**稳定契约与工程约定**的事实来源，面向所有 AI 编码工具
> （Cursor、Claude Code、Windsurf、Codex、Trae 等）与人类协作者。
> 所有 AI 工具在编写、修改代码前必须先读取并遵循本文件。
>
> **文档分工（避免双源真理）：**
> | 文档 | 职责 | 不要放什么 |
> |---|---|---|
> | `CLAUDE.md`（本文件） | 稳定契约、项目边界、协作红线 | 服务清单、业务词表、实现代码、**生产**密钥 |
> | `openspec/` | 单次变更的 proposal / specs / design / tasks | 通用编码教程 |
> | 后端枚举 / Controller / Swagger | 字段级接口契约真相 | 在文档里复述枚举全表 |
> | `pom.xml` / `package.json` | 依赖真相 | 在本文件复制依赖清单 |
> | `docs/` | 流程与工具指导（如 Jenkins 流水线） | 业务契约、短期任务 |
> | 会话 `notes.md` / issue | 未决问题、备忘 | 写入本文件当“规范” |

**阶段说明：** 当前仓库为空项目，本文件只固化「项目规范 + 代码规范」。
具体服务名、表名清单、接口实现、业务术语，待脚手架与业务变更落地后，
写入 `openspec/` 制品或代码，**不**提前写进本文件。

---

## 1. 项目概述

- 项目名称：`project_ai`（仓库目录名；对外产品名**待确认**后回填）
- 业务目标：在同一仓库内交付多个**相互独立**的后台系统（各自 Vue3 前端 + Spring Cloud 微服务后端）
  （业务范围以已归档 openspec 变更为准）。
- 目标用户：企业内部运营与管理人员（角色与权限以后端权限模型为准，不在前端硬编码）。
- 项目边界（明确不做，防功能蔓延）：
  - 不做 C 端独立 App / 小程序原生壳（若需要，另开变更）
  - 不把本仓库做成无关工具集合或一次性脚本堆场
  - 不在仓库内存放**生产**密钥/密码/Token、真实生产数据；测试/开发环境账号密码可写入仓库文档（见 6.7 与 `docs/test-env.md`）
  - 未走 SDD（第 8 节）且未写清验收标准的功能，不直接落代码
  - 不引入与第 2 节技术栈冲突的平行框架（如再叠一套 UI 库、ORM、网关）
  - 空项目阶段不预先锁死服务拆分与库表清单；拆分随变更提案确定

---

## 2. 技术栈

- 前端：Vue3 + Vue Router + TypeScript + Pinia + Element Plus
- 后端：Java 21 + Spring Boot + Spring Security + MyBatis-Plus + Spring Cloud Gateway + Nacos
- 接入层：Nginx（静态资源、反向代理、TLS 终结、负载均衡）
- 数据库：MySQL
- 缓存：Redis
- 构建：前端 Vite / 后端 Maven
- 包管理：npm（前端）
- CI/CD：Jenkins（自动构建与部署；流水线创建见 `docs/jenkins-pipeline-guide.md`）

禁止引入与上表冲突的平行框架。确需替换技术栈时，先改本节并走变更流程。

### 2.1 系统基础依赖

各后端系统**统一**使用下列基础库，禁止同类平行选型（如再引入 Hibernate/JPA、Jedis、
另一套锁客户端、另一套对象映射）。具体坐标与版本以各系统父 `pom.xml` 的
`dependencyManagement` 为准，**不在本文件粘贴版本号**；升级先评估兼容性并走变更流程。

| 依赖 | 用途 | 落点 | 约定 |
|------|------|------|------|
| **MyBatis-Plus** | ORM、通用 Mapper、分页、逻辑删除、自动填充 | `{system}-framework` 装配；Entity/Mapper 在 `{domain}-service` | 业务模块禁止自建 `SqlSessionFactory` / 分页插件 / `MetaObjectHandler`；一律用 framework 统一装配 |
| **Redis（Spring Data Redis + Redisson）** | 缓存、会话、分布式锁、限流计数 | `{system}-framework`（连接与序列化）；业务按需注入 | 缓存 key 见 6.9；分布式锁用 Redisson；禁止业务模块自建 RedisTemplate / RedissonClient |
| **Lombok** | `@Data` / `@Builder` / `@Slf4j` 等样板消除 | 编译期，全模块可用 | 仅用于样板；Entity 关键字段仍可读性优先；CI 需开启注解处理 |
| **Spring Boot Actuator** | 健康检查、指标、存活探针 | 可运行模块（`*-service` / `*-gateway`） | 每个服务必须暴露 `/actuator/health`；生产仅开放必要端点 |
| **MapStruct** | Entity ↔ DTO/VO 对象映射 | 编译期，业务模块使用 | Entity 出参禁止手写逐字段拷贝；映射接口放 `mapper` 或 `converter` 包；禁止用 BeanUtils 反射拷贝做主路径 |
| **SpringDoc OpenAPI** | 接口文档（`@Operation` / `@Schema`） | `{system}-framework` 统一配置；业务只写注解 | 禁止各模块另起 Swagger 配置类 |
| **Bean Validation** | 入参校验（`@Valid` 等） | 全模块；契约见 6.4.4 | 校验失败走全局异常处理 |
| **Jackson** | JSON 序列化；Long→String 全局策略 | `{system}-framework` 统一 `ObjectMapper` | 禁止字段上散落 `@JsonSerialize`（见 6.4.7） |
| **SLF4J + Logback** | 日志门面与实现 | 全模块 | 业务用 `@Slf4j` / 注入 Logger；禁止 `System.out` / `printStackTrace` |
| **Micrometer Tracing**（Brave / OpenTelemetry 桥） | 分布式链路（TraceId / SpanId） | `{system}-framework` 传播与采样；可运行模块上报 | 禁止 Sleuth；业务禁止手写透传头；规范见 6.11.1 |

**落点原则（与 5.4 / 6.4 一致）：**

- 需要连接中间件或额外配置才能工作的依赖（MyBatis-Plus、Redis/Redisson、Swagger、Jackson 装配）
  **只**在 `{system}-framework` 封装一次；业务模块依赖 framework 后直接用，禁止重复装配。
- 纯编译期 / 零配置工具（Lombok、MapStruct）可随模块引入，不算「需配置组件」。
- Actuator 随可运行模块引入；`common` / `framework` 不自带健康检查实现。
- `common` **禁止**依赖 MyBatis-Plus、Redis/Redisson、数据源、Actuator。

**常见反模式：**

| 反模式 | 正确做法 |
|--------|----------|
| 业务模块再引 JPA / MyBatis（非 Plus）/ Jedis | 统一 MyBatis-Plus + Redisson |
| 各服务各写一套 RedisTemplate / Redisson 配置 | framework 统一装配，业务只注入使用 |
| Entity→VO 用 `BeanUtils.copyProperties` 做主路径 | MapStruct 或集中转换层 |
| 去掉 Actuator 或不暴露 health | 可运行模块必须暴露 `/actuator/health` |
| 日志无 TraceId / 手工拼 Header 透传 | MDC + Micrometer Tracing（见 6.11.1） |
| 引入 Sleuth 等 Boot2 旧追踪 | 统一 Micrometer Tracing（Boot3） |
| 在本文件或代码注释锁死基础库版本号 | 版本在父 POM `dependencyManagement` 维护 |

---

## 3. 环境配置与启动

### 3.1 开发环境要求

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21+ | 后端运行环境 |
| Node.js | 24+ | 前端运行环境（统一用 24） |
| npm | 9+ | 前端包管理 |
| Nginx | 1.20+ | 接入层（静态、反向代理、TLS） |
| MySQL | 8.0+ | 数据库 |
| Redis | 6.0+ | 缓存 |
| Nacos | 2.2+ | 注册中心/配置中心 |

### 3.2 启动与自检命令

> 仓库当前尚未创建 `frontend/`、`backend/`。下列为**目标约定**；
> 脚手架落地后必须以真实 `package.json` scripts / Maven 模块为准跑通，再允许提交业务代码。

```bash
# 后端：在某一系统的 backend 目录下，用 -pl 启动具体模块
cd backend/<system> && mvn clean install -DskipTests
cd backend/<system> && mvn spring-boot:run -pl <module>

# 前端：在某一系统的 frontend 目录下
cd frontend/<system> && npm install && npm run dev
```

**提交前自检（按改动涉及的系统分别跑；脚手架落地后必须配置并跑通）：**

```bash
cd frontend/<system> && npm run lint && npm run type-check && npm run test
cd backend/<system> && mvn test
```

- 缺 lint / type-check / test 命令时，先在脚手架变更中补上，不得用“跳过检查”换提交速度。
- **各组件接入信息（IP、端口、用户名、密码、连接串、Bucket/库名等）一律查 `docs/test-env.md`**，本文件不写死具体值。
- 端口、网关前缀、环境差异以 `docs/test-env.md` 与后端 `application.yml` / `application-dev.yml` / `application-test.yml` 为准；生产敏感项以 Nacos / 密钥管理为准。

### 3.3 环境变量管理

- 环境变量统一通过 `.env` 管理，禁止把配置散落硬编码在业务逻辑中。
- **密钥分级入仓（必须遵守）：**
  - **禁止入仓**：生产（`prod`）密码、私钥、Token、生产连接串 → 只放 Nacos/密钥管理/Jenkins Credentials。
  - **允许入仓**：开发（`dev`）、测试（`test`）环境的账号密码，可写入 `docs/test-env.md`、`application-dev.yml` / `application-test.yml` 等文档与配置。
  - 仍建议测试环境只推公司内网仓库，不同步到公开仓库。
- **查各组件配置（MySQL 等的 IP、端口、用户名、密码）时，直接打开 `docs/test-env.md`**，不要在本文件或对话里猜。
- `.env`：本地私密可选（gitignore）；测试/开发密码优先写入上款允许的文档或测试配置，不必强依赖 `.env`。
- 敏感配置优先走 Nacos；**生产必须走配置中心或密钥管理，禁止明文进仓库。**
- 环境变量命名：`SERVICE_NAME_ENV_KEY` 大写下划线格式。

---

## 4. 目录结构（目标）

`frontend/` 与 `backend/` 是**多系统容器**，其下按系统名分目录，各系统独立、可单独构建与部署，
**不是**全仓库只对应一对前后端工程。

```
.
├── frontend/                    # 多系统前端容器
│   └── <system>/                # 一个独立前端工程（Vue3 + TS + Pinia + Vite）
│       ├── package.json
│       └── src/
├── backend/                     # 多系统后端容器
│   └── <system>/                # 一个独立后端系统（Maven 多模块）
│       ├── pom.xml              # 父 POM（聚合模块、锁依赖版本）
│       ├── <system>-common/     # 必选：纯基础（见 5.4；不含需额外配置的组件）
│       ├── <system>-framework/  # 必选：基础设施（MyBatis-Plus/Redis 等，见 5.4）
│       ├── <system>-gateway/    # 建议有：该系统网关（仅边缘治理，见 5.2）
│       ├── <system>-{domain}-api/   # 按需：对外 DTO/VO+Feign 契约（无 Entity，见 5.6.1）
│       └── <system>-…-service/  # 业务模块（自带本域 Entity；按域拆分，随变更增加）
├── docs/                        # 流程指导 + 环境信息（test-env.md 为组件 IP/端口/账密真相源；生产密钥禁止）
├── deploy/                      # 部署配置（如 nginx.conf、compose、Jenkins 共享脚本；按系统可再分）
├── openspec/                    # SDD 制品（勿改定义，见第 8 节）
├── .claude/                     # Claude Code 命令与技能
├── .trae/                       # Trae 等价技能
└── CLAUDE.md                    # 本文件
```

**约定：**

- 新增系统 = 新增 `frontend/<system>/` 与（如需后端）`backend/<system>/`，彼此不共享源码目录；
  跨系统复用只允许通过接口，或极薄的、已在变更中说明的基础库，禁止直接 `import` 另一系统业务代码。
- 系统名用小写连字符，一经确定不随意改名；新建/删除顶层或系统级目录时同步更新本节。
- **脚手架落地顺序（每个系统内）：** `common`（纯基础契约）→ `framework`（MyBatis-Plus/Redis 等需配置组件）→
  网关与认证 → 前端壳（布局/路由/axios/Pinia）→ 再按 openspec 变更扩业务。

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
                                          每服务数据自治        缓存/会话等         注册与配置
```

**硬约束：**

- 前端只访问**本系统对外入口（Nginx）**，由 Nginx 反代到本网关；禁止直连业务服务。
- 禁止把 A 系统的页面/服务/库表嵌进 B 系统；跨系统只走显式接口（且须走变更）。
- 服务按业务域拆分：单一职责、高内聚、低耦合、独立部署、数据自治。
- 服务命名：`{domain}-service`；**具体服务列表随变更确定，不在此预定。**
- 服务间只通过 API（Feign）通信，禁止跨服务直接读对方数据库；禁止跨服务共享 Entity（见 5.6.1）。
- 跨服务一致性优先最终一致（消息/补偿），禁止随意引入强一致分布式事务。

### 5.2 接入层 Nginx 与网关职责边界

**分层原则：** Nginx 做**接入层**（流量入口与静态），网关做**边缘治理**（应用层路由与安全策略）。
二者不抢职责：Nginx 不写业务/鉴权规则，网关不管 TLS 与静态资源。

| 能力 | Nginx（接入层） | 网关 Gateway（边缘治理） | 业务服务 |
|------|-----------------|--------------------------|----------|
| 静态资源 | **托管**前端构建产物、缓存与 gzip | 不处理 | 不处理 |
| TLS / 证书 | **终结 HTTPS**、HTTP→HTTPS | 内网 HTTP 或透传 | — |
| 负载均衡 | 对网关实例（及必要时直出静态）轮询/权重 | 对下游服务的路由与灰度 | — |
| 反向代理 | `/api` 等路径反代到网关；可按系统/域名分流 | 路径/断言转到具体微服务 | — |
| 路由 | 域名/前缀级分流（多系统入口） | **应用级**路由到服务 | — |
| 鉴权 | 仅可选的粗防护（如 IP 黑名单、基础限速） | **认证**：JWT/会话、登录态粗拦截 | **授权**：RBAC/数据权限 |
| 限流 | 连接级/基础限速（防爬、防刷外壳） | 入口 QPS/熔断/超时等应用策略 | 业务配额 |
| 日志 | 访问日志、错误日志 | 应用访问日志、TraceId 透传 | 业务操作/领域审计 |
| 其他 | 跨域相关响应头（若在边缘统一）、路径改写 | CORS 策略、用户身份注入/透传 | 参数校验、事务、业务规则 |

**部署约定：**

- 生产/测试：浏览器 → Nginx（静态 + 反代 `/api`）→ 网关 → 服务。
- 本地开发可由 Vite 代理直打网关，**不得**因此认为生产可绕过 Nginx。
- 多系统：优先「一域名多前缀」或「多子域名」在 Nginx 分流，再进对应网关/服务组。
- Nginx 配置纳入版本库（如 `deploy/nginx/` 或各系统 `deploy/`），禁止只活在服务器上的野配置。

**双重鉴权模型（不变）：**

1. 网关：认证 + 是否登录 + 路由级放行/拒绝（边缘）。
2. 业务服务：细粒度授权与数据权限（用 Spring Security / 方法级鉴权），**不可**只信网关。

网关不得内嵌具体业务规则；Nginx 不得承载业务鉴权。规则只能写在边缘说明设计有问题，应改业务侧。

### 5.3 请求链路（一次同步调用）

```
浏览器/客户端
  → Nginx：TLS、静态资源、反代 /api → 网关、写接入访问日志
  → 网关：认证、限流、写应用访问日志、注入/透传 TraceId 与用户身份
  → 业务服务：MDC 输出 traceId（见 6.11.1），日志/链路同一关联
  → 业务服务 Controller：参数校验（DTO + Bean Validation）
  → Service：业务规则、事务边界、细粒度鉴权
  → Mapper/DB 或 Feign 下游（Feign 必须在事务外）
  → 统一 Result{code,msg,data} + 全局异常处理（common）
  → 网关/前端：按 code 分流（401 跳转、业务提示、通用错误）
```

- 同步链路短、职责单层；耗时/可重试工作走异步（MQ/线程池），必须有重试与死信。
- 失败语义：网络/5xx 可重试；4xx 业务/校验错误不重试；写操作幂等（见 6.11）。

### 5.4 系统内部模块关系（后端）

**模块分层原则：**
- **`common` 只放「零额外配置」的纯基础**（类型/契约/工具/注解）。
- **凡是需要额外配置文件或中间件连接才能工作的一律放 `framework`**（如 MyBatis-Plus、Redis、Swagger、ID 发号、审计填充）。
- `common` **禁止**依赖 MyBatis-Plus、Redis、数据源等；`framework` 可依赖 `common`。
- **实体（Entity）只属于拥有该表的 `{domain}-service`**；跨服务共享只走 `{domain}-api`（DTO/VO + Feign），**禁止**共享 Entity。

```
        ┌──────────────────────────────────────────┐
        │              {system}-common              │
        │  Result/错误码/异常类型/分页契约/通用枚举/  │
        │  常量/工具/注解（零额外配置，禁止 ORM/Redis）│
        └───────────────────┬──────────────────────┘
                            │ 被依赖（编译期）
        ┌───────────────────▼──────────────────────┐
        │            {system}-framework             │
        │  MyBatis-Plus/Redis/Swagger/ID与审计/     │
        │  Long序列化/通用配置绑定（需配置文件）      │
        └───────────────────┬──────────────────────┘
                            │ 被依赖（编译期）
        ┌───────────────────┼──────────────────────┐
        │                   │                      │
        ▼                   ▼                      ▼
 {system}-gateway    {domain}-service        {domain}-service
 （边缘治理）         （业务A，自带 Entity）    （业务B，自带 Entity）
 （可仅依赖 common）  （依赖 framework）        （依赖 framework）
        │                   │                      │
        │                   ▼                      │
        │            {domain}-api                  │
        │         （DTO/VO + Feign 接口）           │
        │                   ▲                      │
        │                   └────── Feign 调用 ─────┘
        │                  （禁止互读数据库/共享 Entity）
        │
        └── 可选：{domain}-dao / {domain}-entity
            （仅同库同限界多模块共享表映射；依赖 framework）
```

| 模块 | 必选 | 职责 |
|------|------|------|
| `{system}-common` | **必须** | 零配置纯基础：返回体、错误码、异常类型、分页契约、通用枚举/常量/工具/注解；**不含** MyBatis-Plus、Redis、数据源、实体等需配置组件 |
| `{system}-framework` | **必须** | 基础设施封装：MyBatis-Plus、Redis、Swagger/OpenAPI、ID 发号与审计填充、Long 序列化、`@ConfigurationProperties` 通用配置；承载 6.4 中「需配置」的实现 |
| `{system}-gateway` | 建议有 | 仅 5.2 边缘治理；可与网关产品/独立部署形态替换，职责边界不变；一般只依赖 `common` |
| `{system}-{domain}-service` | 按需 | 领域服务；**本域 Entity/Mapper 只在这里**；依赖 `framework`；自建返回体/异常/分页/ORM 配置视为违规 |
| `{system}-{domain}-api` | 按需 | 对外契约模块：DTO/VO、Feign 接口与 Fallback；**无 Entity、无 Mapper、无业务实现**；供其他服务依赖 |
| `{system}-{domain}-dao`（可选） | 极少 | 仅当**同一库、同一限界上下文**被多个 Maven 模块共用表映射时再抽；依赖 `framework`（MP 注解）；禁止跨系统引用 |

**common 边界：** 只放可复用、打开即用的纯逻辑/类型；禁止引入需要 `application*.yml` 才能启动的组件。
**framework 边界：** 只放与业务无关的中间件/ORM/通用配置封装；业务规则禁止下沉；变更影响所有业务模块，须走变更流程。
**实体归属与跨模块依赖：** 见 **5.6**。

### 5.5 前端结构约定

```
src/
├── api/          # 接口请求（按业务模块分包 + 统一 axios 封装）
├── views/        # 页面（按业务模块分包）
├── components/   # 公共组件（layout / common / business）
├── stores/       # Pinia（按模块）
├── router/       # 路由（按模块）
├── types/        # TS 类型（api / model / common）
├── utils/        # 工具
├── styles/       # 全局样式
└── locales/      # i18n
```

| 原则 | 说明 |
|------|------|
| 单一职责 | 一个组件只做一件事，复杂组件拆子组件 |
| Props 向下 / Events 向上 | 禁止直接改子组件状态、禁止子组件反向引用父级 |
| 插槽扩展 | 避免 props 爆炸 |
| 组合式优先 | `<script setup lang="ts">`，禁止 Options API |
| 状态 | 跨组件共享一律 Pinia，禁止全局变量/事件总线 |
| 请求 | 业务组件不直接调 axios，只调 `src/api` |
| UI | 统一 Element Plus，禁止自造重复轮子；样式 scoped，禁止内联堆叠 |
| 路由 meta | 含 `title` / `icon` / `hidden` / `requiresAuth`；name 用 PascalCase |
| 权限 | 与后端权限标识一致，禁止在前端硬编码角色 |

### 5.6 后端模块与包结构（多模块，common + framework 必选）

每个 `backend/<system>/` 为 **Maven 多模块**工程，模块划分与依赖关系见 **5.4**。

#### 5.6.1 实体（Entity）归属

| 场景 | 实体放哪 | 模块间怎么依赖 |
|------|----------|----------------|
| **默认（推荐）** | `{domain}-service` 内 `entity/`（或 `mapper/entity`） | 其他服务只依赖该域 `{domain}-api`（DTO/VO + Feign），**禁止**依赖 Entity |
| 同库、同一限界上下文、多个 Maven 模块共用表 | 可选抽 `{domain}-dao` / `{domain}-entity` | 仅限本限界内模块引用；`dao` 依赖 `framework`（MP 注解） |
| 跨服务只读展示对方数据 | **不共享 Entity** | 投影表 / 事件同步（如 `sys_user_ref`），或 Feign 查 DTO |

**硬规则：**
- Entity **只属于拥有该表的服务/限界**；随库表可独立演进，不对外作契约。
- **禁止**把 Entity 放进 `common`（违反零配置）或 `framework`（禁止业务表模型下沉）。
- **禁止**跨服务 import 对方 Entity / Mapper；需要对方数据时走 Feign + `{domain}-api` DTO，或本域投影。
- `{domain}-api`：只含 DTO/VO、Feign 接口、Fallback、常量；**无** Entity、Mapper、Service 实现。
- 只读展示偏好「薄投影」而非共享实体，避免分布式单体。

#### 5.6.2 业务模块内包结构

```
src/main/java/com.example.{system}.{module}/
├── controller/   # 接口层（dto 入参 / vo 出参）
├── service/      # 业务层（impl 实现 / bo 业务对象）
├── mapper/       # 数据访问
├── entity/       # 本域表映射实体（仅本服务/本限界，禁止外泄）
├── feign/        # 对其他服务的 Feign 客户端（接口与 Fallback 优先来自 {domain}-api）
├── config/       # 模块自有业务配置（ORM/Redis 等通用配置在 framework）
├── constants/    # 常量（禁用 constant 单数；通用常量放 common）
├── enums/        # 枚举（业务枚举在本模块；通用状态等在 common）
└── util/         # 工具（通用工具放 common）
```

调用链：`Controller → Service → Mapper`；Service 之间可互调。
**禁止** Controller 直接调 Mapper。

| 对象 | 职责 | 落点与边界 |
|------|------|------------|
| `Entity` | 表映射，只含持久化字段 | **拥有表的** `{domain}-service`（或同限界 `{domain}-dao`）；禁止暴露给 Controller / 前端 / 其他服务 |
| `DTO` | 接口入参 | 本服务 controller/dto；跨服务契约放 `{domain}-api` |
| `VO` | 接口出参 | 本服务 vo；跨服务契约放 `{domain}-api` |
| `BO` | Service 内部业务对象 | 不对外；不进 `{domain}-api` |

- 禁止 `Entity` 直接作接口入参/出参；转换用 MapStruct 或集中转换层，禁止在 Controller 手写逐字段赋值。
- Service 接口 + 实现分离（`XxxService` + `XxxServiceImpl`）；业务规则只在 Service。
- Mapper 只做数据访问。

**Feign：** 统一 Feign，禁止 RestTemplate / 手写 HTTP 调其他服务；客户端放 `feign/`，
接口与 Fallback 优先定义在 `{domain}-api`；必须有 Fallback；建议连接超时 3s、读取超时 10s。

---

## 6. 开发规范

### 6.1 编码与命名

- 前端：变量/函数 `camelCase`，组件 `PascalCase`，常量 `UPPER_SNAKE`。
- 后端：类 `PascalCase`，方法/变量 `camelCase`，常量 `UPPER_SNAKE`。
- 注释与提交说明使用中文。
- 禁止吞异常；统一走 6.4 全局异常处理。
- 前端 Prettier + ESLint；后端遵循 Spring 官方代码风格。

### 6.2 接口与数据模型

- 契约来源：后端真实 Controller 签名与 API 文档；前端按真实字段取值。
- 枚举/字段以后端枚举类与数据库表结构为准，禁止前端自造枚举值。
- RESTful：URL 小写 + 连字符；资源用名词复数；GET 查 / POST 增 / PUT 改 / DELETE 删。
- 路径带版本前缀（`/api/v1/...`）；破坏性变更才升版本。
- 方法名见名知义：`getXxx / listXxx / createXxx / updateXxx / deleteXxx`。

### 6.3 提交规范

- 格式：`type(scope): subject`，type ∈ `feat / fix / refactor / docs / chore / test / perf / ci / style`。
- subject 中文祈使句、≤ 50 字符、不写句号。
- 单人开发直接提交 `main`；较大功能可开分支隔离。
- 一个提交只做一件事。

提交前自查：

- [ ] 编译通过，lint / type-check / 测试通过
- [ ] 无 `console.log` / `System.out.println` 调试残留
- [ ] 无**生产**密钥、密码、Token 泄露；测试/开发密码仅出现在允许的环境文档中
- [ ] 符合本文件规范

示例：

- 好：`feat(user): 新增用户分页查询接口`
- 好：`fix(order): 修复订单状态未回滚问题`
- 坏：`update` / `修改了一些东西` / `feat: add user, order, fix bug and docs`

### 6.4 基础代码契约（common 管契约，framework 管需配置实现）

> 本节约定「必须长成什么样」，不附完整实现。各系统脚手架时**第一批**落入 `common` / `framework`；
> 业务模块直接复用，禁止各写一套。
>
> **落点规则：**
> - **纯契约/类型/工具**（返回体结构、错误码、异常类型、分页字段契约）→ `{system}-common`
> - **需要额外配置文件或中间件才能工作**（MyBatis-Plus、`MetaObjectHandler`、`@TableLogic` 装配、
>   分页拦截器、Redis、Swagger 配置、ID 发号、Long 序列化 `ObjectMapper`）→ `{system}-framework`
> - `common` 禁止依赖 MyBatis-Plus / Redis / 数据源；需要这些才能编译或启动的代码不得放进 `common`。

#### 6.4.1 统一返回体

- 所有接口统一 `{ code, msg, data }`（结构在 `common`）。
- 分页统一 `{ records, total, size, current }`，禁止自造分页结构（字段契约在 `common`；
  与 MyBatis-Plus `Page` 的互转/拦截器在 `framework`）。
- 前端 axios 拦截器统一解包，业务代码禁止逐处判断返回结构。

#### 6.4.2 错误码

| 分段 | 含义 |
|------|------|
| `1xxxx` | 系统异常 |
| `2xxxx` | 业务异常 |
| `3xxxx` | 参数校验异常 |

错误码与文案用枚举/常量维护，禁止在业务代码硬编码错误码字符串。

#### 6.4.3 全局异常处理

- 后端 `@RestControllerAdvice` 分类处理业务 / 校验 / 系统异常（异常类型在 `common`，装配可随业务模块或 `framework`）。
- 禁止吞异常；禁止把堆栈或内部细节返回前端；系统异常返回友好提示并记日志。
- 前端拦截器按 `code` 分流（登录失效、业务提示、通用错误）。

#### 6.4.4 数据校验

- 入参用 Bean Validation（`@Valid` 及校验注解），禁止在 Controller/Service 手写重复 if。
- 校验失败走全局异常处理。

#### 6.4.5 实体与审计字段

所有实体必须包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| `createTime` | `LocalDateTime` | 创建时间 |
| `updateTime` | `LocalDateTime` | 更新时间 |
| `createBy` | `Long` | 创建人 |
| `updateBy` | `Long` | 更新人 |
| `deleted` | `Integer` | 逻辑删除（0 未删 / 1 已删） |

- 字段清单属于契约；逻辑删除 `@TableLogic`、审计 `MetaObjectHandler` 自动填充等 **MyBatis-Plus 装配在 `framework`**，禁止业务代码手写 `deleted = 0` / 手写赋值审计字段。
- 主键 `id`：`Long` / `BIGINT`；策略 **yyMMdd(6) + 序列号(8) = 14 位**（如 `26092100000001`）；
  序列号由序列表按日递增；禁止数据库自增。发号实现（依赖 DB）在 `framework`。

#### 6.4.6 分页入参

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `current` | `long` | `1` | 页码（从 1 起） |
| `size` | `long` | `10` | 每页条数 |

排序字段 `orderBy` / `order` 必须白名单校验，禁止拼进 SQL。

#### 6.4.7 Long 序列化

- 出参中 `Long`（含 `id`）一律序列化为 `String`（防 JS 精度丢失）。
- 后端：全局 `ObjectMapper` 统一处理（配置在 `{system}-framework`），禁止字段上散落 `@JsonSerialize`。
- 前端：对应字段类型为 `string`，禁止 `number`。
- 入参：前端传字符串，后端用转换器或 `@JsonProperty` 接收。

### 6.5 数据库规范

- 表名、字段名 `snake_case`，表名用业务名词单数；禁止物理外键。
- 每张业务表含 6.4.5 审计字段；字段落库名对应 `create_time` 等。
- 金额 `DECIMAL`、状态/标记 `TINYINT`、时间 `DATETIME`、文本合理定长 `VARCHAR`（禁止无脑 `TEXT`）。
- 字符串默认 `NOT NULL` + 默认值；逻辑删除默认 `0`。
- 索引：普通 `idx_表名_字段`，唯一 `uk_表名_字段`；只为真实查询建；单表索引 ≤ 5；禁止冗余索引。
- 查询禁止 `SELECT *`；大表必须走索引。

### 6.6 事务规范

- `@Transactional` 只加在 Service，禁止加在 Controller / Mapper。
- 默认 `RuntimeException` 回滚；业务异常需回滚时显式 `rollbackFor`。
- **禁止在事务内调 Feign/HTTP**；远程调用放事务外或提交后。
- 只读查询用 `@Transactional(readOnly = true)`。

### 6.7 安全规范

- SQL 一律参数化（`#{}`），禁止 `${}` 拼用户输入；动态表名/字段名白名单。
- 前端渲染用户输入用 `v-text`，非明确需要禁用 `v-html`；后端必要时做 HTML 转义。
- 密码 BCrypt/Argon2 单向存储；手机号/身份证等日志与出参脱敏。
- **密钥入仓分级：**
  - **生产**密码、私钥、Token、连接串：**禁止**写入仓库任何文件（含文档、配置、Jenkinsfile）；只进 Nacos/密钥管理/Jenkins Credentials。
  - **测试 / 开发**账号密码：**允许**写入仓库文档与配置（如 `docs/test-env.md`、`application-test.yml`、`application-dev.yml`），便于联调；须标注环境，禁止与生产混用同一账号。
  - 生产密钥误入仓：立即改密、清理历史（需征询），并记为严重问题。
- Token 用 JWT：Access 30 分钟，Refresh 7 天；网关与业务服务双重鉴权。
- 登录失败限制；敏感接口（改密、支付等）二次验证；关键接口限流；CORS 仅信任域名。

### 6.8 API 文档

- 接口用 Swagger/OpenAPI 注解（`@Operation` / `@Parameter` / `@Schema`）。
- OpenAPI/Swagger 通用配置在 `{system}-framework`（需配置类），业务模块只写注解，禁止各起一套文档配置。
- 文档随代码更新，禁止另手维护一份接口文档文件。

### 6.9 性能规范

- 普通查询 ≤ 200ms；复杂查询/报表 ≤ 1s；慢查询优化或异步。
- 缓存 key：`{服务名}:{模块}:{业务标识}`；必须设 TTL；更新优先「更新 DB 再删缓存」。
- 批量用 batch insert/update，禁止循环单条；深度分页用游标或覆盖索引。
- 耗时操作走异步（MQ/线程池），且必须有重试与死信。

### 6.10 配置规范（必须区分环境）

每个后端**可运行**模块（`*-service`、`*-gateway` 等）的配置**必须且仅有**下列三份（缺一不可；禁止只维护单一 `application.yml`）。
`common` / `framework` 为库模块，**不**自带 `application*.yml`，所需连接配置由可运行模块注入。

| 文件 | profile | 用途 |
|------|---------|------|
| `application.yml` | 公共 | 各环境相同项：应用名、`server`/`spring`/`mybatis-plus`/`logging` 分组等；**不含**具体主机/账号密码 |
| `application-dev.yml` | `dev` | **开发环境**：本机/开发中间件、调试日志；**允许**含开发账号密码 |
| `application-test.yml` | `test` | **测试环境**：测试中间件与测试库；**允许**含测试账号密码 |

- **组件连接信息真相源**：MySQL / Redis / Nacos / MinIO 等的 IP、端口、用户名、密码、库名/Bucket，以 **`docs/test-env.md`** 为准；`application-dev.yml` / `application-test.yml` 中的值必须与之一致，不得另写一套。
- 本地开发默认激活 `dev`（`spring.profiles.active: dev`）；联调/部署切 `test`。
- 配置项 `kebab-case`；用 `@ConfigurationProperties` 绑定，禁止散落 `@Value`。
- 禁止把**生产**连接串、密钥写进任何配置或文档并提交到 Git；生产敏感项只走 Nacos/密钥管理。
- 测试/开发密码可按 6.7 分级入仓；`.env` 仅作本机可选补充。
- 重大配置变更先在 `test` 验证，再影响生产环境。
- CI/CD 由 Jenkins 按环境发布；新建/修改流水线按 `docs/jenkins-pipeline-guide.md` 执行；Jenkinsfile **禁止**写生产密钥（用 Credentials）。
- 测试/开发环境主机与账号密码见 `docs/test-env.md`；生产密钥另册且不进本仓库。

### 6.11 依赖与工程

- 锁定 `package-lock.json` / `pom.xml` 版本；升级先评估兼容性。
- 定期 `npm audit` / `mvn dependency-check`，高危必修。
- 每个服务暴露 `/actuator/health`；指标用 Micrometer；关键指标告警。
- 链路追踪与日志规范见 **6.11.1**（Micrometer Tracing；**禁止 Sleuth**）。
- 上传：单文件 ≤ 10MB，批量 ≤ 50MB；类型白名单；OSS/MinIO；UUID 命名；逻辑删除 + 定时清理。
- 写操作幂等（请求 ID 或业务唯一键）；关键接口带 `Idempotent-Key`。
- 重试：指数退避最多 3 次（1s/2s/4s）；超时与 5xx 可重试，4xx 不重试；重试打日志。

### 6.11.1 日志与追踪

**选型：** SLF4J + Logback 记日志；**Micrometer Tracing**（Spring Boot 3）做链路；
**禁止 Sleuth**（已停更）。TraceId / SpanId 写入 **MDC**，由日志模式输出；
`traceparent` / B3 等传播头由 `{system}-framework` 统一配置，Feign / Gateway / 线程池 **自动透传**，
网关注入 TraceId（见 5.2），**禁止**业务代码手工拼接 Header 或自建追踪上下文。

**建议日志模式（须含 traceId）：**

```text
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{traceId:-}] [%X{spanId:-}] %logger{40} - %msg%n
```

| 约定 | 要求 |
|------|------|
| TraceId | 每条应用日志必须可关联 `traceId`；缺失时查 Logback 模式与 tracing 装配 |
| 级别 | `error` 仅故障/需告警；业务失败 `warn`；关键路径 `info`；排查 `debug`；禁止滥用 error |
| 敏感信息 | 禁止打印密码、完整 Token、身份证/银行卡；手机号脱敏；禁止整包参数对象进 SQL 日志 |
| 调试残留 | 提交前无 `System.out` / `printStackTrace` / 临时刷屏日志 |
| 文件与保留 | 走 Logback 按天滚动 + 保留策略；禁止业务旁路写独立日志文件 |
| 跨线程 / MQ | 透传或记录 traceId，保证异步链路可关联 |
| 检索 | 测试/生产按 `traceId` 从日志平台捞全链路，并与 Zipkin/OTLP span 对照 |

**可观测性分工：** 健康看 `/actuator/health`；指标看 Micrometer（6.11）；排查看本节日志 + 链路。
TraceId 贯穿 Nginx 访问日志（可选）、网关、业务服务；异常处理（6.4.3）打日志时一并带出 traceId。

### 6.12 测试规范

- 核心 Service 必须有单测；改 bug 先补回归测试再修复。
- 命名 `XxxTest` + 行为描述；数据自包含。
- 接口层用 MockMvc；禁止无断言空测试。
- 测试失败不得提交。

### 6.13 枚举与常量

- 枚举实现 `BaseEnum<T>`（`code` + `desc`）；类名 `XxxEnum`。
- 常量放 `constants` 包，类名 `XxxConstants`；禁止魔法值。
- 库里存 `code`，界面显示 `desc`；接口只传 `code`，禁止传枚举名或整个枚举对象。

### 6.14 国际化

- 前端 `vue-i18n`，后端 `MessageSource`；用户可见文案必须 i18n key。
- 语言包：`src/locales/zh-CN.json`、`en-US.json`。
- 日期/数字用 `Intl` 或 dayjs locale。

---

## 7. 协作红线（AI 工具必须遵守）

1. 不可逆操作（数据库写、migration、seed、改写 git 历史、改依赖锁文件）必须先征询。
2. 不擅自修改 `openspec/` 制品定义（proposal / specs / design）；`tasks` 只允许勾选状态位。
3. 不添加未被要求的功能、抽象或重构。
4. 复用接口/枚举/字段前，先确认真实契约，不凭推导实现。
5. 不把**生产**密钥、Token、生产连接串写入任何将提交的文件；测试/开发密码仅可按 6.7 写入环境文档。
6. 文档与代码不一致时：以代码与契约为准改文档，或停下来指出冲突，禁止两边将就。
7. 不在本文件写入服务清单、库表明细、完整实现代码或短期任务。

### 7.1 常见反模式（禁止）

| 反模式 | 正确做法 |
|--------|----------|
| 前端 `number` 接主键 `id` | 一律 `string` |
| `Controller` 注入 `Mapper` | 只走 `Service` |
| 事务内调 Feign | 远程调用放事务外 |
| `${}` 拼 SQL | 只用 `#{}` |
| 吞异常 / 空测试凑覆盖率 | 显式失败并修根因 |
| 预写未提案的服务名与表结构 | 先 SDD，结构进制品/代码 |
| 假设仓库只有一个前后端工程 | `frontend/`、`backend/` 下按系统多工程并存 |
| 业务模块自建返回体/异常/分页/ORM·Redis 配置 | 契约用 `{system}-common`，需配置组件用 `{system}-framework` |
| 把 MyBatis-Plus、Redis、数据源等塞进 `common` | `common` 只放零配置纯基础；需配置组件一律 `framework` |
| 把 Entity 放 `common` / `framework`，或跨服务共享 Entity | Entity 只在拥有表的 `{domain}-service`；跨服务只走 `{domain}-api` DTO/Feign |
| 业务模块 import 其他服务 Entity/Mapper | 只依赖 `{domain}-api`，或本域投影表 |
| 只有单一 `application.yml` 不分环境 | 必须三份：`application.yml` + `application-dev.yml` + `application-test.yml` |
| 把细粒度授权/业务日志/业务规则塞进网关 | 网关只做 5.2 边缘治理；授权与审计在服务 |
| 在 Nginx 写业务鉴权/业务限流规则 | Nginx 只做接入；应用策略在网关，业务规则在服务 |
| 生产静态资源由后端或网关托管 | 静态走 Nginx；网关只反代 API |
| 流水线跳过测试/吞错过门 | 质量门失败即失败；见 docs/jenkins-pipeline-guide.md |
| 本文件粘贴依赖版本、端口、**生产**密钥、长代码 | 稳定契约在本文件；组件 IP/端口/账密见 `docs/test-env.md`；构建版本见构建文件；生产密钥见 Nacos/密钥管理 |

---

## 8. 规范驱动开发（SDD）

本项目采用 OpenSpec（schema：`spec-driven`）。入口：
`.claude/commands/opsx/`（Trae 侧：`.trae/skills/openspec-*`）。

- 制品权威性：`proposal / specs / design / tasks` 为变更指令源；实现以制品为准。
- 工作流（按序）：
  - `/opsx:explore` — 澄清需求，只思考不实现
  - `/opsx:propose <change>` — 生成 proposal / design / tasks
  - `/opsx:apply <change>` — 按 tasks 实现并勾选状态
  - `/opsx:archive <change>` — 验证后归档
- 状态：`openspec list --json` / `openspec status --change "<name>" --json`
- 铁律：只改 `tasks` 状态位；未归档前避免直接改对应代码。
- 阅读顺序：本文件 → 当前 change 的 proposal/design/tasks → 代码 → 枚举与 Controller。

---

> 维护约定：本文件变更随对应约定调整一并提交。
> 只保留稳定契约；服务名、端口、依赖版本、迭代任务、完整实现一律不写入。
