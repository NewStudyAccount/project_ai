# 2026-09-25 统一登录 / SSO / 网关缺陷记录

> 范围：unify-login-facade（唯一登录门面、OIDC RP、统一登出）、user-gateway 发现路由。  
> 原则：只记已复现并修掉的问题；验证方式可复跑。  
> 访问约定：浏览器统一 **`localhost`**（禁止与 `127.0.0.1` 混用，Cookie 按 host 隔离且忽略端口）。

---

## 1. 单点登录（SSO）失效

### 1.1 `localhost` / `127.0.0.1` 分裂导致 SSO Cookie 不生效

| 项 | 内容 |
|----|------|
| 现象 | 各端都要重新登录，「单点完全不起作用」 |
| 根因 | 前端 issuer 默认 `http://127.0.0.1:9080`，用户打开的是 `http://localhost:517x`；Cookie 以 host 为准（**忽略端口**），`127.0.0.1` ≠ `localhost`，authorize 收不到 `AUTH_SSO_SESSION` |
| 改动 | issuer / 默认 RP 统一 `http://localhost:9080`；文档强制「全链路 localhost」 |
| 验证 | 同一 CookieJar：`localhost:9080/oauth2/authorize` 直接发码；`127.0.0.1:9080` 401 |

### 1.2 `SsoSessionAuthFilter` 挂载过晚 → authorize 掉进 MVC 报 10004

| 项 | 内容 |
|----|------|
| 现象 | `{"code":10004,"msg":"系统异常"}`，日志 `No static resource oauth2/authorize` |
| 根因 | SAS `OAuth2AuthorizationEndpointFilter` 在资源所有者未认证时会 `chain.doFilter` **穿过**（源码约 181–193 行），期望后续 EntryPoint 拦截。SSO Filter 若挂在 `AnonymousAuthenticationFilter` **之前但仍在 authorize Filter 之后**，会在「已穿过」之后才恢复登录态，`AuthorizationFilter` 放行 → MVC 无 handler |
| 改动 | `SsoSessionAuthFilter` 改为 `addFilterAfter(..., SecurityContextHolderFilter.class)`，保证在 **authorize 之前** 恢复会话；两条安全链（AS + 默认）都挂 |
| 验证 | 有 Cookie：authorize 302 带 `code`；无 Cookie：302 到 portal（不再 10004） |

### 1.3 `formLogin` 覆盖 EntryPoint → `GET /login` 10004

| 项 | 内容 |
|----|------|
| 现象 | `No static resource login` → 10004 |
| 根因 | `exceptionHandling` 写在 `formLogin` **之前**，FormLogin 又把 EntryPoint 改回相对路径 `/login`；且 `GET 9080/login` 无页面 |
| 改动 | `exceptionHandling` **挪到 `formLogin` 之后**（Portal 登录门面 EntryPoint）；`LoginPortalRedirectController`：`GET /login` → 302 portal |
| 验证 | `GET /login` → `http://localhost:5174/login` |

---

## 2. 登录页死循环

### 2.1 Vite 代理 `/login` + 9080 再 302 回 portal → 重定向次数过多

| 项 | 内容 |
|----|------|
| 现象 | 从 `localhost:5173` 进入后在 5174 报「重定向次数过多」 |
| 根因 | `GET localhost:5174/login` 被 portal Vite **整段代理到 9080**；9080 的 `GET /login` 又 302 回 `5174/login`，形成环。**页面路由与验密接口同名** |
| 改动 | 路径拆分（方案 A）：`GET /login` 只属 portal SPA；验密改为 **`POST /api/login`**（`loginProcessingUrl`）；Vite **删除 `/login` 代理**（验密走既有 `/api` 代理） |
| 验证 | `GET /login` 出 Vue 表单；`POST /api/login` 302 + `AUTH_SSO_SESSION`；不再转圈 |

---

## 3. 回调「登录失败，正在重新跳转统一登录…」

### 3.1 `/oauth2/token` 跨域响应无 CORS 头 → 浏览器拦下换票结果

| 项 | 内容 |
|----|------|
| 现象 | 换票后一直「登录失败，正在重新跳转统一登录…」 |
| 根因 | `OPTIONS` 有 CORS，但 **SAS 过滤链上的 `POST /oauth2/token` 响应无 `Access-Control-Allow-Origin`**；fetch 被浏览器判失败（axios/fetch 错误进回调 catch） |
| 改动 | AS 链 `http.cors(corsConfigurationSource)`（与默认链同源）；回调只自动重试 1 次，再失败显示原因 + 按钮 |
| 验证 | `Origin: http://localhost:5173` 的 token 响应带 `ACAO` + `Allow-Credentials` |

