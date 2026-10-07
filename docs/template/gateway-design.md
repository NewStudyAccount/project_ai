# 通用网关设计（`{system}-gateway` 模板）

> 状态：**模板定稿**（对齐 `CLAUDE.md` §5.2 纯网关鉴权，已裁决）
> 定位：业务系统边缘治理层的**可复制设计参照**；新建 `{system}-gateway` 以本文件裁剪，不替代 `CLAUDE.md` 契约
> 范围：路由、JWT 验签、`X-User-*` 注入、CORS、放行策略、链路透传、配置与检查清单
> 配套：`CLAUDE.md` §5.1/§5.2/§5.3（上位契约）、`example-system-design.md` / `user-center-design.md`（系统级模板）、`unified-auth-center-design.md`（JWKS 指向方）、`docs/version-baseline.md`（组件版本）、`fixbug/2026-09-25-unify-login-sso-gateway.md`（实测坑）
> 约束：本文只沉淀**通用网关设计**；具体服务列表、端口、信任域名、密钥不写入本文

---

## 1. 目标与边界

### 1.1 目标

在「前端 → Nginx → 网关 → 业务服务」标准链路中，网关只做**薄边缘治理**：

- 应用级路由到本系统各 `{domain}-service`（`lb://` 服务发现）
- **鉴权唯一归属**：JWT 验签、登录态拦截、路由级放行/拒绝
- 身份注入：覆盖写入 `X-User-Id` / `X-User-Name`
- **CORS 唯一归属**（信任域名登记 `docs/test-env.md`）
- 请求标识与链路透传（`X-Request-Id` / `traceparent`）

### 1.2 边界（明确不做）

| 不做 | 归属 |
|------|------|
| TLS / 证书、静态资源 | Nginx（接入层） |
| 业务规则、参数校验、事务 | `{domain}-service` |
| 用户登录 / 验密 / 发码 | 认证中心 `auth-service`（无网关单体例外） |
| 限流主路径 | 业务服务 `@RateLimit` + Redisson（网关不作主路径） |
| 写幂等 | 业务服务 `@Idempotent` |
| 细粒度权限码拦截（`system:resource:action`） | 业务侧软校验 / 按钮显隐；硬拦截只保证登录态 |
| Spring Authorization Server、业务 DB、MyBatis-Plus、Redis 业务缓存 | 不进网关模块 |
| 业务规则塞进过滤器 / 自建 JWT 业务解析给业务用 | 禁止（身份只经 `X-User-*` 下发） |

---

## 2. 架构位置与职责分层

```text
浏览器 / SPA（本系统管理端）
  │ HTTPS
  ▼
Nginx（接入层）
  │  · TLS 终结、HTTP→HTTPS
  │  · 托管前端静态
  │  · 反代 /api → 本网关（可按域名/前缀分流多系统）
  │  · 仅粗防护（IP 黑名单、连接级限速）
  ▼
{system}-gateway（边缘治理 · 本模板）
  │  · 路由 lb://{serviceId}
  │  · JWT 验签（JWKS → 认证中心）
  │  · 剥除并注入 X-User-*
  │  · CORS 唯一配置
  │  · 放行/拒绝、请求标识
  ▼
{domain}-service × N（业务服务）
     · 不做登录验签 / @PreAuthorize
     · 只信网关注入的 X-User-*
     · @Idempotent / @RateLimit / 业务规则
     · /internal/** 仅服务间可达，网关不对外路由
```

| 能力 | Nginx | **网关** | 业务服务 |
|------|-------|----------|----------|
| 静态资源 | **托管** | 不处理 | 不处理 |
| TLS | **终结** | 内网 HTTP 或透传 | — |
| 负载均衡 | 对网关实例 | 对下游 `lb://` | — |
| 路由 | 域名/前缀分流 | **应用级**到服务 | — |
| 鉴权 | 粗防护 | **唯一归属**（JWT/登录态/放行） | **不做**用户鉴权 |
| 限流 | 连接级/基础限速 | 超时可保留；**不作主路径** | **主路径** `@RateLimit` |
| CORS | 不写跨域头 | **唯一归属** | 不配置 |
| 身份 | 不注入 | **注入 `X-User-*`** | 只读注入值 |
| 日志 | 访问日志 | 应用访问日志、Trace 透传 | 业务/领域审计 |

