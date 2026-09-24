# 2026-09-24 启动 / 鉴权 / OIDC 缺陷记录

> 范围：user-center、unified-auth-center。  
> 原则：只记已复现并修掉的问题；验证方式可复跑。

---

## 1. 启动与中间件

### 1.1 Nacos 注册失败 `ErrCode:-401, Client not connected`

| 项 | 内容 |
|----|------|
| 现象 | user-service 启动注册 Nacos 失败，随后 context cancel |
| 根因 | `application-local.yml` 未配置 `spring.cloud.nacos.discovery.server-addr`，客户端默认打 `127.0.0.1:8848`（本机未监听） |
| 改动 | local/test 配置为 `192.168.99.100:8848`（与 `docs/test-env.md` 一致） |
| 验证 | TCP 8848/9848 通；readiness OK；服务注册成功 |

### 1.2 Nacos `ErrCode:403, user not found!`

| 项 | 内容 |
|----|------|
| 现象 | 地址正确后注册仍 403 |
| 根因 | Nacos 开了鉴权，客户端未带账号密码 |
| 改动 | `username/password: nacos`（测试账密见 test-env） |
| 验证 | `/nacos/v1/auth/login` 返回 token；注册成功 |

### 1.3 auth-service 误配 Nacos

| 项 | 内容 |
|----|------|
| 根因 | 认证中心是**无网关单体**，按设计不引入 Nacos |
| 改动 | 去掉 auth 的 `spring.cloud.nacos.*`；Feign 改直连 `user.feign.user-center.base-url` |

---

## 2. 编译期

### 2.1 `isRotated(String)` 方法重复定义

| 项 | 内容 |
|----|------|
| 现象 | `已在类 TokenManagementService 中定义了方法 isRotated(java.lang.String)` |
| 根因 | 公开方法收**原始 RT**，私有方法收 **hash**，Java 不区分语义 |
| 改动 | 私有方法改名 `isRotatedHash` |
| 验证 | `mvn -q compile` 通过 |

### 2.2 `OAuth2Authorization.getToken` 类型不兼容

| 项 | 内容 |
|----|------|
| 现象 | `Token<T>` 与 `OAuth2RefreshToken` 不兼容 |
| 根因 | `getToken(Class)` 返回的是包装类型 `Token<T>`，不是 T 本体 |
| 改动 | `authorization.getRefreshToken()` / `token.getToken()` |

### 2.3 源文件 `ackage` / UTF-8 BOM

| 项 | 内容 |
|----|------|
| 现象 | `非法字符: '\ufeff'` 或 `需要 class、interface...` |
| 根因 | 脚本写文件引入 BOM，或剥 BOM 时误删首字母 `p` |
| 改动 | 无 BOM UTF-8 重写；`package` 声明补全 |

---

## 3. 鉴权模型（已裁决：纯网关鉴权）

### 3.1 业务服务挂 Spring Security 导致 `/internal` 401、打印 generated password

| 项 | 内容 |
|----|------|
| 现象 | `/internal/**` 401；启动日志 `Using generated security password: ...` |
| 根因 | 旧「双重鉴权」引入 `spring-boot-starter-security`；classpath 有 Security 即默认登录墙 |
| 改动 | `CLAUDE.md` §5.2 改为**纯网关鉴权**；`user-framework` 去掉 starter-security；删除 `UserPermissionChecker` 与全部 `@PreAuthorize` |
| 边界 | **网关**与 **auth-service** 保留 Security；业务服务只读 `X-User-*` |
| 验证 | 命令行 `spring-boot:run` 无密码告警；`/internal` 可访问 |

### 3.2 IDE 仍出现 generated password

| 项 | 内容 |
|----|------|
| 根因 | IntelliJ 未 Reload Maven，运行时 classpath 仍旧 |
| 处理 | Maven Reload → Rebuild → 重启；依赖树仅剩 `spring-security-crypto`（Nacos 传递） |

### 3.3 端口无统一登记

| 项 | 内容 |
|----|------|
| 改动 | 新建 `docs/port-registry.md`（唯一端口登记处）；`test-env.md` 只留接入摘要 |

---

## 4. MyBatis

### 4.1 `BindingException: Invalid bound statement ... UserService.getByUsername`

| 项 | 内容 |
|----|------|
| 现象 | 调 Service 方法报 Mapper 未绑定 SQL |
| 根因 | `@MapperScan("com.qjj.user")` / `com.qjj.auth` **过宽**，把 Service/Feign 接口注册成 Mapper |
| 改动 | 收窄为 `com.qjj.user.service.mapper`、`com.qjj.auth.service.mapper` |
| 口诀 | `@MapperScan` **只扫 mapper 包**，禁止扫 service |

---

## 5. user-api / internal

### 5.1 `/internal/users/**` 500（`code:10004`）

