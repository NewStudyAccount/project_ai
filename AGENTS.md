# 项目全局规范（AGENTS.md）

> 本文件是项目唯一的事实来源（Single Source of Truth），面向所有 AI 编码工具
> （Cursor、Claude Code、Windsurf、Codex、Trae 等）与人类协作者。
> 所有 AI 工具在编写、修改代码前必须先读取并遵循本文件。

---

## 1. 项目概述

<!-- 项目是做什么的，一句话说清业务目标与边界 -->

- 项目名称：
- 业务目标：
- 目标用户：

## 2. 技术栈

- 前端：Vue3 + Vue Router + TypeScript + Pinia + Element Plus
- 后端：Java 21 + Spring Boot + Spring Security + MyBatis-Plus + Spring Cloud Gateway + Nacos
- 数据库：MySQL
- 构建：前端 Vite / 后端 Maven
- 包管理：npm（前端）

## 3. 目录结构

<!-- 前后端分仓或分目录，按实际落地后更新 -->

```
.
├── frontend/           # 前端（Vue3 + TS + Pinia）
├── backend/            # 后端（Spring Boot 微服务，含 Gateway/Nacos）
├── docs/               # 文档
├── openspec/           # 规范驱动开发制品（勿手改，见第 6 节）
└── AGENTS.md           # 本文件
```

## 4. 开发规范

### 4.1 编码规范

- 命名：前端变量/函数 `camelCase`、组件 `PascalCase`、常量 `UPPER_SNAKE`；
  后端类名 `PascalCase`、方法/变量 `camelCase`、常量 `UPPER_SNAKE`。
- 格式化：前端 Prettier + ESLint；后端遵循 Spring 官方代码风格（IDE 统一格式化）。
- 注释语言：中文（与提交语言一致）。
- 错误处理：统一走 4.4.3 全局异常处理，前后端均禁止吞异常。

### 4.1.1 前端代码规范

- 组件风格：统一使用组合式 API（`<script setup lang="ts">`），禁止混用 Options API。
- 组件命名：文件名与组件名用 `PascalCase`，单文件组件（SFC）一个文件只放一个组件。
- 目录约定：`src/api`（接口请求）、`src/views`（页面）、`src/components`（通用组件）、`src/stores`（Pinia）、`src/utils`（工具函数）、`src/types`（TS 类型）。
- 状态管理：跨组件共享状态一律走 Pinia，禁止用全局变量或事件总线传状态。
- 路由：路由配置集中维护，动态路由与权限需与后端权限标识一致，不硬编码角色。
- 样式：优先用 Element Plus 组件与 scoped 样式，禁止内联样式堆叠；统一主题变量写在全局样式文件。
- UI 组件：统一使用 Element Plus 组件，禁止自造重复轮子；图标用 Element Plus 图标库。
- TypeScript：页面取值处逐字段对照接口真实签名定义类型，禁止用 `any` 绕过类型检查。
- 请求封装：统一走 `src/api` 封装 + axios 拦截器，业务组件不直接调 axios。

### 4.1.2 后端代码规范

- 对象分层职责，禁止越层混用：
  | 对象 | 职责 | 边界 |
  |---|---|---|
  | `Entity` | 数据库表映射，只含持久化字段 | 禁止暴露给 Controller / 前端 |
  | `DTO` | 接口入参，接收前端请求 | 负责参数校验 |
  | `VO` | 接口出参，返回给前端 | 按页面需要组装 |
  | `BO` | 业务对象，Service 内部流转 | 不对外 |
- 禁止把 `Entity` 直接作为接口入参或出参；`Entity` ↔ `DTO`/`VO` 转换统一用 MapStruct 或集中转换层，禁止在 Controller 里手写逐字段赋值。
- Service 接口 + 实现分离（`XxxService` + `XxxServiceImpl`），业务规则只写在 Service 层，Controller 只做参数接收、调用 Service、返回结果。
- Mapper 只写数据访问，禁止在 Mapper 里写业务逻辑。

### 4.2 接口与数据模型

