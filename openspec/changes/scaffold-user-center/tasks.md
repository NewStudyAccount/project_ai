# Tasks: scaffold-user-center

## 1. 工程骨架与版本基线

- [x] 1.1 新建 `backend/user/` 父 POM 与五模块（`user-common` / `user-framework` / `user-api` / `user-service` / `user-gateway`），父 POM `dependencyManagement` 按 `docs/version-baseline.md` 锁定，模块依赖不写版本号
- [x] 1.2 在 `docs/version-baseline.md` 登记前端主框架版本（Vue3 / Vite / Element Plus / Pinia 等），再初始化 `frontend/user-admin/`（Vite + Vue3 + TS + Pinia + Element Plus），锁 `package-lock.json`
- [x] 1.3 各模块落包结构 `com.qjj.user.{module}`（controller/dto/vo/service/bo/converter/mapper/entity/feign/config/constants/enums/util，对齐 `CLAUDE.md` §5.6.2）；确认 `user-common` 无 MP/Redis/数据源/Actuator/MQ/MinIO/JWT/Hutool 依赖，`user-api` 无 Entity/Mapper/实现

## 2. common / framework 基础契约

- [x] 2.1 `user-common`：统一返回体 `Result{code,msg,data}`、分页契约 `{records,total,size,current}`、业务/校验/系统异常类型、`BaseEnum`、常量与脱敏工具（纯零配置）
- [x] 2.2 `user-common`：错误码体系（系统段 `1xxxx` / 校验段 `3xxxx` 对齐 `docs/error-code-ranges.md` 固定码值；业务段先完成 4.1 登记再建 `UserErrorCodeEnum`），禁止魔法字符串
- [x] 2.3 `user-framework`：MyBatis-Plus / Redis+Redisson / OpenFeign / springdoc / Micrometer Tracing 统一装配；配置绑定用 `@ConfigurationProperties`（kebab-case），禁止散落 `@Value`
- [x] 2.4 `user-framework`：审计 5 字段自动填充（`MetaObjectHandler`）、`@TableLogic` 逻辑删除、`Long→String` 全局序列化、16 位发号 `IdGenerator`（`sys_sequence` 段式取号，步长 500，`Asia/Shanghai` 日切）
- [x] 2.5 `user-framework`：`@RateLimit`（Redisson RRateLimiter，超限 HTTP 429 + `10003`）与 `@Idempotent`（`Idempotent-Key`/`X-Request-Id` + Redis 占位，重复返回首次 `Result`）AOP 实现；全局异常处理（`@RestControllerAdvice`，校验失败 `30001` 字段细节放 `data`，不泄堆栈）；Logback 按天滚动含 traceId
- [x] 2.6 `mvn -q compile` 全绿（`backend/user/`）

## 3. 数据库

- [x] 3.1 编写建表 SQL 落 `deploy/db/migration/user_db/`：`sys_sequence` / `sys_user` / `sys_user_profile` / `user_audit_log`（字段对齐 `docs/template/user-center-design.md` §6.2；含审计 5 字段、16 位发号主键、`uk_/idx_` 索引命名、单表索引 ≤5；`sys_user` 无任何凭证字段）
- [x] 3.2 同目录落 RBAC 4 表 SQL（`sys_menu` / `sys_role` / `sys_user_role` / `sys_role_menu`，按 `rbac-design.md` §2，无 `system_code`）
- [ ] 3.3 测试环境建库 `user_db`（utf8mb4）并**人工执行** SQL；生产执行前按 `CLAUDE.md` §7 征询（本任务仅登记执行记录，不自动执行生产）

## 4. 对内查询契约（user-api + user-query 能力）

- [x] 4.1 在 `docs/error-code-ranges.md` 登记本系统业务错误码系统号
- [x] 4.2 `user-api`：DTO/VO + Feign + Fallback（GET `/users/{id}`、`/users/by-username/{username}`、`/users/{id}/profile`）；固定连接/读取超时；profile 出参脱敏
- [x] 4.3 `user-service` 实现对内查询接口（Controller → Service → Mapper，禁 Controller 直调 Mapper；仅服务间可达，网关/Nginx 不对外路由）；`user-gateway` 不转发对内前缀（口径按 design Open Question 定案并回填）

