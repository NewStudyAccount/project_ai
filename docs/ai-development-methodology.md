# 单条业务逻辑分析法 —— 从页面点击追到数据落库（系统 A 逆向手册）

> **本文只解决一件事：** 给定系统 A 的一个业务操作（例如"运营在后台发布一篇文章"），如何把它完整拆解成一张**可实施、可验收的分析卡**——前端怎么操作、调了哪些接口、接口操作了哪些表、有哪些特殊业务校验、数据怎么流动。
> **产出：** 一张《业务逻辑分析卡》（模板见 §2），别人只看这张卡就能在系统 B 里实现同样的逻辑。
> **适用：** A 是 Vue3 + Spring Boot + MyBatis(-Plus) + MySQL 一类的前后端分离系统。其他栈方法相同，命令换掉即可。

---

## 1. 先看成品：分析卡长什么样

分析一条业务逻辑，最终就交这一张卡。**先看成品，再学怎么填。**

```markdown
# 业务逻辑分析卡 · 文章发布

## 1. 场景
角色：运营（admin）
入口：后台 → 内容管理 → 文章列表 → 某篇文章「发布」按钮
结果：文章状态变为已发布，公开端可见

## 2. 前端操作路径
路由：/articles/edit/:id  （router/index.ts:45）
页面：views/article/ArticleEdit.vue
按钮：「发布」→ @click="handlePublish"（ArticleEdit.vue:18）
表单校验：标题必填（ArticleEdit.vue:12）—— 可绕过，不算业务校验
请求：POST /api/v1/admin/articles/{id}/publish（api/article.ts:31）
拦截器：baseURL=/api/v1，自动带 Bearer Token（utils/request.ts:8）

## 3. 接口
API-001  POST /api/v1/admin/articles/{id}/publish
Controller：ArticleController#publish（ArticleController.java:9）
权限：blog:article:publish（@RequiresPermission）
入参：path id；无 body
出参：Result<Void>
错误码：203001 / 203002 / 203003 / 203004

## 4. 后端调用链
ArticleController#publish
 └─ ArticleServiceImpl#publish  @Transactional(rollbackFor=Exception)
     ├─ ArticleMapper#selectById     读 blog_article
     ├─ 校验：存在 / 状态 / 正文 / 分类
     ├─ ArticleMapper#updateById     写 blog_article
     ├─ AuditLogService#log          写 sys_audit_log
     └─ ArticleSearchPublisher#send  发 MQ（检索同步）

## 5. 表操作
| # | 表 | 操作 | 字段 | 条件 | 事务内 | 证据 |
|---|----|------|------|------|--------|------|
| 1 | blog_article | SELECT | id,status,content_file_id,category_id | id=? AND deleted=0 | 是 | ArticleMapper.xml:12 |
| 2 | blog_article | UPDATE | status,publish_time,update_time | id=? AND deleted=0 | 是 | ArticleMapper.xml:28 |
| 3 | sys_audit_log | INSERT | biz_type,biz_id,action,operator_id | — | 是 | AuditLogServiceImpl:31 |

## 6. 特殊业务校验
| # | 校验 | 层 | 条件 | 失败表现 | 证据 |
|---|------|----|------|----------|------|
| V1 | 文章存在 | 后端 | selectById == null | 203001 | ArticleServiceImpl:12 |
| V2 | 仅草稿可发布 | 后端 | status != 0 | 203002 | ArticleServiceImpl:13 |
| V3 | 正文必填 | 后端 | content_file_id == null | 203003 | ArticleServiceImpl:14 |
| V4 | 分类必填 | 后端 | category_id == null | 203004 | ArticleServiceImpl:15 |
| V5 | slug 唯一 | DB | 唯一索引冲突 | 1062 → 203005 | DDL uk_blog_article_slug |
| V6 | 权限 | 网关 | 无 blog:article:publish | 403 | @RequiresPermission |

## 7. 数据流向
| 数据 | 来源 | 转换 | 落点 | 副作用 |
|------|------|------|------|--------|
| 标题 | 表单 | trim，≤100 字 | blog_article.title | MQ 检索同步 |
| 正文 | Markdown 编辑器（保存时已上传） | UTF-8 文件 | sys_file.object_key + article_detail.content_file_id | 对象存储 |
| 状态 | 发布按钮 | 0→1 | blog_article.status | 审计、缓存失效、MQ |
| 发布时间 | 服务端 now | Asia/Shanghai | blog_article.publish_time | — |
| 操作人 | 网关 X-User-Id | — | sys_audit_log.operator_id | — |

## 8. 异常与回滚
失败统一抛 BizException → 全局异常处理 → HTTP 200 + code
事务：任一失败整体回滚（含 audit）；MQ 发送在事务提交后（如有 @TransactionalEventListener）

## 9. 副作用
审计日志、检索同步 MQ、文章详情缓存失效、公开端列表缓存失效

## 10. 证据与疑点
证据：见各行号
疑点：Q-003 发布时不校验 MinIO 对象是否存在（ArticleServiceImpl:14 只判空）
```

