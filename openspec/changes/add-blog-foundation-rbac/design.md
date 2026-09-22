# Design: add-blog-foundation-rbac

## Context

- 统一认证与 14 位 ID/审计规范已定；权限**各系统自管**。
- blog：`system_code=blog`；匿名前台 + 后台登录管理。
- **各系统自持 `{system}-common`**；blog **不依赖** `platform-common`（JWT 验签在 blog 内自实现，配置与 auth 同 `iss`/密钥）。

## Goals / Non-Goals

**Goals:** `blog-common` 基础契约（§6.4）、骨架、网关 AuthN、本地 RBAC、user_ref、权限码鉴权、**admin/portal 前端壳与 RBAC 管理页骨架**。  
**Non-Goals:** 文章业务 UI/评论 UI、file 上传实现、数据范围过滤引擎、跨系统共享 common、md-editor 接入。

## Decisions

### D1. 工程与 blog-common

```
backend/blog/
  blog-common/            §6.4 基础契约 + 验签 + 权限注解（不依赖 platform-common）
  blog-gateway/           仅 JWT → X-User-Id
  blog-content-service/   首期唯一业务进程
```

`blog-common` 包结构（业务模块只准引用，禁止再自建）：

| 包 | 类型 / 职责 |
|----|-------------|
| `result` | `Result` `{code,msg,data}`；`PageResult` `{records,total,size,current}` |
| `error` | 错误码枚举（1xxxx/2xxxx/3xxxx）；`BizException` |
| `exception` | `GlobalExceptionHandler`（`@RestControllerAdvice`） |
| `page` | `PageQuery`（current/size）；`orderBy`/`order` 白名单 |
| `audit` | `BaseEntity`；`AuditMetaObjectHandler`；`CurrentUser`；`IdService`（14 位）+ `SysSequence` |
| `json` | Long→String `Jackson2ObjectMapperBuilderCustomizer` |
| `auth` | `UserHeaders`；`JwtVerifier`；`@RequiresPermission` + 切面；`PermissionChecker`；权限码常量 |
| `swagger` | springdoc 公共配置 |

**不进 common：** 业务规则、OSS、数据范围引擎、具体 API。

依赖方向：`blog-gateway` / `blog-content-service` → `blog-common` → Spring/jjwt/MyBatis-Plus/validation。无 `platform-common`。

### D2. RBAC 本地

表同基础 schema；`user_id` 逻辑指向认证中心；关联物理删；`data_scope` 预留。

### D3. 鉴权

网关 AuthN（自实现 JWT，同 auth `iss`）；服务 `@RequiresPermission`；`/api/v1/public/**` 放行清单；无权限 403 语义。

**统一认证 / SSO（强制，对齐 CLAUDE.md 与 unified-auth）：**

```
未登录访问 blog-admin
  → 302 GET /auth/sso/authorize?client_id=blog-admin&return_url={origin}/sso/callback
  → 无 sid：跳 auth-portal 登录页（不在 blog 自建账密页）
  → 有 sid：回跳 return_url?code=xxx
  → blog-admin SsoCallback：POST /auth/sso/token {code, clientId}
  → 存 JWT，业务请求 Bearer 调 /api/**
```

| 约束 | 内容 |
|------|------|
| blog-admin | **禁止**本地账密登录表单；只走统一认证 SSO |
| client_id | `blog-admin` / `blog-portal`（return_url 进 auth 白名单） |
| 令牌 | Access 30m + Refresh 7d（Redis 可吊销，与 auth 策略一致） |
| sid | 仅存 auth Redis；blog 不持有密码、不重建会话 |

### D4. 投影

进入 blog upsert `sys_user_ref`；展示 batch 补洞；**不用** ref.status 放行。

### D6. Redis（对齐 CLAUDE.md 技术栈）

| 用途 | 键 | 模块 |
|------|----|------|
| 权限码缓存 | `blog:perm:{userId}`，TTL 如 300s，赋权后失效 | blog-content-service |
| （预留）JWT 黑名单 | `blog:bl:access:{jti}` | blog-gateway |

blog-content-service 引入 `spring-boot-starter-data-redis`；**禁止**用 Redis 存密码/生产密钥。

### D5. 前端工程（blog-admin / blog-portal）

对齐 `CLAUDE.md` §5.5：组合式 API、Pinia、业务组件不直调 axios、Element Plus（admin）、路由 meta、权限码与后端一致。

```
frontend/blog-admin/                 # 后台（需登录）
  src/
    api/          # http.ts 统一 axios 解包 Result；rbac.ts / user.ts
    views/
      layout/     # 侧栏 + 顶栏壳
      rbac/       # 角色、权限、用户-角色（本变更骨架）
    components/   # layout / common
    stores/       # user.ts（Pinia：uid/username/权限码）
    router/       # meta: title / icon / hidden / requiresAuth
    types/        # ApiResult / PageResult / RBAC 模型；id 一律 string
    utils/        # token.ts（对齐 auth-portal：access 内存+session，refresh session）
    styles/
  index.html · vite.config.ts · package.json · tsconfig*.json

frontend/blog-portal/                # 匿名前台
  src/
    api/          # http.ts + public.ts（/api/v1/public/**）
    views/        # 首页壳（文章列表在 content-modules）
    router/       # 公开路由，requiresAuth=false
    stores/ types/ utils/ styles/
```

| 决策 | 内容 |
|------|------|
| 登录 | **统一认证 SSO only**：`/auth/sso/authorize` + `/auth/sso/token`；**无** blog 自建账密页 |
| 令牌 | 与 auth-portal 同策略；业务请求带 `Authorization: Bearer` |
| 网关 | dev：`/api` → blog-gateway:8083；`/auth` → auth:8081 |
| 返回体 | axios 拦截器解包 `{code,msg,data}`；分页 `{records,total,size,current}` |
| 主键 | 前端类型 **string**，对接 Long→String |
| 权限 | 路由 `meta.requiresAuth` + 按钮 `v-permission="blog:rbac:role"` 等，码表同 `BlogPermissions` |
| admin 本变更范围 | 布局壳 + RBAC 三页骨架（列表/赋权入口）；**不含**文章编辑器 |
| portal 本变更范围 | 工程壳 + 首页占位；公开 API 可通 |

## Risks

- [权限码漂移] → blog-common 常量统一。  
- [公开写滥用] → 写接口强制 JWT；评论限流在业务变更。  
- [common 膨胀] → 禁止业务规则下沉；变更影响全部业务模块须走变更流程。

## Migration Plan

1. 建 `blog-common` 契约与 Maven 模块。 2. 网关验签与 RBAC 表。 3. 配置 JWT（同 iss）。 4. 种子角色权限。 5. frontend 双壳与 RBAC 页骨架。

## Open Questions

1. 内置角色命名（blog_admin / blog_author）。 2. 匿名评论策略细节。
