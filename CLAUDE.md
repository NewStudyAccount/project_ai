# 项目全局规范

> 本文件是项目唯一的事实来源（Single Source of Truth），面向所有 AI 编码工具
> （Cursor、Claude Code、Windsurf、Codex、Trae 等）与人类协作者。
> 所有 AI 工具在编写、修改代码前必须先读取并遵循本文件。

---

## 1. 项目概述

> 请填写以下信息，帮助 AI 工具理解项目背景

- 项目名称：（填写项目名称）
- 业务目标：（一句话描述项目要解决的业务问题）
- 目标用户：（描述主要用户群体）
- 项目边界：（明确项目不做什么，避免功能蔓延）

## 2. 技术栈

- 前端：Vue3 + Vue Router + TypeScript + Pinia + Element Plus
- 后端：Java 21 + Spring Boot + Spring Security + MyBatis-Plus + Spring Cloud Gateway + Nacos
- 数据库：MySQL
- 缓存：Redis
- 构建：前端 Vite / 后端 Maven
- 包管理：npm（前端）

## 3. 环境配置与启动

### 3.1 开发环境要求

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21+ | 后端运行环境 |
| Node.js | 18+ | 前端运行环境 |
| npm | 9+ | 前端包管理 |
| MySQL | 8.0+ | 数据库 |
| Redis | 6.0+ | 缓存 |
| Nacos | 2.2+ | 注册中心/配置中心 |

### 3.2 启动命令

```bash
# 后端启动
cd backend
mvn clean install -DskipTests
mvn spring-boot:run -pl <服务名>

# 前端启动
cd frontend
npm install
npm run dev

# 启动基础设施（Docker Compose）
docker-compose up -d mysql redis nacos
```

### 3.3 环境变量管理

- 环境变量统一通过 `.env` 文件管理，禁止硬编码在代码中
- `.env` 文件禁止提交到 Git（已在 `.gitignore` 中排除）
- 敏感配置（密钥、密码）统一走 Nacos 配置中心
- 环境变量命名：`SERVICE_NAME_ENV_KEY` 大写下划线格式

## 4. 目录结构

<!-- 前后端分仓或分目录，按实际落地后更新 -->

```
.
├── frontend/           # 前端（Vue3 + TS + Pinia）
├── backend/            # 后端（Spring Boot 微服务，含 Gateway/Nacos）
├── docs/               # 文档
├── openspec/           # 规范驱动开发制品（勿手改，见第 7 节）
└── CLAUDE.md           # 本文件
```

## 5. 项目结构设计

### 5.1 前端架构设计

#### 5.1.1 页面组件结构

```
src/
├── api/                # 接口请求层
│   ├── modules/        # 按业务模块划分
│   │   ├── user.ts     # 用户模块接口
│   │   ├── order.ts    # 订单模块接口
│   │   └── ...
│   └── index.ts        # 导出统一接口
├── views/              # 页面视图层
│   ├── user/           # 用户模块页面
│   │   ├── list/       # 列表页
│   │   │   ├── index.vue
│   │   │   └── components/
│   │   ├── detail/     # 详情页
│   │   └── components/ # 模块内组件
│   ├── order/          # 订单模块页面
│   └── ...
├── components/         # 公共组件
│   ├── layout/         # 布局组件
│   ├── common/         # 通用组件
│   └── business/       # 业务公共组件
├── stores/             # Pinia 状态管理
│   ├── modules/        # 按业务模块划分
│   └── index.ts
├── router/             # 路由配置
│   ├── index.ts
│   └── modules/        # 按业务模块划分
├── types/              # TypeScript 类型定义
│   ├── api/            # 接口返回类型
│   ├── model/          # 业务模型类型
│   └── common/         # 通用类型
├── utils/              # 工具函数
├── styles/             # 全局样式
├── locales/            # 国际化语言包
└── App.vue
```

#### 5.1.2 组件设计原则

| 原则 | 说明 |
|------|------|
| 单一职责 | 一个组件只做一件事，复杂组件拆分为子组件 |
| Props 向下 | 父组件通过 props 传递数据，禁止直接修改子组件状态 |
| Events 向上 | 子组件通过 emit 通知父组件，禁止反向引用 |
| 插槽扩展 | 使用插槽（slot）实现组件扩展，避免 props 爆炸 |
| 组合式优先 | 使用 `<script setup>` + Composition API，禁止 Options API |