**这张卡就是标准。** 下面讲怎么一步步把每一格填出来。

---

## 2. 总方法：双向追踪 + 运行时对账

只从一个方向追，一定会漏。用三条路径互相印证：

```
正向：页面点击 → 请求 → Controller → Service → Mapper → 表
反向：表 → 谁写它（SQL/Mapper）→ Service → Controller → 哪个页面
运行时：实际操作一次 → Network / SQL 日志 / DB 前后快照 → 对照上面两条
```

- **正向**回答"点了之后发生了什么"。
- **反向**回答"这个字段/状态是谁写进去的"，专治漏路径（批量、Job、脚本也会写同一张表）。
- **运行时**回答"代码真的是这么跑的吗"，专治漏 AOP、拦截器、缓存、MQ。

三条路径的结论必须一致；不一致的地方就是疑点，必须查清。

---

## 3. 五个问题、五套追踪方法

### 3.1 问题一：前端如何操作？

**目标：** 找到"用户点了哪里 → 发了个什么请求"。

| 步骤 | 做什么 | 命令/动作 |
|------|--------|-----------|
| 1 | 找路由 | 搜路径或页面名 |
| 2 | 找页面组件 | 路由指向的 `.vue` |
| 3 | 找按钮/事件 | 搜按钮文案或 `@click` |
| 4 | 找 API 函数 | 搜 `src/api/` |
| 5 | 找请求封装 | `baseURL`、拦截器、统一错误处理 |
| 6 | 找权限控制 | `v-permission`、路由 meta、按钮显隐 |

```powershell
# 1. 路由
Get-ChildItem -Recurse -Include *.ts | Select-String -Pattern "path:\s*['\"]/articles"

# 2. 页面里的按钮（中文文案最可靠）
Get-ChildItem -Recurse -Include *.vue | Select-String -Pattern '发布' -Context 2,8

# 3. API 定义
Get-ChildItem -Recurse -Include *.ts | Select-String -Pattern 'publishArticle|/publish'

# 4. 请求封装
Get-ChildItem -Recurse -Include *.ts | Select-String -Pattern 'baseURL|interceptors\.(request|response)'

# 5. 权限指令
Get-ChildItem -Recurse -Include *.vue | Select-String -Pattern 'v-permission|v-hasPerm'
```

**读代码时确认这 6 件事：**

1. 路由路径与组件文件
2. 触发控件（按钮/菜单/行内操作）及事件函数
3. 事件函数里做了哪些前置动作（表单校验、二次确认、loading）
4. 调用了哪个 API 函数，传了什么参数（注意 id 是 string 还是 number）
5. axios 拦截器加了什么（Token、租户、语言、统一解包）
6. 成功/失败分别怎么提示、跳转