| 项 | 内容 |
|----|------|
| 现象 | 系统异常壳子，无业务码 |
| 根因 | `BizException` 被包装后落入 `Exception` 处理器；profile 行缺失；`by-username` 查无用户直接抛错 |
| 改动 | 全局异常**解包 cause 链**；profile 可缺省；无用户返回空 VO |
| 验证 | `by-username/admin` → `id/status`；非法 id → `201002` |

### 5.2 Feign 直连端口

| 项 | 内容 |
|----|------|
| 改动 | `UserQueryClient` 使用 `url=${user.feign.user-center.base-url}`；local 指向 `18173` |

---

## 6. OIDC / Token（按真实报错）

### 6.1 `NumberFormatException: For input string: "admin"`

| 项 | 内容 |
|----|------|
| 堆栈 | `TokenManagementService.upsertGrant` ← `saveRefreshToken` ← `RedisOAuth2AuthorizationService.save` |
| 根因 | SAS `getPrincipalName()` 是**登录名**，被 `Long.valueOf` 当成 `userId` |
| 改动 | `AuthPrincipal.getUsername()` 返回 `user.id` 字符串；`displayName()` 供 `preferred_username` |
| 验证 | TOKEN 200；JWT `sub=2026092400000001`，`preferred_username=admin` |

### 6.2 `/oauth2/token` 只有 500 壳子

| 项 | 内容 |
|----|------|
| 根因 | 异常在 SAS **过滤器链**，不走 `@RestControllerAdvice` |
| 改动 | local 打开 `server.error.include-stacktrace=always` 等（排障用） |

### 6.3 `preferred_username` 变成 Authentication#toString

| 项 | 内容 |
|----|------|
| 根因 | `JwtEncodingContext.getPrincipal()` 可能是 `Authentication` 而非 `AuthPrincipal` |
| 改动 | 先 `authentication.getPrincipal()` 再取 `displayName()` |

### 6.4 授权码 UUID 被当 Long 主键

| 项 | 内容 |
|----|------|
| 根因 | SAS `authorizationId` 多为 UUID，`upsertGrant` 里 `Long.valueOf` |
| 改动 | `resolveBizGrantId`：非数字则映射为 16 位业务 id，Redis 与 `auth_grant` 同键 |

### 6.5 `REFRESH → invalid_client`

| 项 | 内容 |
|----|------|
| 根因 | SAS 1.2 `PublicClientAuthenticationConverter` **强制 PKCE `code_verifier`**，刷新只带 `client_id` 时认证失败 |
| 改动 | `PublicClientRefreshAuthenticationConverter` 放行 `grant_type=refresh_token` + `client_id` |

### 6.6 `invalid_grant`（findByToken 查不到）

| 项 | 内容 |
|----|------|
| 根因 | 授权对象只靠进程内 `InMemory`，且索引不全 / 响应 RT 与入库 RT 可能不一致 |
| 改动 | `OAuth2Authorization` 序列化入 Redis；索引 code/AT/RT/id_token（type + any）；`sha256(RT)→authorizationId` 兜底；`PublicClientRefreshTokenGenerator` **同事件幂等** |
| 状态 | TOKEN/userinfo 已通；**刷新轮转仍见 invalid_grant**，需用 `auth-find.log`（save/findByToken 实据）收口 |

### 6.7 非法 `redirect_uri` 302 到登录

| 项 | 内容 |
|----|------|
| 根因 | `OidcRequestValidationFilter` 只检查参数是否存在 |
| 改动 | 注入 `RegisteredClientRepository`，**精确匹配** redirect_uri 白名单，失败 400 |
| 验证 | `http://evil.example/cb` → 400 |

---

## 7. 权限 / 会话

### 7.1 登录后管理接口 403、`/me/*` 为空

| 项 | 内容 |
|----|------|
| 根因 | `AuthPrincipal.getAuthorities()` 恒空；`AuthContext` 只认 `X-User-Id` 头，formLogin 会话无此头 |
| 改动 | 登录时 `RbacService.permissionsForUser` 装入 authorities；`AuthContextFilter` 从 SecurityContext 回填 userId |
| 验证 | `/me/permissions` 返回 `auth:*` 列表；`/api/v1/clients` 200 |

---

## 8. 通用避坑（摘录）

1. **先拿堆栈再改代码**（`include-stacktrace` 或服务日志），禁止只靠读代码猜。
2. `@MapperScan` 只扫 `mapper` 包。
3. SAS 的 `principalName` / `authorizationId` **不能假设是 Long 业务主键**。
4. Public 客户端在 SAS 1.2 上 **refresh 与 PKCE 认证路径不一致**，需单独转换器。
5. `OAuth2AuthorizationService` 落 Redis 时必须给**所有 token 类型**建索引，并保证「响应里的 token」与「save 进去的 token」是**同一个实例/同一值**。
6. 业务微服务默认**不**引 Spring Security（纯网关鉴权）；网关与认证中心除外。