#### 5.1.3 状态管理设计

```
stores/
├── user/               # 用户模块 Store
│   ├── index.ts        # 用户状态
│   ├── types.ts        # 类型定义
│   └── actions.ts      # 异步操作
├── app/                # 应用全局 Store
│   ├── index.ts
│   └── types.ts
└── index.ts            # 导出所有 Store
```

**Store 设计规范：**
- 状态：只存放需要跨组件共享的数据
- Getters：计算属性，派生状态
- Actions：异步操作（API 调用），同步操作直接修改 state
- 命名：`use{Module}Store`，如 `useUserStore`

#### 5.1.4 路由设计

```typescript
// router/modules/user.ts
import type { RouteRecordRaw } from 'vue-router'

const userRoutes: RouteRecordRaw[] = [
  {
    path: '/user',
    name: 'User',
    component: () => import('@/views/user/index.vue'),
    meta: { title: '用户管理', icon: 'User' },
    children: [
      {
        path: 'list',
        name: 'UserList',
        component: () => import('@/views/user/list/index.vue'),
        meta: { title: '用户列表' }
      },
      {
        path: 'detail/:id',
        name: 'UserDetail',
        component: () => import('@/views/user/detail/index.vue'),
        meta: { title: '用户详情', hidden: true }
      }
    ]
  }
]

export default userRoutes
```

**路由规范：**
- 路由文件按模块划分，统一在 `router/modules/` 目录
- 路由 name 使用 PascalCase，如 `UserList`、`OrderDetail`
- 路由 meta 包含：`title`（标题）、`icon`（图标）、`hidden`（是否隐藏）、`requiresAuth`（是否需要登录）

### 5.2 后端架构设计

#### 5.2.1 分层架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      前端 (Vue3)                            │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Cloud Gateway                      │
│              （路由转发、鉴权、限流、日志）                     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     业务服务集群                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ user-service │  │order-service│  │ ... -service │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      数据层                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │    MySQL     │  │    Redis    │  │    Nacos    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

#### 5.2.2 服务内部分层

```
src/main/java/com/example/{module}/
├── controller/         # 接口层
│   ├── dto/           # 请求 DTO
│   └── vo/            # 响应 VO
├── service/            # 业务层
│   ├── impl/          # 实现类
│   └── bo/            # 业务对象
├── mapper/             # 数据访问层
│   └── entity/        # 实体类
├── config/             # 配置类
├── constant/           # 常量
├── enums/              # 枚举
├── exception/          # 异常定义
└── util/               # 工具类
```

**分层调用规则：**
```
Controller → Service → Mapper
    │           │
    │           └── Service 之间可以相互调用
    │
    └── 禁止 Controller 直接调用 Mapper
```

#### 5.2.3 微服务拆分原则

| 原则 | 说明 |
|------|------|
| 单一职责 | 一个服务只负责一个业务领域（如用户、订单、商品） |
| 高内聚 | 相关功能放在同一服务，不相关的拆分到不同服务 |
| 低耦合 | 服务间通过 API 通信，禁止共享数据库 |
| 独立部署 | 每个服务可独立部署、升级、扩缩容 |
| 数据自治 | 每个服务拥有自己的数据库，禁止跨服务直接访问 |

**服务拆分示例：**

| 服务名 | 职责 | 核心表 |
|--------|------|--------|
| `user-service` | 用户管理、认证授权 | `sys_user`, `sys_role`, `sys_menu` |
| `order-service` | 订单管理、支付 | `biz_order`, `biz_order_item` |
| `product-service` | 商品管理、库存 | `biz_product`, `biz_sku` |
| `system-service` | 系统配置、字典 | `sys_dict`, `sys_config` |

#### 5.2.4 服务间调用规范

```java
// Feign 客户端定义
@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {
    
    @GetMapping("/api/v1/users/{id}")
    Result<UserVO> getUserById(@PathVariable("id") Long id);
}

// 服务调用
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    
    private final UserClient userClient;
    
    @Override
    public OrderVO getOrder(Long orderId) {
        // 调用用户服务获取用户信息
        Result<UserVO> userResult = userClient.getUserById(order.getUserId());
        // ...
    }
}
```

