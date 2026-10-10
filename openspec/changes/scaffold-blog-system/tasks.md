# Tasks: scaffold-blog-system

## 1. 工程骨架与版本基线

- [x] 1.1 新建 `backend/blog/` 父 POM 与五模块（`blog-common` / `blog-framework` / `blog-api` / `blog-service` / `blog-gateway`），父 POM `dependencyManagement` 按 `docs/version-baseline.md` 锁定，模块依赖不写版本号；`blog-gateway` 显式依赖 `spring-cloud-starter-loadbalancer`
- [x] 1.2 确认前端主框架版本已在 `docs/version-baseline.md` 登记（缺则先登记），初始化 `frontend/blog-admin/`（Vite + Vue3 + TS + Pinia + Element Plus），锁 `package-lock.json`
- [x] 1.3 各模块落包结构 `com.qjj.blog.{module}`（对齐 `CLAUDE.md` §5.6.2）；确认 `blog-common` 无 MP/Redis/数据源/Actuator/MQ/MinIO/JWT/Hutool，`blog-api` 无 Entity/Mapper/实现，`blog-gateway` 不依赖 `blog-framework`

## 2. common / framework 基础契约

- [x] 2.1 `blog-common`：统一返回体 `Result{code,msg,data}`、分页契约 `{records,total,size,current}`、业务/校验/系统异常、`BaseEnum`、常量与脱敏工具（零配置纯基础）
- [x] 2.2 `blog-common`：错误码对齐 `docs/error-code-ranges.md` 系统段 `1xxxx` / 校验段 `3xxxx` 固定码值；业务段待 4.1 登记后建 `BlogErrorCodeEnum`，禁止魔法字符串
- [x] 2.3 `blog-framework`：MyBatis-Plus / Redis+Redisson / OpenFeign / springdoc / Micrometer Tracing / MinIO 封装（契约 `object-storage-design.md` §3）统一装配；`@ConfigurationProperties`（kebab-case），禁止散落 `@Value`；上传策略（≤10MB、MIME 白名单、预签名 TTL）键名对齐设计稿 §5.3 / `object-storage-design.md` §6
- [x] 2.4 `blog-framework`：审计 5 字段 `MetaObjectHandler`、`@TableLogic`、Long→String、16 位发号 `IdGenerator`（`sys_sequence` 段式，段长默认 500，`Asia/Shanghai` 日切）
- [x] 2.5 `blog-framework`：`@RateLimit`（Redisson RRateLimiter，超限 429 + `10003`）、`@Idempotent`（`Idempotent-Key`/`X-Request-Id`，重复返回首次 `Result`）、全局异常（校验 `30001` 细节放 `data`）、Logback 含 traceId
- [x] 2.6 `mvn -q compile` 全绿（`backend/blog/`）

## 3. 数据库

- [x] 3.1 编写建表 SQL 落 `deploy/db/migration/blog_db/`：`sys_sequence` / `sys_file` / `blog_audit_log`（字段对齐 `docs/template/example-system-design.md` §6.2，表名按 blog 映射；审计 5 字段、16 位发号主键、`uk_/idx_` 命名、单表索引 ≤5；`sys_file` 删除语义=`deleted`）
- [x] 3.2 同目录落 RBAC 4 表 SQL（`sys_menu` / `sys_role` / `sys_user_role` / `sys_role_menu`，按 `rbac-design.md` §2，无 `system_code`）
- [ ] 3.3 测试环境建库 `blog_db`（utf8mb4）并**人工执行** SQL；生产执行前按 `CLAUDE.md` §7 征询（仅登记执行记录，不自动执行生产）

## 4. 错误码与对内契约（blog-internal-api）

- [x] 4.1 在 `docs/error-code-ranges.md` 登记 blog 业务错误码系统号（不预占，实施分配）
- [x] 4.2 `blog-api`：DTO/VO + Feign + Fallback（`GET /internal/files/{id}`、`GET /internal/ping`）；固定连接/读取超时；未命中/失败返回降级标记
- [x] 4.3 `blog-service` 实现对内接口（Controller → Service → Mapper，禁 Controller 直调 Mapper）；`blog-gateway` 不路由 `/internal/**`