**硬约束：**

1. 前端只访问本系统 Nginx 入口，由 Nginx 反代到本网关；禁止前端/浏览器直连业务服务。
2. 业务服务**不得**信任客户端自带的 `X-User-Id` / `X-User-Name`；以网关**覆盖写入**后的值为准。
3. `/internal/**` 仅服务间（Feign/内网）可达：网关**不**配置对外路由到该前缀；不经用户登录态。
4. 多系统一仓时：**一系统一网关**（或按变更明确合并策略）；禁止跨系统网关直连对方库表/服务内部接口。

---

## 3. 模块落点与依赖

### 3.1 落点

```
backend/{system}/
├── {system}-common
├── {system}-framework
├── {system}-gateway          # 本模板
├── {system}-{domain}-api
└── {system}-{domain}-service
```

- 网关是**可运行模块**（自带三份 yml），包名建议 `com.qjj.{system}.gateway`。
- 网关**只依赖** `{system}-common`（错误码/契约对齐）；**不依赖** `{system}-framework`（Servlet 栈，与 WebFlux 冲突）。
- 网关内**无** Entity / Mapper / 业务 Service。

### 3.2 依赖清单（版本一律取 `docs/version-baseline.md`）

| 依赖 | 必须 | 说明 |
|------|------|------|
| `spring-cloud-starter-gateway` | ✅ | WebFlux 边缘 |
| `spring-cloud-starter-loadbalancer` | ✅ **显式** | `lb://` 必需；**禁止**只靠 gateway starter 传递（缺失则 Nacos 有实例仍 503） |
| `spring-cloud-starter-alibaba-nacos-discovery` | ✅ | 注册发现（SCA） |
| `spring-boot-starter-oauth2-resource-server` | ✅ | JWT 验签（JWKS）；**不引 SAS** |
| `spring-boot-starter-actuator` | ✅ | 健康/存活/指标 |
| Lombok | ✅ | provided |
| `{system}-common` | ✅ | 错误码语义对齐 |
| MyBatis-Plus / Redis / Redisson / Feign / MinIO / MQ / Hutool | ❌ | 不进网关 |
| `spring-cloud-starter-alibaba-nacos-config` | 可选 | 若配置中心接入按系统变更决定 |

---

## 4. 核心能力设计

### 4.1 路由

| 约定 | 要求 |
|------|------|
| URI | `lb://{serviceId}`（Nacos 发现），禁止写死实例 IP（生产） |
| 断言 | 默认 `Path=/api/**`；可按子路径拆多服务路由 |
| 对内前缀 | **不**路由 `/internal/**`（或等价前缀）到公网入口 |
| 超时 | 配置连接/读取超时（网关侧保留超时能力；熔断组件暂不引入，见 `CLAUDE.md` §2.2） |
| 版本 | 对外路径 `/api/v1/...`，破坏性变更才升版本（`CLAUDE.md` §6.2） |