**调用规范：**
- 统一使用 Feign 调用，禁止使用 RestTemplate
- Feign 客户端按服务模块划分，放在 `feign/` 包下
- 必须配置降级（Fallback），避免服务雪崩
- 调用超时设置合理（连接超时 3s，读取超时 10s）

### 5.3 数据库设计规范

#### 5.3.1 表设计原则

| 原则 | 说明 |
|------|------|
| 原子性 | 字段不可再分，如「地址」应拆分为省、市、区、详细地址 |
| 一致性 | 同一字段在不同表中类型、长度、命名一致 |
| 冗余控制 | 适当冗余提升查询性能，但避免数据不一致 |
| 命名规范 | 表名、字段名小写 + 下划线，禁止驼峰 |
| 审计字段 | 每张表必须包含创建时间、更新时间、创建人、更新人、删除标记 |

#### 5.3.2 表关系设计

**一对一关系：**
```sql
-- 用户基本信息（主表）
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    ...
);

-- 用户扩展信息（扩展表）
CREATE TABLE sys_user_detail (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,  -- 关联用户ID
    avatar VARCHAR(255),
    ...
);
```

**一对多关系：**
```sql
-- 订单主表
CREATE TABLE biz_order (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ...
);

-- 订单明细表（多端）
CREATE TABLE biz_order_item (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,  -- 关联订单ID
    product_id BIGINT NOT NULL,
    ...
);
```

**多对多关系：**
```sql
-- 用户表
CREATE TABLE sys_user (...);

-- 角色表
CREATE TABLE sys_role (...);

-- 用户角色关联表
CREATE TABLE sys_user_role (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
);
```

#### 5.3.3 字段设计规范

| 字段类型 | 规范 | 示例 |
|---------|------|------|
| 主键 | `BIGINT`，日期+序列号策略 | `26092100000001` |
| 金额 | `DECIMAL(10,2)`，禁止用 `FLOAT` | `99.99` |
| 状态 | `TINYINT`，枚举值 | `0` 未激活 / `1` 已激活 |
| 时间 | `DATETIME`，不用 `TIMESTAMP` | `2026-09-21 14:30:00` |
| 文本 | `VARCHAR` 定长，禁止无脑 `TEXT` | `VARCHAR(255)` |
| 布尔 | `TINYINT(1)`，`0`/`1` | `0` false / `1` true |

#### 5.3.4 索引设计规范

```sql
-- 索引命名
CREATE INDEX idx_user_username ON sys_user(username);      -- 普通索引
CREATE UNIQUE INDEX uk_user_email ON sys_user(email);     -- 唯一索引
CREATE INDEX idx_order_user_time ON biz_order(user_id, create_time);  -- 组合索引
```

**索引原则：**
- 仅为 `WHERE`、`JOIN`、`ORDER BY` 中的字段建索引
- 选择性高的字段放前面（如 `user_id` 在前，`status` 在后）
- 单表索引不超过 5 个
- 禁止冗余索引（如已有 `idx_a_b`，不再建 `idx_a`）

### 5.4 数据字典规范

#### 5.4.1 字典表设计

```sql
-- 数据字典表
CREATE TABLE sys_dict (
    id BIGINT PRIMARY KEY,
    dict_type VARCHAR(100) NOT NULL COMMENT '字典类型',
    dict_code VARCHAR(100) NOT NULL COMMENT '字典编码',
    dict_label VARCHAR(100) NOT NULL COMMENT '字典标签',
    dict_value VARCHAR(100) NOT NULL COMMENT '字典值',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态（0禁用 1启用）',
    UNIQUE KEY uk_dict_code (dict_type, dict_code)
);
```

#### 5.4.2 常用字典类型

| 字典类型 | 说明 | 示例值 |
|---------|------|--------|
| `sys_status` | 通用状态 | 0-禁用, 1-启用 |
| `sys_gender` | 性别 | 0-未知, 1-男, 2-女 |
| `sys_yes_no` | 是否 | 0-否, 1-是 |
| `biz_order_status` | 订单状态 | 0-待支付, 1-已支付, 2-已完成, 3-已取消 |

---

## 6. 开发规范

### 6.1 编码规范