**跑不起来的代码怎么办：** 打开测试环境页面，F12 → Network → 勾选 Preserve log → 操作一次 → 右键请求 → Copy as cURL。请求的 method/URL/payload/response 就是最硬的证据，比读代码可靠。

**坑：**

- 页面上的按钮可能调多个接口（先保存再发布）→ 按 Network 顺序记。
- 同一个 API 函数可能被多个页面复用 → 反向搜函数名，确认所有调用点。
- 前端表单校验只是体验，**不是业务校验**，后端必须再校验；分析卡里要标"可绕过"。

---

### 3.2 问题二：调用哪些接口？

**目标：** 把前端请求映射到后端入口，并确认权限、入参、出参、错误码。

| 步骤 | 做什么 | 命令/动作 |
|------|--------|-----------|
| 1 | 确认网关前缀 | 看 `application.yml` 的 context-path / gateway 路由 |
| 2 | 找 Controller | 搜路径片段 |
| 3 | 确认类级前缀 + 方法级路径 | 读注解 |
| 4 | 确认权限 | `@RequiresPermission` / 拦截器 / 网关白名单 |
| 5 | 确认入参 | path / query / body，DTO 字段与校验注解 |
| 6 | 确认出参 | 返回类型、是否包装 `Result`、分页结构 |

```powershell
# 找 Controller
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern '"/articles"|"/publish"'

# 找权限注解
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern '@RequiresPermission|@PreAuthorize'

# 找网关路由/白名单
Get-ChildItem -Recurse -Include *.yml,*.yaml | Select-String -Pattern 'routes:|predicates:|public-paths'
```

**一个接口条目的最小信息：**

```
API-001 POST /api/v1/admin/articles/{id}/publish
Controller: ArticleController#publish (ArticleController.java:9)
权限: blog:article:publish
入参: path id (Long); 无 body
出参: Result<Void>
错误码: 203001 不存在 / 203002 状态不允许 / 203003 正文缺失 / 203004 分类缺失
副作用: 状态变更、审计、MQ
```

**坑：**

- 网关可能改写路径（`/api/v1/**` → 去掉前缀），别拿前端路径直接去后端搜。
- 一个"保存"可能触发多个接口（上传、保存、发布），按顺序全部列出。
- 同一个 URL 可能 GET/POST 各一个方法，别混。
- 权限可能不在注解上，而在网关路由或全局拦截器里。

---

### 3.3 问题三：接口操作哪些表？

**目标：** 列出这次操作读/写了哪些表、哪些字段、什么条件、是否在同一事务。

| 步骤 | 做什么 | 命令/动作 |
|------|--------|-----------|
| 1 | Controller 方法体找 Service 调用 | 读代码 |
| 2 | Service 实现里找 Mapper 调用 | 搜 `Mapper.` |
| 3 | Mapper 接口找 SQL | XML 或注解 |
| 4 | MyBatis-Plus 方法还原 SQL | `selectById` / `updateById` / `selectList` |
| 5 | 找动态 SQL 的所有分支 | `<if>` / `<where>` / `<foreach>` |
| 6 | 标记事务边界 | `@Transactional` 所在方法 |

```powershell
# 从 Service 找 Mapper 调用
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern '\w+Mapper\.\w+\('

# 反向：谁写了这张表（关键，防漏 Job/批量/脚本）
Get-ChildItem -Recurse -Filter *.xml | Select-String -Pattern '(?i)(insert\s+into|update|delete\s+from)\s+blog_article'
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern 'blogArticleMapper\.(insert|update|delete)'

# 看 Entity 的真实表名与逻辑删除
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern '@TableName|@TableLogic'
```

**表操作矩阵（必须逐行填）：**