- 接口契约来源：以后端真实 Controller 签名与 API 文档为准，前端按真实字段取值。
- 枚举/字段以什么为准：后端枚举类与数据库表结构为准，禁止前端自造枚举值。
- RESTful 风格：URL 用小写 + 连字符（kebab-case），资源用名词复数（如 `/users`）；HTTP 方法语义正确（查询 `GET`、新增 `POST`、修改 `PUT`、删除 `DELETE`）。
- 版本号：接口路径带版本前缀（如 `/api/v1/...`），破坏性变更才升级版本。
- 命名：接口方法见名知义（`getXxx / listXxx / createXxx / updateXxx / deleteXxx`），禁止语义模糊的方法名。

### 4.3 提交规范

- 格式：`type(scope): subject`，type ∈ `feat / fix / refactor / docs / chore / test / perf`。
- subject 用中文祈使句、≤ 50 字符，不写句号。
- 单人开发：直接提交到 `main`，不强制分支与 PR 评审；较大功能可自行开分支隔离。

### 4.4 基础代码规范

> 本节只约定「契约」，不写具体实现。基础代码在脚手架初始化后作为第一批落地，
> 落地实现必须符合以下契约。

#### 4.4.1 统一返回体

- 后端所有接口统一返回结构 `{ code, msg, data }`，`code` 为业务错误码，`msg` 为提示信息，`data` 为业务数据。
- 分页场景统一返回 `{ records, total, size, current }`（MyBatis-Plus `Page` 对象），新接口沿用既有约定。
- 前端 axios 拦截器统一解包，禁止在业务代码里逐处判断返回结构。

#### 4.4.2 错误码

- 错误码用数字分段区分类型，分段固定如下：
  | 分段 | 含义 |
  |---|---|
  | `1xxxx` | 系统异常 |
  | `2xxxx` | 业务异常 |
  | `3xxxx` | 参数校验异常 |
- 错误码与提示信息通过枚举/常量维护，禁止在业务代码里硬编码错误码字符串。

#### 4.4.3 全局异常处理

- 后端用 `@RestControllerAdvice` 统一捕获异常，分类处理：业务异常、参数校验异常、系统异常。
- 铁律：禁止吞异常；禁止把堆栈 / 内部实现细节返回给前端；系统异常统一返回友好提示并记日志。
- 前端统一 axios 拦截器处理错误，按 `code` 分流（如登录失效跳转、业务错误弹提示、系统错误通用提示）。

#### 4.4.4 鉴权

- 后端 Spring Security 统一鉴权，网关层（Spring Cloud Gateway）做入口鉴权与路由。
- 权限判断以真实角色/权限标识为准，禁止前端把鉴权当作安全边界（前端隐藏仅优化体验，后端必须兜底校验）。

#### 4.4.5 日志

- 后端用统一日志规范，异常必须记录完整堆栈；关键业务操作记操作日志。
- 禁止用 `System.out.println` 打印日志。

#### 4.4.6 数据校验

- 入参校验统一用 Bean Validation（`@Valid` / 校验注解），禁止在 Controller / Service 里手写重复的 if 校验。
- 校验失败统一走全局异常处理，返回校验类错误码。

#### 4.4.7 实体类约定

- 所有数据库实体类必须包含审计字段，命名与类型统一：
  | 字段 | 类型 | 说明 |
  |---|---|---|
  | `createTime` | `LocalDateTime` | 创建时间 |
  | `updateTime` | `LocalDateTime` | 更新时间 |
  | `createBy` | `Long` | 创建人 |
  | `updateBy` | `Long` | 更新人 |
  | `deleted` | `Integer` | 删除标记（逻辑删除，`0` 未删 / `1` 已删） |
- 逻辑删除用 MyBatis-Plus `@TableLogic`，查询自动过滤已删除数据，禁止手写 `deleted = 0` 条件。
- 审计字段（创建/更新时间、人）用 MyBatis-Plus `MetaObjectHandler` 自动填充，禁止在业务代码手写赋值。
- 主键统一 `id`，类型 `Long`，ID 策略用雪花算法趋势自增（MyBatis-Plus `IdType.ASSIGN_ID`），禁止依赖数据库自增。

#### 4.4.8 分页查询入参

- 分页查询入参类统一继承 MyBatis-Plus `Page`（或包含同等分页字段），字段名与默认值统一：
  | 字段 | 类型 | 默认 | 说明 |
  |---|---|---|---|
  | `current` | `long` | `1` | 当前页（从 1 开始） |
  | `size` | `long` | `10` | 每页条数 |
