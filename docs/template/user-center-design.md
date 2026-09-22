# 用户中心 · 设计说明

> 状态：已确认与认证中心**拆分**  
> 范围：用户主数据、账号生命周期、用户查询接口  
> 配套：`unified-auth-center-design.md`、`user-center-database.md`  
> 约束：`CLAUDE.md`（独立系统、common/framework、Entity 归属、密钥分级）

---

## 1. 目标与边界

### 1.1 目标

作为全仓多系统的**用户账号权威**：

- 账号创建、启停、锁定、资料维护
- 按 id / username 查询，供认证中心与业务系统使用
- 向业务系统提供稳定 `user.id`（= OIDC `sub`）

### 1.2 边界

| 在用户中心 | 不在用户中心 |
|------------|--------------|
| `sys_user` 主数据、资料 | **密码 / 凭证**（认证中心） |
| 账号状态与生命周期 | 会话、Access/Refresh Token |
| 对内用户查询/管理 API | OAuth Client 注册 |
| （可预留）组织/部门 | 细粒度业务权限码、业务扩展资料 |

**禁止**存储 `password_hash`；**禁止**实现登录验密。

---

## 2. 系统关系

```text
  业务系统 SPA/后端          认证中心
        │                     │
        │ CRUD/查询用户        │ 登录：username→user
        │ 同步投影            │ userinfo：profile
        ▼                     ▼
  ┌──────────────────────────────────┐
  │  用户中心 backend/user            │
  │  user_db                          │
  └──────────────────────────────────┘
```

| 调用方 | 用途 | 协议 |
|--------|------|------|
| 认证中心 | `getUserByUsername` / `getUser` / `getUserProfile` | Feign / HTTP + DTO（`user-api`） |
| 业务系统 | 列表、详情、搜索；或同步到本地投影 | 同上 |
| 用户中心管理端 | 账号管理 UI（见 §5 前端设计） | `user-service` |

- 跨系统**只**走接口与 DTO，**禁止**共享 Entity（CLAUDE.md §5.6.1）。
- 契约模块建议：`user-api`（Feign 接口 + DTO/VO + Fallback），无 Entity/Mapper。

---

## 3. 核心能力

| 能力 | 说明 |
|------|------|
| 账号创建 | 分配 14 位 `id`；`username` 全局唯一；初始状态 |
| 启停/锁定 | `status` 变更；通知认证中心吊销 RT（可选事件/管理端操作） |
| 资料维护 | 姓名、昵称、头像、邮箱、手机（脱敏出参） |
| 查询 | `by-id` / `by-username` / 分页列表 |
| 管理 API/UI | 用户中心自有管理端 `frontend/user-admin`（与认证「客户端后台」分离，见 §5） |

**建号与凭证：** 用户中心创建成功后，调用认证中心「初始化/重置凭证」接口，或由管理端分别操作；**最终一致**，不在两库开分布式事务。

---

## 4. 接口草案（`/api/v1`）

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/users/{id}` | 按 id 查基础信息 |
| GET | `/users/by-username/{username}` | 登录解析 |
| GET | `/users/{id}/profile` | OIDC / 展示用资料 |
| GET | `/users` | 分页列表（管理） |
| POST | `/users` | 创建账号（管理） |
| PUT | `/users/{id}` | 更新资料/状态（管理） |
| POST | `/users/{id}/status` | 启停/锁定（管理） |

- 统一 `{code,msg,data}`；`id` 出参为 **string**。
- 认证中心调用以 `by-username` / `profile` 为主；管理写接口需鉴权（管理端自身 OIDC 登录）。

---

## 5. 前端设计

用户中心对外提供**管理端**前端；不提供 C 端注册/登录页（登录在认证中心）。

| 工程 | 域名/路径（示例） | 角色 | OIDC |
|------|-------------------|------|------|
| `frontend/user-admin` | `user-admin.example.com` 或统一运营台子路由 | 用户账号/资料管理 | 公开客户端 + **PKCE**（与业务 SPA 同模式，AT/RT 可测） |

**技术栈：** Vue3 + Vue Router + TypeScript + Pinia + Element Plus；`<script setup lang="ts">`；对齐 `CLAUDE.md` §5.5。

### 5.1 信息架构

| 菜单 | 功能 |
|------|------|
| 用户列表 | 分页、按 username/状态/部门筛选 |
| 用户详情/编辑 | 资料、状态、预留部门 |
| 新建用户 | 创建账号（成功后引导/触发认证侧初始化凭证） |
| 启停/锁定 | `status` 变更；可选「同时踢下线」（调认证中心吊销） |
| 操作审计 | `user_audit_log` |

### 5.2 目录结构

```text
frontend/user-admin/
├── package.json
└── src/
    ├── api/          # users.ts（CRUD、status）、auth 续期工具
    ├── views/        # users/、audit/
    ├── components/   # layout / common / business
    ├── stores/       # user（当前操作员）、token
    ├── router/       # meta: title/icon/hidden/requiresAuth
    ├── types/        # api / model（id: string）
    ├── utils/
    ├── styles/
    └── locales/
```

### 5.3 交互与约定

- 登录：跳转认证中心授权码 + PKCE；本端**不**自建账密页。
- 请求：只调 `src/api`；统一解包 `{code,msg,data}`；主键/关联 id 用 **string**。
- 列表/表单：Element Plus 表格与表单；样式 scoped。
- 创建用户后的凭证初始化：管理端操作员触发（调认证管理 API），或用户中心后端编排；前端只体现结果与提示。
- 启停并吊销会话：先改 `user.status`，再调认证「按用户踢下线」；失败需明确提示重试。

### 5.4 与后端边界

| 能力 | 后端 |
|------|------|
| 用户 CRUD/状态 | `user-service` 管理 API |
| 凭证初始化/重置、踢下线 | `auth-service` 管理 API（可由 user-admin 组合调用，或仅认证 admin 操作） |
| 当前操作员身份 | JWT `sub`（操作者本人），与被管理用户分离 |

---

## 6. 模块与库

- 工程：`backend/user/`：`user-common`、`user-framework`、`user-api`、`user-service`
- 前端：`frontend/user-admin/`（见 §5）
- 库：**`user_db`**（表见 `user-center-database.md`）
- 配置：`application.yml` + `application-dev.yml` + `application-test.yml`
- 组件连接信息：`docs/test-env.md`

---

## 7. 非目标

密码与登录、SSO、令牌、OAuth Client、RBAC、跨系统直连库表。

---

## 8. 相关文档

| 文档 | 内容 |
|------|------|
| `unified-auth-center-design.md` | 认证中心（调用本系统） |
| `user-center-database.md` | 库表 |
| `CLAUDE.md` | 全局契约 |