| # | 表 | 操作 | 字段 | 条件 | 事务内 | 证据 |
|---|----|------|------|------|--------|------|
| 1 | blog_article | SELECT | id,status,content_file_id,category_id | id=? AND deleted=0 | 是 | ArticleMapper.xml:12 |
| 2 | blog_article | UPDATE | status,publish_time,update_time | id=? AND deleted=0 | 是 | ArticleMapper.xml:28 |
| 3 | sys_audit_log | INSERT | biz_type,biz_id,action,operator_id | — | 是 | AuditLogServiceImpl:31 |

**MyBatis-Plus 的还原规则（最容易漏）：**

| 代码 | 实际 SQL 要点 |
|------|----------------|
| `selectById(id)` | `SELECT ... FROM 表 WHERE id=? AND deleted=0`（`@TableLogic` 自动加） |
| `updateById(entity)` | 只更新非 null 字段；`update_time` 由 `MetaObjectHandler` 自动填 |
| `selectList(wrapper)` | 看 wrapper 的 eq/like/orderBy；注意有没有默认条件 |
| `insert(entity)` | `create_time/create_by` 自动填；主键由 `framework` 发号 |

**坑：**

- 只看 Service 不看 Mapper XML → 漏 where 条件与联表。
- 只看一条 SQL → 动态 SQL 有多个分支，每个分支都是不同规则。
- 漏逻辑删除：所有查询都带 `deleted=0`，这是全局规则，卡里写一次即可。
- 漏自动填充：`create_time/update_time/create_by/update_by` 不在业务代码里。
- 事务边界：`@Transactional` 加在 Service 上，但同类自调用会失效；跨服务 Feign 不在事务内。
- 反向查"谁写这张表"能发现：定时任务、批量导入、运维脚本、MQ 消费者也在改同一张表。

---

### 3.4 问题四：有哪些特殊业务校验？

**目标：** 把"什么情况下不允许做"全部找出来，并区分层级：前端 / 后端 / 数据库。

**校验的五个藏身点：**

| 藏身点 | 怎么找 | 典型形式 |
|--------|--------|----------|
| Controller/DTO | 看 `@Valid` 与字段注解 | `@NotBlank`、`@Size`、`@Pattern`、自定义 `@XxxValid` |
| Service 手写 | 搜 `if (... ) throw`、`BizException` | 状态、归属、重复、时限、配额 |
| Mapper/SQL | 看 where 条件与条件更新 | `WHERE status=0`（CAS）、`WHERE user_id=?`（数据范围） |
| 数据库 | 看 DDL 唯一索引/非空/默认值 | `uk_xxx`、`NOT NULL`、外键（本项目禁物理外键） |
| 横切 | 看拦截器/AOP/网关 | 权限、幂等、限流、租户、数据范围 |

```powershell
# 手写校验
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern 'throw new BizException|throw new \w+Exception'

# Bean Validation
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern '@NotNull|@NotBlank|@Size|@Pattern|@Min|@Max|@Valid'

# 数据库约束
Get-ChildItem -Recurse -Include *.sql | Select-String -Pattern 'UNIQUE|NOT NULL|CHECK'

# 条件更新（CAS 也是校验）
Get-ChildItem -Recurse -Include *.xml | Select-String -Pattern 'WHERE.*status\s*='
```

**校验清单（每条都要有"条件 / 层级 / 失败表现 / 证据"）：**

| # | 校验 | 层 | 条件 | 失败表现 | 证据 |
|---|------|----|------|----------|------|
| V1 | 标题必填 | 前端 | title 为空 | 表单提示，不发请求 | ArticleEdit.vue:12 |
| V2 | 标题必填 | 后端 | `@NotBlank` | 400 + 3xxxx | ArticleDTO:8 |
| V3 | 文章存在 | 后端 | `selectById == null` | 203001 | ArticleServiceImpl:12 |
| V4 | 仅草稿可发布 | 后端 | `status != 0` | 203002 | ArticleServiceImpl:13 |
| V5 | 正文必填 | 后端 | `content_file_id == null` | 203003 | ArticleServiceImpl:14 |
| V6 | slug 唯一 | DB | 唯一索引冲突 | 1062 → 203005 | DDL uk_blog_article_slug |
| V7 | 权限 | 网关 | 无 `blog:article:publish` | 403 | @RequiresPermission |