- 命名：前端变量/函数 `camelCase`、组件 `PascalCase`、常量 `UPPER_SNAKE`；
  后端类名 `PascalCase`、方法/变量 `camelCase`、常量 `UPPER_SNAKE`。
- 格式化：前端 Prettier + ESLint；后端遵循 Spring 官方代码风格（IDE 统一格式化）。
- 注释语言：中文（与提交语言一致）。
- 错误处理：统一走 6.4.3 全局异常处理，前后端均禁止吞异常。

### 6.1.1 前端代码规范

- 组件风格：统一使用组合式 API（`<script setup lang="ts">`），禁止混用 Options API。
- 组件命名：文件名与组件名用 `PascalCase`，单文件组件（SFC）一个文件只放一个组件。
- 目录约定：`src/api`（接口请求）、`src/views`（页面）、`src/components`（通用组件）、`src/stores`（Pinia）、`src/utils`（工具函数）、`src/types`（TS 类型）。
- 状态管理：跨组件共享状态一律走 Pinia，禁止用全局变量或事件总线传状态。
- 路由：路由配置集中维护，动态路由与权限需与后端权限标识一致，不硬编码角色。
- 样式：优先用 Element Plus 组件与 scoped 样式，禁止内联样式堆叠；统一主题变量写在全局样式文件。
- UI 组件：统一使用 Element Plus 组件，禁止自造重复轮子；图标用 Element Plus 图标库。
- TypeScript：页面取值处逐字段对照接口真实签名定义类型，禁止用 `any` 绕过类型检查。
- Long 类型处理：后端返回的 `Long` 类型字段（如 `id`）在前端定义为 `string`，禁止用 `number`，避免精度丢失。
- 请求封装：统一走 `src/api` 封装 + axios 拦截器，业务组件不直接调 axios。

### 6.1.2 后端代码规范

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

### 6.2 接口与数据模型

- 接口契约来源：以后端真实 Controller 签名与 API 文档为准，前端按真实字段取值。
- 枚举/字段以什么为准：后端枚举类与数据库表结构为准，禁止前端自造枚举值。
- RESTful 风格：URL 用小写 + 连字符（kebab-case），资源用名词复数（如 `/users`）；HTTP 方法语义正确（查询 `GET`、新增 `POST`、修改 `PUT`、删除 `DELETE`）。
- 版本号：接口路径带版本前缀（如 `/api/v1/...`），破坏性变更才升级版本。
- 命名：接口方法见名知义（`getXxx / listXxx / createXxx / updateXxx / deleteXxx`），禁止语义模糊的方法名。

### 6.3 提交规范

- 格式：`type(scope): subject`，type ∈ `feat / fix / refactor / docs / chore / test / perf / ci / style`。
- subject 用中文祈使句、≤ 50 字符，不写句号。
- 单人开发：直接提交到 `main`，不强制分支与 PR 评审；较大功能可自行开分支隔离。
- 提交粒度：一个提交只做一件事，禁止混合多种变更。
- 提交前自查清单：
  - [ ] 代码能编译通过
  - [ ] 测试全部通过
  - [ ] 无 `console.log` / `System.out.println` 调试代码
  - [ ] 无敏感信息泄露（密码、密钥、Token）
  - [ ] 代码符合项目编码规范
  - [ ] 提交信息格式正确

### 6.4 基础代码规范

> 本节只约定「契约」，不写具体实现。基础代码在脚手架初始化后作为第一批落地，
> 落地实现必须符合以下契约。

#### 6.4.1 统一返回体

- 后端所有接口统一返回结构 `{ code, msg, data }`，`code` 为业务错误码，`msg` 为提示信息，`data` 为业务数据。
- 分页场景统一返回 `{ records, total, size, current }`（MyBatis-Plus `Page` 对象），新接口沿用既有约定。
- 前端 axios 拦截器统一解包，禁止在业务代码里逐处判断返回结构。

#### 6.4.2 错误码

- 错误码用数字分段区分类型，分段固定如下：
  | 分段 | 含义 |
  |---|---|
  | `1xxxx` | 系统异常 |
  | `2xxxx` | 业务异常 |
  | `3xxxx` | 参数校验异常 |
- 错误码与提示信息通过枚举/常量维护，禁止在业务代码里硬编码错误码字符串。