路由示例（结构示意，服务名随变更确定）：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: {domain}-service
          uri: lb://{domain}-service
          predicates:
            - Path=/api/**
```

### 4.2 鉴权（JWT 验签 · 唯一归属）

| 项 | 约定 |
|----|------|
| 模型 | **纯网关鉴权**（已裁决）：网关验登录态；业务服务不验用户、不 `@PreAuthorize` |
| 令牌 | JWT（Access Token）；验签用 **JWKS**，`jwk-set-uri` 指向认证中心 |
| 组件 | `oauth2-resource-server`（reactive）；**不引入** Spring Authorization Server |
| 放行 | `OPTIONS`、健康检查、登记过的公开路径；其余 `authenticated` |
| 401 | 未登录 / Token 无效 → HTTP 401 + 系统段「未认证」 |
| 403 | 已认证但路由拒绝 → HTTP 403 + 系统段「无权限」（细粒度业务权限码不在网关做） |
| 本地联调 | `local-pass-through` **仅** `local` profile 允许；**禁止**带入 `test` 生产联调默认开启 |

> **例外：** 认证中心 `auth-service` 自身是登录与 OIDC 发行方（单体、无网关），保留自管登录与管理端 RBAC，不套用本条到其它业务系统。

### 4.3 身份注入（`X-User-*`）

**处理顺序（强制）：**

1. **剥除**客户端传入的 `X-User-Id` / `X-User-Name`（一律不信任）
2. 从 `Authorization: Bearer` 解析已验签 JWT 的 `sub` → `X-User-Id`，`preferred_username`（缺省用 `sub`）→ `X-User-Name`
3. 仅当 `local-pass-through=true` 时，可退 `X-Dev-User-Id` / `X-Dev-User-Name`（联调）
4. 能解析到身份则 **set** 覆盖写入；否则不写（业务侧视为未识别，禁止静默信任旧头）

| Header | 含义 | 谁写入 | 业务侧 |
|--------|------|--------|--------|
| `X-User-Id` | 用户主键（字符串化 Long / 令牌 `sub`） | **仅网关** | 以覆盖后的值为准 |
| `X-User-Name` | 登录名/显示名 | **仅网关** | 同上 |
| `Idempotent-Key` 或 `X-Request-Id` | 幂等/请求标识 | 客户端；网关可补 `X-Request-Id` | 幂等用 |
| `traceparent`（或 B3） | 分布式追踪 | framework/代理自动 | **禁止**业务手拼 |

> **红线（实测）：** 网关必须从 Bearer JWT 注入 `X-User-*`；只认 `X-Dev-*` 会导致经网关的 `/me/*` 空、admin 全量失效（见 fixbug §8/§9）。

### 4.4 CORS（唯一归属）

| 项 | 约定 |
|----|------|
| 配置点 | **只在网关**；Nginx 不写跨域响应头；业务服务不配置 CORS |
| 允许来源 | 显式白名单（`trusted-origins`），**禁止** `*` + credentials |
| 允许方法 | `GET, POST, PUT, DELETE, OPTIONS`（按需增） |
| Credentials | 按登录形态决定；使用 Cookie/SSO 时须与认证中心 host 策略一致 |
| 登记 | 信任域名写入 `docs/test-env.md`（或变更登记）；本地与测试可不同，但须显式列出 |
| 同 host 原则 | IdP、portal、各 RP 的 origin **同一 host**（本地一律 `localhost`，禁止与 `127.0.0.1` 混用） |

### 4.5 放行路径（public-paths）

默认建议：

| 路径 | 说明 |
|------|------|
| `OPTIONS /**` | 预检 |
| `/actuator/health` | 存活探针（勿暴露敏感端点） |
| `/internal/**` | **不是放行给公网**——应是「网关不路由」；若误配成 permit 仅表示不验 JWT，仍须靠网络隔离 |

公开路径必须在配置中**显式列表**，禁止「默认全放行」进入 `test`/生产。

### 4.6 链路、日志与监控

| 项 | 约定 |
|----|------|
| 追踪 | Micrometer Tracing（Brave / OTel 桥）；网关向下游透传 `traceparent`（或 B3） |
| 日志 | 应用访问日志含 traceId；禁止业务旁路日志 |
| 指标 | Actuator：`health,info,metrics`（按需裁剪暴露面） |
| 请求标识 | 可补 `X-Request-Id`（若客户端未带）供幂等与排障 |

---

## 5. 配置契约

三份 yml（`CLAUDE.md` §6.10）：`application.yml` / `application-local.yml` / `application-test.yml`。  
**本地默认 `local`；联调切 `test`。生产敏感项走 Nacos / 密钥管理。**

### 5.1 建议配置键（`kebab-case`，`@ConfigurationProperties`）

```yaml
{system}-gateway:
  security:
    local-pass-through: false      # 仅 local 可 true；test/生产必须 false
    jwk-set-uri: ""                # 指向认证中心 JWKS（test/生产必填）
    public-paths:
      - /actuator/health
    trusted-origins:
      - http://localhost:5173
```

| 键 | local | test / 生产 |
|----|-------|-------------|
| `local-pass-through` | 可 `true`（联调） | **必须 `false`** |
| `jwk-set-uri` | 可空（配合 pass-through） | **必填**（认证中心 JWKS） |
| `trusted-origins` | 本地前端 origin | 测试/生产域名（登记 `docs/test-env.md`） |
| `public-paths` | 最小集 | 最小集；禁止宽放行 |

连接信息真相源：`docs/test-env.md`；端口登记：`docs/port-registry.md`（系统网关段 **8170–8199**）。

### 5.2 配置项绑定

- 统一 `GatewaySecurityProperties`（或等价）+ `@ConfigurationProperties`
- **禁止**散落 `@Value`；**禁止**把主机/账号密码写入公共 `application.yml`

---

## 6. HTTP 与错误语义（网关侧）

对齐 `CLAUDE.md` §5.3：

| 场景 | HTTP | `code` 语义 | 前端 |
|------|------|-------------|------|
| 未登录 / Token 无效 | 401 | 系统段 `1xxxx`「未认证」 | 跳转登录 |
| 无权限（路由拒绝） | 403 | 系统段「无权限」 | 提示 |
| 业务失败 / 校验失败 | 200 | `2xxxxx` / `3xxxx` | 展示 `msg`（由业务服务产生） |
| 限流 | 429 | 系统段「请求过于频繁」 | 稍后重试（**主路径在业务服务**） |
| 服务端错误 / 下游不可用 | 5xx | 系统段「系统异常」 | 通用错误；不暴露堆栈 |

网关错误体仍用 `{ code, msg, data }`（结构在 `{system}-common`）；**不向前端返回堆栈**。

---

## 7. 明确不放进网关

| 项 | 原因 |
|----|------|
| 业务 if/规则、组织数据权限 | 边界：业务服务 |
| `@RateLimit` 限流主路径 | 业务服务 + Redisson `RRateLimiter`；网关限流易成单点且与幂等/用户维度难对齐 |
| 写幂等 `@Idempotent` | 业务写语义；网关只透传 `Idempotent-Key` |
| Sentinel / Gateway `RequestRateLimiter` 作主限流 | `CLAUDE.md` §2.2 / §6.11 明确禁止 |
| 自建 JWT 业务解析供业务使用 | 身份只经 `X-User-*`；业务不绑 JWT 库 |
| 熔断器组件（Resilience4j 等） | 暂不引入（§2.2）；先保证超时与重试策略 |
| 静态资源、TLS | Nginx |
| 登录页、账密校验、发码换票 | 认证中心 |

---

## 8. 形态偏差（无网关系统）

| 形态 | 谁 | 偏差登记 |
|------|----|----------|
| 认证中心单体 | `auth-service` | 无网关、无 Nacos；Nginx 直反；CORS 自配；鉴权单层（以验签 `sub` 为准，不信客户端 `X-User-*`） |
| 单体业务模板 | `example-mono` | 同上收窄；见 `example-mono-design.md` §9 |

**纪律：** 偏差必须在对应系统设计文档「决策/偏差」表登记；**不得**把偏差扩成其他系统的默认架构。

---

## 9. 硬约束与避坑清单

摘自 `CLAUDE.md` 与 `fixbug/2026-09-25-unify-login-sso-gateway.md`（新建网关逐项核对）：

1. **`lb://` 必须显式依赖 `spring-cloud-starter-loadbalancer`**——否则 Nacos 有实例仍 503。
2. **必须从 Bearer JWT 注入 `X-User-*`**（`sub`→id）；只认 `X-Dev-*` 会导致 `/me/*` 空、admin 全量失效。
3. **客户端 `X-User-*` 一律剥除**，禁止信任可伪造头。
4. **CORS 只配在网关**；信任域名显式白名单并登记。
5. **`local-pass-through` 不得进入 test/生产默认配置。**
6. **`/internal/**` 不对公网路由**；对内接口靠网络隔离 + 网关不暴露。
7. **网关不引 SAS**；验签用 JWKS 指向认证中心。
8. **业务服务不装 Spring Security 做用户鉴权**（纯网关）；`auth-service` 例外除外。
9. **鉴权与过滤器顺序**：身份注入过滤器须在路由转发前完成剥除/写入（通常 `Ordered.HIGHEST_PRECEDENCE` 邻近优先级）。
10. **OIDC/SSO 相关**（若本网关保护 OIDC RP 的业务 API）：Cookie host 与 issuer 一致；SPA 调 `/oauth2/token` 的 CORS 在 **AS 安全链**开启（不只是网关）；详见 fixbug §9。

---

## 10. 新建系统落地检查清单

**设计期**

- [ ] 在系统设计文档中明确：一系统一网关（或登记合并/豁免偏差）
- [ ] 服务列表、`lb://` 目标随变更确定；端口写入 `docs/port-registry.md`
- [ ] 信任域名写入 `docs/test-env.md`
- [ ] JWKS 指向认证中心；确认 AT 的 `sub` 语义（= 用户主键字符串化）

**工程期**

- [ ] 模块：`{system}-gateway`（只依赖 common）
- [ ] 依赖：gateway + **loadbalancer** + nacos-discovery + oauth2-resource-server + actuator
- [ ] 三份 yml；`local-pass-through` 仅 local
- [ ] `UserHeaderFilter`（或等价）：剥除 → JWT 解析 → 注入
- [ ] CORS 白名单；`public-paths` 最小集
- [ ] 不路由 `/internal/**`
- [ ] Actuator 健康检查可用

**联调期**

- [ ] 经网关访问业务 `/api/**` 非 503（LoadBalancer 齐全）
- [ ] 带 AT：业务侧能读到真实 `X-User-Id`（非 0）
- [ ] 伪造客户端 `X-User-Id` 被覆盖/剥除
- [ ] 无 Token：401；公开路径：放行
- [ ] 跨域：仅信任域名成功，其他 origin 拒绝
- [ ] `local-pass-through=false` 时行为符合 test 预期

---

## 11. 关键设计决策

| # | 决策 | 说明 / 备选否决 |
|---|------|----------------|
| 1 | **薄边缘治理**：路由 + 验签 + 注入 + CORS | 已裁决；备选「网关承载业务 BFF / 权限中心」否决——与纯网关鉴权及服务自治冲突 |
| 2 | **纯网关鉴权**（业务不验用户、不 `@PreAuthorize`） | 已裁决（`CLAUDE.md` §5.2）；细粒度权限码属业务软校验/按钮显隐 |
| 3 | **一系统一网关** | 多系统容器边界清晰；合并网关须另开变更 |
| 4 | **JWKS 验签、不引 SAS** | 认证中心是唯一发行方；网关只做资源侧 |
| 5 | **限流主路径在业务服务** | 网关只保留超时；避免网关成为限流单点与语义混乱 |
| 6 | **`local-pass-through` 仅限 local** | 联调便利不得进入 test/生产（实测踩坑） |
| 7 | **显式依赖 LoadBalancer** | 实测：缺依赖时 Nacos 健康仍 503 |
| 8 | **CORS 唯一归属网关** | 避免 Nginx/业务多头配置；无网关系统偏差须登记 |

---

## 12. 相关文档

| 文档 | 内容 |
|------|------|
| `CLAUDE.md` | 全局契约（§5.1 链路、§5.2 职责/鉴权、§5.3 错误分流、§6.10 配置、§6.11 横切） |
| `example-system-design.md` | 完整业务系统模板（含 gateway 模块表） |
| `user-center-design.md` | 用户中心（`user-gateway` 落地参照） |
| `unified-auth-center-design.md` | 认证中心（JWKS 指向方；无网关例外） |
| `example-mono-design.md` | 无网关单体偏差登记样例 |
| `docs/template/fixbug/2026-09-25-unify-login-sso-gateway.md` | 网关/SSO 实测缺陷与避坑 |
| `docs/version-baseline.md` | 组件版本基线（唯一取用处） |
| `docs/port-registry.md` | 端口登记（网关段 8170–8199） |
| `docs/test-env.md` | 组件接入、信任域名 |
| `docs/error-code-ranges.md` | 错误码系统号登记 |

---

> 维护约定：本文件为**模板**，稳定约定变更随 `CLAUDE.md` 对应章节一并修订；具体系统的服务名、端口、域名、密钥写入各系统设计与 `docs/*` 登记处，不写入本文件。