**区分三类校验（分析卡里必须标）：**

1. **业务校验**：业务规则，换系统也要保留（状态、归属、时限、配额）。
2. **技术校验**：空值、长度、格式、类型（Bean Validation 为主）。
3. **体验校验**：前端表单提示（可绕过，后端必须再校验）。

**坑：**

- 只看 `if` 不看 `throw` 里的错误码 → 卡里要写 code 与文案。
- 漏"否定分支"：`if (status == 1) return;` 这种提前返回也是规则。
- 漏条件更新：`UPDATE ... WHERE status=0` 是并发校验，不是普通写。
- 漏数据范围：SQL 里的 `user_id = ?` 是权限校验。
- 漏幂等/限流：`@Idempotent`、`@RateLimit` 也是"不允许重复/过量"的校验。
- 前端校验当业务校验 → 直接调接口就绕过了，必须标明"可绕过"。

---

### 3.5 问题五：数据流向是什么？

**目标：** 回答"数据从哪来、变成了什么、落到哪、还有什么副作用"。

**数据流追踪表（逐字段填）：**

| 数据 | 来源 | 转换 | 落点 | 副作用 |
|------|------|------|------|--------|
| 标题 | 表单 | trim，≤100 字 | blog_article.title | MQ 检索同步 |
| 正文 | Markdown 编辑器 | 保存时上传为 UTF-8 文件 | sys_file.object_key + article_detail.content_file_id | 对象存储 |
| 状态 | 发布按钮 | 0→1 | blog_article.status | 审计、缓存失效、MQ |
| 发布时间 | 服务端 now | Asia/Shanghai | blog_article.publish_time | — |
| 操作人 | 网关 `X-User-Id` | — | sys_audit_log.operator_id | — |

**追这五类数据：**

1. **用户输入**：表单字段 → DTO → BO → Entity，哪些字段被 trim/默认值/加密/脱敏。
2. **数据库读出的数据**：读出来之后做了什么判断、拼装、转换。
3. **文件/对象存储**：上传接口、bucket、object key、文件记录表。
4. **外部服务**：Feign/HTTP 的入参出参，失败怎么处理。
5. **异步数据**：MQ 消息体、消费方、缓存 key、检索索引。

**副作用清单（单独列，最容易漏）：**

| 副作用 | 触发点 | 证据 |
|--------|--------|------|
| 审计日志 | `AuditLogService#log` | ArticleServiceImpl:19 |
| 检索同步 MQ | `ArticleSearchPublisher#send` | ArticleServiceImpl:20 |
| 缓存失效 | `@CacheEvict("article:detail")` | ArticleServiceImpl:10 |
| 对象存储 | 保存正文时上传 | ArticleContentStorage:22 |

**坑：**

- 只看主表，漏明细表/关联表/文件表。
- 漏"先上传文件再保存记录"的两步流程（正文常常是这种）。
- 漏 MQ：代码里 `send` 了，但消费方在另一个模块，卡里要写"谁消费、干什么"。
- 漏缓存：读的时候可能先查 Redis，写的时候要失效。
- 漏状态机：发布后还有下架/归档，数据流向要写清"谁能触发下一步"。

---

## 4. 横向补充：异常、隐藏路径、运行时验证

### 4.1 异常与回滚矩阵

| 场景 | HTTP | code | msg | 事务 |
|------|------|------|-----|------|
| 文章不存在 | 200 | 203001 | 文章不存在 | 回滚 |
| 非草稿 | 200 | 203002 | 当前状态不可发布 | 回滚 |
| 正文缺失 | 200 | 203003 | 请先上传正文 | 回滚 |
| 分类缺失 | 200 | 203004 | 请选择分类 | 回滚 |
| 无权限 | 403 | 10002 | 无权限 | 不进入 |
| 重复提交 | 200 | 首次结果 | — | 幂等拦截 |