#### 6.4.3 全局异常处理

- 后端用 `@RestControllerAdvice` 统一捕获异常，分类处理：业务异常、参数校验异常、系统异常。
- 铁律：禁止吞异常；禁止把堆栈 / 内部实现细节返回给前端；系统异常统一返回友好提示并记日志。
- 前端统一 axios 拦截器处理错误，按 `code` 分流（如登录失效跳转、业务错误弹提示、系统错误通用提示）。

#### 6.4.4 数据校验

- 入参校验统一用 Bean Validation（`@Valid` / 校验注解），禁止在 Controller / Service 里手写重复的 if 校验。
- 校验失败统一走全局异常处理，返回校验类错误码。

#### 6.4.5 实体类约定

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
- 主键统一 `id`，类型 `Long`（数据库 `BIGINT`），ID 策略：日期(6位 `yyMMdd`) + 序列号(8位)，共14位（如 `26092100000001`），兼顾可读性与简洁性。禁止依赖数据库自增。
- 序列号由数据库序列表维护，按日期自动递增，支持单表每日千万级数据量。

#### 6.4.6 分页查询入参

- 分页查询入参类统一继承 MyBatis-Plus `Page`（或包含同等分页字段），字段名与默认值统一：
  | 字段 | 类型 | 默认 | 说明 |
  |---|---|---|---|
  | `current` | `long` | `1` | 当前页（从 1 开始） |
  | `size` | `long` | `10` | 每页条数 |
- 可选排序字段 `orderBy` / `order` 必须做白名单校验，禁止把排序字段直接拼进 SQL。
- 分页返回统一走 MyBatis-Plus `Page` 对象 `{ records, total, size, current }`（见 6.4.1），禁止各接口自造分页结构。

#### 6.4.7 Long 类型序列化

- 后端返回的 `Long` 类型字段（如主键 `id`）必须序列化为 `String`，避免前端 JavaScript 精度丢失（JS 安全整数最大值 `2^53-1`，16位）。
- 后端：通过全局配置 `ObjectMapper` 统一处理，禁止在字段上添加 `@JsonSerialize` 注解。
- 前端：接收 `Long` 类型字段的类型定义为 `string`，禁止用 `number`。
- 请求传参：前端向后端传递 `Long` 类型参数时用 `string`，后端使用 `@JsonProperty` 或自定义转换器接收。

```java
// 全局配置：Long -> String 序列化
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        mapper.registerModule(module);
        return mapper;
    }
}
```

### 6.5 数据库规范

- 命名：表名、字段名一律小写 + 下划线（`snake_case`），禁止驼峰；表名用业务名词单数形式。
- 主键：统一 `id BIGINT`，策略为日期(6位 `yyMMdd`) + 序列号(8位)，共14位（与 6.4.5 一致），禁止物理外键，关联用逻辑字段。
- 必备字段：每张业务表包含 6.4.5 定义的审计字段（`create_time / update_time / create_by / update_by / deleted`）。
- 字段类型：金额用 `DECIMAL`、状态/标记用 `TINYINT`、时间用 `DATETIME`、文本用 `VARCHAR`（定长合理，禁止无脑 `TEXT`）。
- 索引：命名 `idx_表名_字段`，唯一索引 `uk_表名_字段`；只为真实查询场景建索引，禁止冗余索引。
- 查询：禁止 `SELECT *`，必须明确列出字段；大表查询必须走索引，禁止全表扫描。
- 约定：字符串字段默认 `NOT NULL` 并给默认值，避免 `NULL` 歧义；逻辑删除字段数据库默认 `0`。

### 6.6 事务规范

- 事务注解 `@Transactional` 只加在 Service 层（接口或实现类），禁止加在 Controller / Mapper。
- 默认只对 `RuntimeException` 回滚，业务异常需回滚时明确声明 `rollbackFor`。
- 禁止在事务内调用远程接口（Feign / RPC / HTTP），避免长事务；远程调用放在事务提交后或事务外。
- 只读查询方法加 `@Transactional(readOnly = true)`（或走只读数据源），禁止无谓写事务。

### 6.7 安全规范

#### 6.7.1 SQL 注入防护

