# 组件版本基线（固定登记表）

> **定位**：全仓库各系统组件版本的**唯一取用处**（对应 `CLAUDE.md` 2.1）。
> 本文件**固定存在、不可删除**：新系统脚手架、新增依赖、版本升级一律以本表为准——
> **先改本表，再改各系统父 pom `dependencyManagement` 落地**；禁止各系统各锁各的版本。
> 版本号已按 Maven Central 实据核验（2026-09-23）。明确不引入的组件见 `CLAUDE.md` 2.2。

## 1. 基线组合（已裁决：Spring Boot 3 线）— 2026-09-23

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | CLAUDE.md 3.1 |
| Spring Boot | 3.5.16 | 跟随 3.5.x 补丁线 |
| Spring Cloud | 2025.0.3 | 2025.0.x 列车（OpenFeign 4.3.3 / Gateway 4.3.5 随 BOM） |
| Spring Cloud Alibaba | 2025.0.0.0 | Nacos Client 3.0.3 / Sentinel 1.8.9 随 BOM |
| MyBatis-Plus | 3.5.17 | `mybatis-plus-spring-boot3-starter` |
| Redisson | 3.52.0 | `redisson-spring-boot-starter`（基线 Boot 3.5.5；4.x 线属 Boot 4，勿用） |
| springdoc-openapi | 2.9.1 | `springdoc-openapi-starter-webmvc-ui`（2.x 线对应 Boot 3） |
| MapStruct | 1.6.3 | 1.7.0 仅 Beta |
| MinIO SDK | 9.0.3 | 如遇 API 变动可退 8.5.17 |
| Spring Authorization Server | 随 Spring Boot BOM（3.5.16 → 1.5.8） | 认证中心 OAuth2/OIDC 协议内核（`spring-boot-starter-oauth2-authorization-server`）；**仅 auth 系统引入**，业务服务禁止依赖 |
| spring-security-oauth2-jose（Nimbus） | 随 Spring Boot BOM | JWT JOSE 栈（与 SAS 同源）；业务服务经 `spring-boot-starter-oauth2-resource-server`（随 Boot BOM）本地验签，不引 SAS |
| RocketMQ | 客户端 5.3.1（SCA BOM 管理） | `rocketmq-spring-boot-starter` 2.3.6；收发与消费重试/死信统一 `framework` 封装（CLAUDE.md 6.11） |
| HikariCP / Lombok / SLF4J+Logback / Micrometer Tracing / MySQL Connector/J / Jackson | 随 Spring Boot 3.5.16 BOM | 不单独锁；实据解析值：HikariCP 6.3.3、Lombok 1.18.46、Logback 1.5.34、SLF4J 2.0.18、micrometer-tracing 1.5.12、mysql-connector-j 9.7.0、jackson-bom 2.21.4 |

**前端主框架**（新增前端工程时取用；`package-lock.json` 按其锁定）：

| 组件 | 版本 | 说明 |
|------|------|------|
| Vue | ^3.5.x | 组合式 API（`<script setup lang="ts">`） |
| Vite | ^6.x | 构建 |
| TypeScript | ~5.7.x | 与 vue-tsc 配套 |
| vue-tsc | ^2.2.x | type-check 用 |
| vue-router | ^4.5.x | |
| Pinia | ^2.3.x | 状态管理 |
| Element Plus | ^2.9.x | 统一 UI 库 |
| axios | ^1.8.x | 经 `src/api` 统一封装 |
| ESLint 9 + typescript-eslint + eslint-plugin-vue / Prettier | 当前稳定线 | lint / format（flat config） |

配套证据：Spring Cloud 2025.0.x ↔ Boot 3.5.x（SC 2025.1.3 BOM 硬钉 `spring-boot.version=4.0.8`，佐证 2025.1 列车 = Boot 4 线）；SCA `2025.0.0.0` ↔ SC 2025.0.x（版本列车命名配套）；`redisson-spring-boot-starter` 3.52.0 的 BOM 基线为 Boot 3.5.5。

## 2. 取用与升级规则

- 各系统父 pom `dependencyManagement` 必须与本表一致；模块依赖不写版本号，一律继承父 pom。
- **升级 = 先改本表**（同步 §3 变更记录），再走变更同步各系统 pom；禁止只改 pom 不改本表。
- 新组件入表前先对照 `CLAUDE.md` 2.2「明确不引入」清单与第 2 节技术栈；冲突须先改 `CLAUDE.md` 再入表。
- 前端依赖同理：新增前端工程时在本表登记主框架版本（Vue3 / Vite / Element Plus / Pinia 等），`package-lock.json` 按其锁定。

## 3. 变更记录

| 日期 | 变更 | 来源 |
|------|------|------|
| 2026-09-23 | 建立基线：Spring Boot 3 线组合（3.5.16 / SC 2025.0.3 / SCA 2025.0.0.0）；MQ=RocketMQ（客户端 5.3.1） | 用户裁决，Maven Central 实据核验 |
| 2026-09-24 | JWT 库改判 spring-security-oauth2-jose（Nimbus，撤销 JJWT）；新增 Spring Authorization Server（仅 auth 引）；AT 默认 10 分钟（5–15 可配） | 用户裁决（SAS 内核同源，避免双 JOSE 栈） |
| 2026-09-24 | 「随 BOM」行补实据解析值（mysql-connector-j 9.7.0 等）；用户中心组件清单按模块锁定 | 用户确认（Spring Cloud 方式：一域一服务 + 独立网关） |
| 2026-09-24 | 新增前端主框架版本行（Vue3/Vite/TS/Pinia/Element Plus 等），随 `scaffold-user-center` 首个前端工程登记 | 规则：新增前端工程时登记（§2） |