**要确认的三件事：** 失败时回滚哪些表；有没有"部分成功"（如文件已上传但记录没写）；MQ 发送在事务内还是提交后。

### 4.2 别漏了这些隐藏路径（逐项打勾）

- [ ] 网关：前缀重写、鉴权、CORS、限流
- [ ] 拦截器/过滤器：登录、权限、租户、数据范围、TraceId
- [ ] AOP：审计、幂等、限流、日志、事务代理
- [ ] 全局异常处理：异常 → HTTP → code 映射
- [ ] 返回包装：`Result`、分页、Long → String
- [ ] MyBatis-Plus：逻辑删除、自动填充、主键发号
- [ ] Feign：下游服务、Fallback、超时
- [ ] MQ：发送/消费、重试、死信
- [ ] 定时任务：后续处理（如定时发布、状态清理）
- [ ] 缓存：读缓存、写失效、key 规则
- [ ] 对象存储：上传、URL、删除
- [ ] 异步线程池：提交后不阻塞主流程
- [ ] 数据库：唯一索引、默认值、触发器（如有）
- [ ] 前端：路由守卫、按钮权限、表单校验、请求拦截器、错误提示
- [ ] 多端：admin/portal/mobile 是否同一接口
- [ ] 多入口：批量操作、导入、脚本、Job 也改同一张表

### 4.3 运行时验证（把静态分析钉死）

静态读代码会漏，**必须跑一次**：

1. **打开 SQL 日志**（测试环境）：

```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
# 或
logging:
  level:
    com.qjj.blog.mapper: debug
```

2. **DB 前后快照**：

```sql
-- 操作前
SELECT id,status,publish_time,update_time FROM blog_article WHERE id=1001;
-- 执行发布
-- 操作后
SELECT id,status,publish_time,update_time FROM blog_article WHERE id=1001;
SELECT id,biz_type,biz_id,action,operator_id,create_time
  FROM sys_audit_log WHERE biz_id=1001 ORDER BY id DESC LIMIT 3;
```

3. **对照三样东西**：SQL 日志的语句顺序 / 表操作矩阵 / 实际 DB 变化。三者不一致 → 有隐藏路径，回去查拦截器、AOP、触发器、异步任务。

4. **前端 Network 对照**：请求 URL、payload、response 与 API 条目逐字段比对。

**验证记录模板：**

```markdown
## 运行时验证 · 2026-11-06
- 环境：test，账号 admin
- 操作：文章 1001 发布
- 请求：POST /api/v1/admin/articles/1001/publish → 200 {"code":0}
- SQL 日志：SELECT blog_article → UPDATE blog_article → INSERT sys_audit_log → （无 MQ 日志，疑点 Q-006）
- DB 变化：status 0→1，publish_time 2026-11-06 15:20:31，update_time 同步变化
- 审计：新增 1 条 PUBLISH
- 与矩阵差异：矩阵写了 MQ 发送，但日志里没有 → 查是否异步/是否条件发送 → 记 Q-006
```

---

## 5. 完整样例：文章发布（逐步填卡）

### 5.1 前端

```vue
<!-- views/article/ArticleEdit.vue -->
<template>
  <el-form ref="formRef" :model="form" :rules="rules">
    <el-input v-model="form.title" />
    <el-button v-permission="'blog:article:publish'" @click="handlePublish">发布</el-button>
  </el-form>
</template>

<script setup lang="ts">
import { publishArticle } from '@/api/article'

const rules = { title: [{ required: true, message: '请输入标题', trigger: 'blur' }] }

async function handlePublish() {
  await formRef.value?.validate()
  await publishArticle(route.params.id as string)
  ElMessage.success('发布成功')
}
</script>
```