- 所有 SQL 查询必须使用参数化查询（MyBatis `#{}`），禁止使用 `${}` 拼接用户输入。
- 动态表名/字段名使用白名单校验，禁止直接拼接。

#### 6.7.2 XSS 防护

- 前端：用户输入在渲染前必须转义，使用 Vue 的 `v-text` 而非 `v-html`（除非明确需要富文本）。
- 后端：返回给前端的数据在必要时进行 HTML 实体转义。

#### 6.7.3 敏感数据处理

- 密码存储：使用 BCrypt 或 Argon2 单向加密，禁止明文存储。
- 敏感字段（手机号、身份证）在日志和接口返回中脱敏处理。
- 敏感配置（密钥、密码）禁止写入代码或提交到 Git，统一走 Nacos 配置中心。

#### 6.7.4 认证与授权

- Token 统一使用 JWT，设置合理过期时间（Access Token 30分钟，Refresh Token 7天）。
- 接口权限校验在网关层和业务服务层双重校验。
- 登录失败次数限制，超过阈值锁定账号或增加验证码。

#### 6.7.5 接口安全

- 敏感接口（修改密码、支付等）需要二次验证。
- 接口限流：关键接口配置 QPS 限制，防止暴力攻击。
- CORS 配置：仅允许信任的域名访问。

### 6.8 API 文档规范

- 所有接口必须使用 Swagger/OpenAPI 注解（`@Operation`、`@Parameter`、`@Schema`）。
- 接口文档随代码同步更新，禁止手动维护单独的文档文件。
- 接口变更需同步更新 Swagger 注解，保持文档与代码一致。

### 6.9 性能规范

#### 6.9.1 接口性能

- 普通查询接口响应时间 ≤ 200ms。
- 复杂查询/报表接口响应时间 ≤ 1s。
- 慢查询（>1s）必须优化或异步处理。

#### 6.9.2 缓存策略

- 频繁查询且变化不频繁的数据使用 Redis 缓存。
- 缓存 key 规范：`{服务名}:{模块}:{业务标识}`，如 `user:info:1001`。
- 缓存必须设置过期时间，避免内存溢出。
- 缓存更新策略：优先使用「先更新数据库，再删除缓存」。

#### 6.9.3 数据库性能

- 大表查询必须走索引，禁止全表扫描。
- 批量操作使用 `batch insert/update`，禁止循环单条操作。
- 分页查询避免深度分页（`LIMIT 10000, 10`），使用游标分页或覆盖索引。

#### 6.9.4 异步处理

- 耗时操作（邮件发送、文件处理、复杂计算）使用异步任务（MQ 或线程池）。
- 异步任务必须有重试机制和死信队列处理。

### 6.10 微服务规范

- 服务命名：小写 + 连字符，语义清晰（如 `user-service / order-service`），注册到 Nacos 用统一命名空间与分组。
- 服务间调用：统一走 Feign（`@FeignClient`），禁止在业务代码里手写 HTTP 调用其他服务。
- 配置管理：环境配置（dev / test / prod）统一托管在 Nacos 配置中心，禁止硬编码环境相关地址、密钥。
- 网关：跨服务请求统一走 Spring Cloud Gateway 路由，前端不直接调业务服务地址；鉴权在网关统一处理（见 6.7.4）。
- 分布式一致性：跨服务业务优先用最终一致性（消息/补偿），禁止随意引入分布式事务强一致方案。

### 6.11 依赖管理

- 依赖版本锁定：前端 `package-lock.json`、后端 `pom.xml` 中的版本号必须锁定。
- 升级策略：依赖升级前需评估兼容性，重大版本升级需单独测试验证。
- 安全漏洞：定期扫描依赖漏洞（`npm audit` / `mvn dependency-check`），高危漏洞必须及时修复。
- 禁止引入：禁止引入与项目技术栈冲突或维护不活跃的依赖。

### 6.12 监控与健康检查

- 健康检查：每个服务必须暴露 `/actuator/health` 端点。
- 指标暴露：使用 Micrometer 暴露 JVM、HTTP、数据库等指标。
- 告警配置：关键指标（CPU>80%、内存>85%、接口错误率>1%）配置告警。
- 链路追踪：使用 Sleuth + Zipkin 实现分布式链路追踪。

### 6.13 文件上传规范