- 可选排序字段 `orderBy` / `order` 必须做白名单校验，禁止把排序字段直接拼进 SQL。
- 分页返回统一走 MyBatis-Plus `Page` 对象 `{ records, total, size, current }`（见 4.4.1），禁止各接口自造分页结构。

### 4.5 数据库规范

- 命名：表名、字段名一律小写 + 下划线（`snake_case`），禁止驼峰；表名用业务名词单数形式。
- 主键：统一 `id BIGINT`，雪花趋势自增（与 4.4.7 一致），禁止物理外键，关联用逻辑字段。
- 必备字段：每张业务表包含 4.4.7 定义的审计字段（`create_time / update_time / create_by / update_by / deleted`）。
- 字段类型：金额用 `DECIMAL`、状态/标记用 `TINYINT`、时间用 `DATETIME`、文本用 `VARCHAR`（定长合理，禁止无脑 `TEXT`）。
- 索引：命名 `idx_表名_字段`，唯一索引 `uk_表名_字段`；只为真实查询场景建索引，禁止冗余索引。
- 查询：禁止 `SELECT *`，必须明确列出字段；大表查询必须走索引，禁止全表扫描。
- 约定：字符串字段默认 `NOT NULL` 并给默认值，避免 `NULL` 歧义；逻辑删除字段数据库默认 `0`。

### 4.6 事务规范

- 事务注解 `@Transactional` 只加在 Service 层（接口或实现类），禁止加在 Controller / Mapper。
- 默认只对 `RuntimeException` 回滚，业务异常需回滚时明确声明 `rollbackFor`。
- 禁止在事务内调用远程接口（Feign / RPC / HTTP），避免长事务；远程调用放在事务提交后或事务外。
- 只读查询方法加 `@Transactional(readOnly = true)`（或走只读数据源），禁止无谓写事务。

### 4.7 微服务规范

- 服务命名：小写 + 连字符，语义清晰（如 `user-service / order-service`），注册到 Nacos 用统一命名空间与分组。
- 服务间调用：统一走 Feign（`@FeignClient`），禁止在业务代码里手写 HTTP 调用其他服务。
- 配置管理：环境配置（dev / test / prod）统一托管在 Nacos 配置中心，禁止硬编码环境相关地址、密钥。
- 网关：跨服务请求统一走 Spring Cloud Gateway 路由，前端不直接调业务服务地址；鉴权在网关统一处理（见 4.4.4）。
- 分布式一致性：跨服务业务优先用最终一致性（消息/补偿），禁止随意引入分布式事务强一致方案。

### 4.8 测试规范

- 关键业务路径（核心 Service 逻辑）必须有单元测试；改 bug 先补回归测试再修复。
- 测试命名：`XxxTest` + 方法名描述行为；测试数据自包含，不依赖外部环境与执行顺序。
- 接口层用 `MockMvc` 做集成测试；禁止为凑覆盖率写无断言的空测试。
- 提交前必须跑通本地测试与 lint，测试失败不得提交。

## 5. 协作红线（AI 工具必须遵守）

1. 不可逆操作（数据库写、migration、seed、改写 git 历史、改依赖锁文件）必须先征询。
2. 不擅自修改 `openspec/` 下的制品文件。
3. 不添加未被要求的功能、抽象或重构。
4. 复用接口/枚举/字段前，先确认真实契约，不凭推导实现。

## 6. 规范驱动开发（SDD）

本项目采用 OpenSpec（schema：`spec-driven`）驱动开发，命令入口见
`.claude/commands/opsx/`（Trae 侧等价技能见 `.trae/skills/openspec-*`）。

- 制品权威性：`proposal / specs / design / tasks` 为唯一指令源，实现必须以制品为准，禁止绕过制品直接改代码。
- 工作流入口（四个命令，按顺序使用）：
  - `/opsx:explore` — 探索/澄清需求，只思考不实现。
  - `/opsx:propose <change>` — 创建 change 并生成 `proposal / design / tasks` 制品。
  - `/opsx:apply <change>` — 按 `tasks` 逐条实现，完成后勾选任务状态位。
  - `/opsx:archive <change>` — 变更完成并验证后归档。
- 状态查询：`openspec list --json` / `openspec status --change "<name>" --json`。
- 铁律：只改 `tasks` 状态位，不擅自改制品定义；未归档前，避免直接修改对应代码。

---

> 维护约定：本文件变更需随对应代码变更一并提交；结构增删需在 PR 中说明理由。