---

## 4. 统一登出 / Refresh Token

### 4.1 `/connect/logout` 401（被 SAS end_session 占用）

| 项 | 内容 |
|----|------|
| 现象 | 统一登出接口 401 Bearer |
| 根因 | SAS 将 `/connect/logout` 注册为 OIDC `end_session_endpoint`，默认要求认证，业务 `@Controller` 进不去 |
| 改动 | `OidcLogoutFilter`（`HIGHEST_PRECEDENCE`）**独占** `/connect/logout`：清 SSO Cookie、按用户吊销 RT/grant、校验 `post_logout_redirect_uri` 白名单 |
| 验证 | 合法 post-logout 302；非法 URI 返回默认页不开放重定向；登出后 RT 刷新失败 |

### 4.2 登出后 RT 仍可刷新

| 项 | 内容 |
|----|------|
| 现象 | 统一登出后旧/新 RT 仍能换 AT |
| 根因 | 吊销只删了 RT 哈希键，**SAS `OAuth2Authorization` 仍留在 Redis**，`findByToken` 仍能装出有效授权 |
| 改动 | `saveRefreshToken` 记 `auth:grant-authorization:{bizGrantId}` → SAS authzId；`revokeGrant` 一并 `removeAuthorization` |
| 验证 | 登出后 refresh 失败；另一 client 的 RT 同步失效（全链） |

### 4.3 公开客户端 `refresh_token` 始终 `invalid_grant`

| 项 | 内容 |
|----|------|
| 现象 | 换票成功但刷新 400 `invalid_grant`（日志无 `findByToken`） |
| 根因 | SAS 默认 client 认证不认 `refresh_token + client_id`；client 未认证 → refresh converter 直接放弃 |
| 改动 | `PublicClientRefreshAuthenticationConverter` + `PublicClientRefreshAuthenticationProvider`（NONE 公开客户端） |
| 验证 | `grant_type=refresh_token` 返回新 AT/RT |

---

## 5. 用户中心业务 503（换票成功后）

### 5.1 `user-gateway` 缺 LoadBalancer → Nacos 有实例仍 503

| 项 | 内容 |
|----|------|
| 现象 | 登录成功后回调报 `Request failed with status code 503`（axios）；`GET /api/v1/me/menus` 经网关 503 |
| 根因 | 路由 `uri: lb://user-service` 需要 **Spring Cloud LoadBalancer**；`spring-cloud-starter-gateway 4.3.5` 不再传递该依赖。Nacos 里 `user-service` 健康，**直连 18173 为 200**，经 8173 为 503 |
| 改动 | `user-gateway/pom.xml` 显式加入 `spring-cloud-starter-loadbalancer` |
| 验证 | 重启 user-gateway 后 `/api/v1/me/menus` 不再 503（业务数据为空属 RBAC 另题） |

| 易混淆 | 说明 |
|--------|------|
| 不是 | user-admin 前端 `auth.init()` 业务逻辑写错 |
| 不是 | MeController 缺接口（直连已 200） |
| 是 | 网关 `lb://` 无 LoadBalancer / 发现失败 |

---

## 6. 管理 API 恒 `10002 无权限`（OIDC 之后）

| 项 | 内容 |
|----|------|
| 现象 | auth-admin 调 `/api/v1/**` 带 AT 仍 `{"code":10002,"msg":"无权限"}` |
| 根因 | `@PreAuthorize("hasAuthority('auth:…')")` 要 authorities；JWT **Claims 最小集不含权限码**（设计约束），默认 converter 只有 `SCOPE_*`；SSO 恢复的 `AuthPrincipal` 也是空 permissions |
| 改动 | ① `RbacJwtAuthenticationConverter`：JWT 验签后按 `sub` 调 `permissionsForUser` 装载 authorities；② `SsoSessionAuthFilter` 恢复会话时同样从 RBAC 装载 |
| 验证 | 带 AT/SSO 调 `auth:menu:list` 等接口 200；无权限用户 10002 |

---

## 7. RBAC 菜单/权限接口空、动态路由无组件

### 6.1 `/me/menus`、`/me/permissions` 恒为空