## 5. 账号管理（user-account 能力）

- [x] 5.1 `user-service`：账号创建（16 位发号、username 唯一、初始状态正常、禁凭证字段）+ 资料维护（`sys_user` / `sys_user_profile` 1:1，出参手机/邮箱脱敏）+ Bean Validation
- [x] 5.2 `user-service`：启停/锁定（`status` 1/0/2）+ 分页列表（`current`/`size`、username/status 筛选、排序白名单、禁 `SELECT *`）
- [x] 5.3 `user-service`：管理审计（`user_audit_log`：创建/更新/状态变更/RBAC 变更留痕，操作人=网关注入值，`detail` 禁密码/完整令牌）；关键写接口挂 `@Idempotent`

## 6. 本系统 RBAC（user-rbac 能力）

- [x] 6.1 `user-service`：菜单树 CRUD（type 1/2/3/4；`permission` 首段=`user` 且系统内唯一应用层校验；逻辑删除处理子节点策略按 design 定案）+ 角色管理（`role_code` 唯一、`data_scope` 枚举）+ 角色-菜单授权（关键写 `@Idempotent`）
- [x] 6.2 `user-service`：用户-角色分配/回收（唯一键 `user_id,role_id`，"选人"经 `user-api` 代理查询，Feign 在事务外、Fallback 不阻断主流程）+ `/me/menus`（type 1/2 子树按 `sort`）与 `/me/permissions`（type 3/4 集合）本库联查
- [x] 6.3 `user-service`：管理接口 `@PreAuthorize`（`user:resource:action` 与前端同串）；服务内 `user_id` 以网关注入 `X-User-Id` 为准，不信客户端头

## 7. 网关（user-gateway）

- [x] 7.1 路由到 `user-service`（Nacos 发现）+ `oauth2-resource-server` JWT 验签（JWKS 指向认证中心，不引 SAS；壳阶段本地放行开关留接线注释）+ 登录态粗拦截/放行清单
- [x] 7.2 `X-User-Id` / `X-User-Name` 覆盖注入 + CORS 唯一归属配置（信任域名登记 `docs/test-env.md`）；`/actuator/health` 可用

## 8. 配置与环境登记

- [x] 8.1 三份 yml（`application.yml` 无主机/账密 + `application-local.yml` + `application-test.yml`，profile 各司其职）；连接信息与 `docs/test-env.md` 一致（MySQL/Redis/Nacos/JWKS/信任域名），个人差异走 `docs/test-env.local.md`
- [x] 8.2 Nacos 注册/配置接线（`user-service` / `user-gateway`）；本地默认 `local`、联调切 `test` 可启动

## 9. 前端 user-admin

- [x] 9.1 工程壳：`src/` 按 `CLAUDE.md` §5.5 结构（api/views/components/stores/router/types/utils/styles）；axios 统一封装 + 解包 + §5.3 分流（401 跳登录、403 提示、业务失败展示 `msg`）；请求只经 `src/api`；`id` 一律 `string`
- [x] 9.2 账号管理页（列表/详情/新建/启停）+ 审计查询页（user-account spec 管理端行为）
- [x] 9.3 菜单管理（树表单）+ 角色管理（含用户-角色分配、选人降级交互）；登录后经 `/me/menus` 动态生成路由、`/me/permissions` 控制按钮显隐；禁硬编码角色名（user-rbac spec 管理端行为）
- [x] 9.4 `npm run lint && npm run type-check` 全绿（`frontend/user-admin/`）；无 `console.log` 残留

## 10. 联调自检与文档收尾

- [ ] 10.1 冒烟（对齐 design Migration Plan）：创建用户 → by-username → 启停 → 分页 → 菜单/角色 CRUD + 授权 → `/me/menus`；错误路径（重复 username、401、403、校验 `30001`）
- [x] 10.2 收尾：`deploy/nginx/`（或等价）登记 Nginx 反代与静态托管配置；提交前自查清单过一遍（`CLAUDE.md` §6.3：编译/lint/自检命令过、无调试残留、无生产密钥、规范对齐）