## 5. RBAC（blog-rbac）

- [x] 5.1 `blog-service`：菜单树 CRUD（type 1/2/3/4，`permission` 首段=`blog` 应用层校验）+ 角色分页/CRUD + 角色-菜单授权；关键写 `@Idempotent` + 审计；排序白名单
- [x] 5.2 `blog-service`：用户-角色分配/回收（选人经用户中心 `user-api`，Feign 在事务外 + Fallback 不阻断主流程）+ `/me/menus` 与 `/me/permissions` 本库联查
- [x] 5.3 细粒度权限仅软校验/按钮显隐（**不用** `@PreAuthorize` 拦接口）；身份以网关注入 `X-User-*` 为准

## 6. 文件与审计（blog-file / blog-audit）

- [x] 6.1 `blog-service`：单文件上传（大小/MIME/UUID 文件名/元数据落 `sys_file`）+ 分页/详情 + 预签名 URL + 逻辑删除；`@RateLimit`（上传/列表/预签名）与 `@Idempotent`（删除等关键写）；策略按 `object-storage-design.md` §4
- [x] 6.2 `blog-service`：`blog_audit_log` 关键写审计（事务提交后或统一 AOP 二选一）+ 审计分页（action/时间筛选，`detail` 脱敏）
- [x] 6.3 登记接口权限码 `blog:resource:action`（与前端同串），管理 API 路径对齐设计稿 §5.2

## 7. 网关（blog-gateway，按 gateway-design.md 裁剪）

- [x] 7.1 路由 `Path=/api/**` → `lb://blog-service`；JWT 验签（JWKS → 认证中心，`iss`/`aud`）；`public-paths` 最小集；`local-pass-through` 仅 local 可 `true`，test/生产 `false`
- [x] 7.2 剥除/覆盖注入 `X-User-Id`/`X-User-Name`；CORS 唯一归属（信任域名登记 `docs/test-env.md`）；错误体/访问日志/超时按 `gateway-design.md` §4.7–§4.9；`/actuator/health` 可用

## 8. 配置与环境登记

- [x] 8.1 三份 yml（公共无主机/账密 + local + test）；配置键 `blog.idgen` / `blog.idempotent` / `blog.rate-limit` / `blog.minio` / `blog.feign` 对齐设计稿 §5.3；连接信息与 `docs/test-env.md` 一致，个人差异 `docs/test-env.local.md`
- [x] 8.2 端口登记 `docs/port-registry.md`；Nacos 注册/配置接线；本地默认 `local`、联调 `test` 可启动

## 9. 前端 blog-admin

- [x] 9.1 工程壳：`src/` 按 `CLAUDE.md` §5.5；axios 解包 `{code,msg,data}` 并按 §5.3 分流；请求只经 `src/api`；`id` 一律 `string`
- [x] 9.2 布局壳 + 菜单管理（树 CRUD）+ 角色管理（授权/用户-角色分配）；`/me/menus` 动态路由、`/me/permissions` 按钮显隐；禁硬编码角色
- [x] 9.3 文件管理页（对齐 `example-system-design.md` §7.1 与 `assets/rbac-admin-proto.html`：上传前端校验、关键字/MIME 筛选、分页、预签名预览/下载、逻辑删除）+ 审计日志页（筛选分页）
- [x] 9.4 壳阶段登录联调放行（local）；留 OIDC 接线注释（PKCE/同 host 等红线见设计稿 §7）；`npm run lint && npm run type-check` 全绿，无 `console.log` 残留

## 10. 联调自检与文档收尾

- [ ] 10.1 冒烟（对齐 design Migration Plan 与设计稿 §10）：RBAC CRUD+授权 → `/me/*` → 上传/预签名/删 → 幂等首次 Result → 限流 429/`10003` → Feign ping/files + Fallback；网关 `lb://` 非 503；勾选 `gateway-design.md` §10 工程期/联调期清单
- [x] 10.2 提交前自查（`CLAUDE.md` §3.2/§6.3）：`mvn -q compile`、`npm run lint && npm run type-check`；无调试残留、无生产密钥、版本只来自基线表；`deploy/nginx/`（或等价）登记静态托管与 `/api` 反代