| 项 | 内容 |
|----|------|
| 现象 | `GET /api/v1/me/menus`、`/me/permissions` 返回空数组（有种子、有角色绑定） |
| 根因 | `AuthContextFilter` 只认 `X-User-Id` 与 formLogin 的 `AuthPrincipal`，**不解析 JWT**。OIDC Bearer 下 `SecurityContext` 是 `JwtAuthenticationToken`（`sub`=用户 id），`AuthContext.userIdOrSystem()`=**0** → `myMenuList()` 直接 `List.of()` |
| 改动 | `AuthContextFilter.fillFromSecurityContext` 增加 JWT：`getSubject()` / `claims.sub` → userId，`preferred_username` → userName（反射，framework 不绑 oauth2 类） |
| 验证 | 带 AT 调 `/me/permissions` 返回非空 `auth:*`；`/me/menus` 含 type 1/2 树 |

### 6.2 `sys_menu.component` 为空导致动态路由挂不上

| 项 | 内容 |
|----|------|
| 现象 | 菜单接口有数据时前端仍无页面（或 fallback 到 Dashboard） |
| 根因 | 冒烟种子 `component` 全是 `''`；`auth-admin` 的 `componentByPath` 需要 `views/client/ClientList.vue` 等键 |
| 改动 | seed 中 type=2 填入与 `frontend/auth-admin/src/router/index.ts` 一致的 component；并提供按 id 的 `UPDATE`（`INSERT IGNORE` 不更新旧行） |
| 验证 | `sys_menu` type=2 的 component 非空且命中前端 map |

| 避坑 | |
|------|--|
| `/me/*` 空 | 先查 **AuthContext.userId** 是否为真实 user.id，再查 RBAC 数据 |
| 菜单有数据无页面 | 对照前端 `componentByPath` 核对 **component** 字符串 |

---

## 8. 用户中心 admin 全量不生效 / `/me/*` 空（经网关）

| 项 | 内容 |
|----|------|
| 现象 | 已实现 `role_code=admin` 全量，经 user-gateway 仍空权限 |
| 根因 | **网关 `UserHeaderFilter` 不解析 Bearer JWT**；`local-pass-through` 只认 `X-Dev-*`，浏览器不带 → 业务 `UserContext.userId=0` → 跳过 admin 分支 |
| 证据 | 直连 `user-service` + `X-User-Id=…0001` 返回全量 `user:*`；无头/仅 `X-Dev-*` 为空 |
| 改动 | 网关从 `Authorization: Bearer` 解析 `sub`/`preferred_username` 注入 `X-User-*`；local 再退 `X-Dev-*`；客户端同名头一律剥除 |
| 验证 | 带 AT 经网关 `/api/v1/me/permissions` 返回全量；admin 新建菜单无需绑表即可见 |

---

## 9. 避坑清单（后续接入新 RP / 新系统）

1. **IdP、portal、各 RP 的 origin 必须同一 host**（本地一律 `localhost`，不要混 `127.0.0.1`）。  
2. **页面路由与验密接口禁止同名同方法**（`GET /login` 页面 vs `POST /api/login` 验密）。  
3. **SPA 调 `/oauth2/token` 必须在 AS 安全链上开 CORS**，不能只配默认链。  
4. **会话恢复 Filter 必须早于 SAS authorize Filter**（`SecurityContextHolderFilter` 之后）。  
5. **`exceptionHandling` 写在 `formLogin` 之后**，否则 EntryPoint 被覆盖。  
6. **`lb://` 服务必须带 `spring-cloud-starter-loadbalancer`**。  
7. **吊销 RT 时必须删 SAS `OAuth2Authorization`**，否则刷新仍成功。  
8. **统一登出用 Filter 抢占 `/connect/logout`**，勿与 SAS end_session 抢 Controller。
9. **AuthContext 必须同时认 JWT `sub` 与网关 `X-User-*`**，否则 `/me/*` 按 user_id=0 查空。
10. **`sys_menu.component` 必须与前端动态路由 map 的键完全一致**（如 `views/menu/MenuList.vue`）。
11. **`@PreAuthorize` 的 authorities 必须在验签/恢复会话时从 RBAC 装载**；禁止假设 JWT 内嵌权限码（Claims 最小集）。
12. **网关必须从 Bearer JWT 注入 `X-User-*`**（`sub`→id）；只认 `X-Dev-*` 会导致 admin 全量与 `/me/*` 失效。