```ts
// api/article.ts
export function publishArticle(id: string) {
  return request.post(`/articles/${id}/publish`)
}

// utils/request.ts
const request = axios.create({ baseURL: '/api/v1', timeout: 10000 })
request.interceptors.request.use(cfg => {
  cfg.headers.Authorization = `Bearer ${tokenStore.accessToken}`
  return cfg
})
request.interceptors.response.use(res => res.data)
```

**前端结论：** 页面 `ArticleEdit.vue`，按钮有 `v-permission`，点击前做表单校验（可绕过），请求 `POST /api/v1/admin/articles/{id}/publish`，无 body，响应被拦截器解包成 `{code,msg,data}`。

### 5.2 后端入口

```java
@RestController
@RequestMapping("/api/v1/admin/articles")
public class ArticleController {
    @PostMapping("/{id}/publish")
    @RequiresPermission("blog:article:publish")
    public Result<Void> publish(@PathVariable Long id) {
        articleService.publish(id, UserContext.getUserId());
        return Result.ok();
    }
}
```

**接口结论：** 权限 `blog:article:publish`；入参只有 path id；出参 `Result<Void>`；无 body。

### 5.3 调用链与表操作

```java
@Service
public class ArticleServiceImpl implements ArticleService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Long userId) {
        BlogArticle a = articleMapper.selectById(id);                    // 1 读
        if (a == null)                    throw new BizException(203001); // V1
        if (a.getStatus() != 0)           throw new BizException(203002); // V2
        if (a.getContentFileId() == null) throw new BizException(203003); // V3
        if (a.getCategoryId() == null)    throw new BizException(203004); // V4

        a.setStatus(1);
        a.setPublishTime(LocalDateTime.now());
        articleMapper.updateById(a);                                     // 2 写
        auditLogService.log(userId, "PUBLISH", id);                     // 3 写审计
        searchPublisher.send(a);                                        // 4 MQ
    }
}
```

**表操作矩阵：**

| # | 表 | 操作 | 字段 | 条件 | 事务内 | 证据 |
|---|----|------|------|------|--------|------|
| 1 | blog_article | SELECT | id,status,content_file_id,category_id | id=? AND deleted=0 | 是 | ArticleMapper.xml:12 |
| 2 | blog_article | UPDATE | status,publish_time,update_time | id=? AND deleted=0 | 是 | ArticleMapper.xml:28 |
| 3 | sys_audit_log | INSERT | biz_type,biz_id,action,operator_id,create_time | — | 是 | AuditLogServiceImpl:31 |

### 5.4 特殊校验（三层）

| # | 校验 | 层 | 条件 | 失败表现 | 证据 |
|---|------|----|------|----------|------|
| V1 | 标题必填 | 前端 | 空 | 表单提示 | ArticleEdit.vue:12 |
| V2 | 权限 | 网关 | 无 `blog:article:publish` | 403 | @RequiresPermission |
| V3 | 文章存在 | 后端 | null | 203001 | ArticleServiceImpl:12 |
| V4 | 仅草稿可发布 | 后端 | status != 0 | 203002 | :13 |
| V5 | 正文必填 | 后端 | content_file_id == null | 203003 | :14 |
| V6 | 分类必填 | 后端 | category_id == null | 203004 | :15 |
| V7 | slug 唯一 | DB | 唯一索引 | 1062 → 203005 | DDL uk_blog_article_slug |

### 5.5 数据流向

| 数据 | 来源 | 转换 | 落点 | 副作用 |
|------|------|------|------|--------|
| 标题 | 表单 | trim | blog_article.title | 检索同步 |
| 正文 | 保存时上传 MinIO | UTF-8 文件 | sys_file + article_detail.content_file_id | 对象存储 |
| 状态 | 按钮 | 0→1 | blog_article.status | 审计、缓存失效、MQ |
| 发布时间 | 服务端 now | Asia/Shanghai | blog_article.publish_time | — |
| 操作人 | 网关 X-User-Id | — | sys_audit_log.operator_id | — |