- 文件大小限制：单文件最大 10MB，批量上传最大 50MB。
- 文件类型白名单：只允许上传指定类型（图片、文档等），禁止上传可执行文件。
- 文件存储：优先使用 OSS/MinIO 对象存储，禁止存储在本地磁盘。
- 文件命名：使用 UUID + 原文件扩展名，禁止使用中文或特殊字符。
- 文件删除：逻辑删除 + 定时清理，禁止立即物理删除。

### 6.14 错误重试与幂等性

- 幂等性设计：所有写操作必须保证幂等性（通过请求ID或业务唯一键）。
- 重试策略：网络请求使用指数退避重试（最多3次，间隔 1s/2s/4s）。
- 重试场景：超时、5xx 错误可重试；4xx 错误（参数错误、权限不足）不重试。
- 幂等键：关键接口（支付、下单）必须携带幂等键（`Idempotent-Key`）。
- 重试日志：重试必须记录日志，包含重试次数、原始请求、失败原因。

### 6.15 测试规范

- 关键业务路径（核心 Service 逻辑）必须有单元测试；改 bug 先补回归测试再修复。
- 测试命名：`XxxTest` + 方法名描述行为；测试数据自包含，不依赖外部环境与执行顺序。
- 接口层用 `MockMvc` 做集成测试；禁止为凑覆盖率写无断言的空测试。
- 提交前必须跑通本地测试与 lint，测试失败不得提交。

### 6.16 枚举与常量规范

- 枚举类统一继承 `BaseEnum<T>` 接口，包含 `code`（编码）和 `desc`（描述）字段。
- 枚举类命名：`XxxEnum`，成员变量全大写下划线（`USER_TYPE_ADMIN`）。
- 常量类统一放在 `constants` 包下，类名 `XxxConstants`，禁止在业务代码中硬编码魔法值。
- 枚举/常量使用：数据库存储编码（`code`），前端展示描述（`desc`），禁止在接口中传递枚举名称。
- 枚举序列化：返回前端时只传 `code`，禁止传整个枚举对象。

### 6.17 配置文件规范

- 配置文件结构：`application.yml` 按功能模块分组（`server`、`spring`、`mybatis-plus`、`logging` 等）。
- 多环境配置：`application-{profile}.yml`（`dev`/`test`/`prod`），公共配置放 `application.yml`。
- 配置项命名：`kebab-case`（如 `spring.datasource.url`），禁止驼峰。
- 敏感配置：密码、密钥等敏感信息禁止写入配置文件，统一走 Nacos 配置中心或环境变量。
- 配置类：使用 `@ConfigurationProperties` 绑定配置，禁止在代码中用 `@Value` 散落注入。
- 配置变更：配置变更需评估影响范围，重大变更需在测试环境验证。

### 6.18 代码审查规范

- 审查维度：功能正确性、代码质量、性能、安全性、可维护性。
- 审查清单：
  - [ ] 是否符合项目规范（CLAUDE.md）
  - [ ] 是否有潜在的性能问题
  - [ ] 是否有安全漏洞（SQL注入、XSS等）
  - [ ] 是否有重复代码可以复用
  - [ ] 单元测试是否覆盖关键路径
  - [ ] 文档/注释是否同步更新
- 审查反馈：问题描述清晰，给出修改建议，不发表主观意见。

### 6.19 国际化规范

- 多语言支持：前端使用 `vue-i18n`，后端使用 `MessageSource`。
- 语言包文件：`src/locales/zh-CN.json`、`src/locales/en-US.json`。
- 硬编码文案：所有用户可见的文案必须使用 i18n key，禁止硬编码中文/英文。
- 日期/数字格式：使用 `Intl` API 或 `dayjs` 的 `locale` 方法格式化。
- 后端提示信息：业务异常的 `msg` 字段使用 i18n key，前端根据语言环境翻译。

## 6. 协作红线（AI 工具必须遵守）

1. 不可逆操作（数据库写、migration、seed、改写 git 历史、改依赖锁文件）必须先征询。
2. 不擅自修改 `openspec/` 下的制品文件。
3. 不添加未被要求的功能、抽象或重构。
4. 复用接口/枚举/字段前，先确认真实契约，不凭推导实现。

## 7. 规范驱动开发（SDD）

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