### 5.6 分析卡成品

把 5.1–5.5 按 §1 的模板拼起来，就是一张完整的卡。**交付标准：** 一个没看过 A 代码的开发，只看这张卡就能在 B 里实现发布功能；实现时不需要再打开 A 的代码。

---

## 6. 十问自检（每张卡交付前过一遍）

1. 我能画出"点击 → 请求 → Controller → Service → 表"的完整链路吗？
2. 每个接口的路径、方法、权限、入参、出参、错误码都齐吗？
3. 每张表的每次读/写、字段、where 条件、事务边界都列了吗？
4. 每条校验的条件、层级（前端/后端/DB）、失败表现、证据都有吗？
5. 数据从哪来、怎么转换、落到哪、有什么副作用，都填了吗？
6. 状态怎么变、谁能触发下一步、有没有中间态？
7. 失败时回滚哪些表？有没有部分成功？MQ 在事务内还是提交后？
8. 隐藏路径（AOP、拦截器、MQ、Job、缓存、文件、多入口）都查了吗？
9. 运行时验证和静态分析对得上吗？差异解释了吗？
10. 别人只看这张卡，能在 B 里实现吗？不能 → 缺什么补什么。

---

## 7. 常见错误与纠正

| 错误 | 症状 | 纠正 |
|------|------|------|
| 只看 Controller | 规则都在 Service 里，卡里只有接口没有校验 | 必须读到 Mapper/SQL |
| 只看成功路径 | 漏异常分支与错误码 | 逐个 `if/throw` 列 |
| 把前端校验当业务校验 | 直接调接口就绕过 | 标"可绕过"，后端另列 |
| 漏 MyBatis-Plus 自动逻辑 | 卡里没有 `deleted=0`、自动填充字段 | 按 §3.3 表逐项还原 |
| 漏 AOP/拦截器 | 审计、幂等、权限没写 | 过 §4.2 清单 |
| 漏异步副作用 | 卡里没有 MQ/缓存/文件 | 搜 `send|publish|evict|putObject` |
| 动态 SQL 只记一条 | 不同条件走不同 SQL | 每个 `<if>` 分支都记 |
| 用类名猜语义 | "应该是校验分类" | 必须读实现，给行号 |
| 不跑运行时 | 静态分析和实际不一致 | 按 §4.3 验证一次 |
| 卡写成流水账 | 没有表格、没有证据、不可核对 | 按 §1 模板逐格填 |

---

## 8. 命令速查

```powershell
# 前端
Get-ChildItem -Recurse -Include *.ts  | Select-String -Pattern "path:\s*['\"]/articles"
Get-ChildItem -Recurse -Include *.vue | Select-String -Pattern '发布' -Context 2,8
Get-ChildItem -Recurse -Include *.ts  | Select-String -Pattern 'publishArticle|/publish'
Get-ChildItem -Recurse -Include *.ts  | Select-String -Pattern 'baseURL|interceptors'

# 后端入口
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern '"/articles"|"/publish"'
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern '@RequiresPermission'

# 表操作
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern '\w+Mapper\.\w+\('
Get-ChildItem -Recurse -Filter *.xml  | Select-String -Pattern '(?i)(insert\s+into|update|delete\s+from)\s+blog_article'
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern '@TableName|@TableLogic'

# 校验
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern 'throw new BizException'
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern '@NotNull|@NotBlank|@Size|@Pattern'
Get-ChildItem -Recurse -Include *.sql   | Select-String -Pattern 'UNIQUE|NOT NULL'

# 副作用
Get-ChildItem -Recurse -Filter *.java | Select-String -Pattern 'send\(|publish\(|evict|putObject|convertAndSend'
```

---

> **一句话总结：** 一条业务逻辑的分析 = **前端找到请求** + **后端找到链路** + **SQL 找到表** + **if/索引找到校验** + **字段级找到数据流** + **跑一次验证**，最后填成一张卡。
