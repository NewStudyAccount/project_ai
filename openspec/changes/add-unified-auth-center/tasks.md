# Tasks: add-unified-auth-center

## 1. 工程与数据基础

- [x] 1.1 创建单体 `backend/auth/auth-service`（可选 `auth-api` 契约 jar；及共享 `*-common`）Maven/Vite 骨架
- [x] 1.2 迁移 `auth_db`：`sys_sequence`、`sys_user`、`sys_dept`、登录/SSO 日志表
- [x] 1.3 实现 14 位 ID 发号与审计字段自动填充（MetaObjectHandler / TableLogic）
- [x] 1.4 测试环境种子管理员与部门（禁止生产账号入仓）

## 2. 认证核心

- [x] 2.1 账密登录：BCrypt、启停校验、失败限流、登录日志
- [x] 2.2 JWT 签发（Access 30m）与 Refresh（7d，Redis）轮转/吊销
- [x] 2.3 登出、改密后强制失效 sid + refresh
- [x] 2.4 SSO 会话 sid（Redis + Cookie）与 `/sso/authorize` 回跳换 JWT

## 3. 用户只读 API 与账号安全

- [x] 3.1 用户搜索、按 id 批量查询（无凭证字段）
- [x] 3.2 改密/启用停用等账号安全接口
- [x] 3.3 Nginx 直反代认证前缀（不经业务网关）

## 3B. 统一认证前端 auth-portal（必需）

- [x] 3B.1 搭建 `frontend/auth-portal`：Vue3 + Vite + TypeScript + Element Plus
- [x] 3B.2 登录页：账密表单、错误提示、失败锁定文案
- [x] 3B.3 对接 `/auth/login` `/auth/logout` `/auth/refresh`；Access/Refresh 本地策略与 401 引导
- [x] 3B.4 SSO：携带 `client_id`/`return_url` 登录后走 authorize；处理回跳 `code` 并 `POST /auth/sso/token`
- [x] 3B.5 构建产物由 Nginx 静态托管（与 `/auth` API 同域或按域名分流）
- [x] 3B.6 `npm run build` / lint 通过；不在前端存储密码或打印完整 Token

## 4. 子系统接入（首个系统）

- [x] 4.1 common 内 JWT 验签 starter（iss/JWK 统一配置）
- [x] 4.2 `{system}-gateway` 验 JWT 并透传 uid
- [x] 4.3 业务库 RBAC 表：`sys_role`/`sys_permission`/`sys_user_role`/`sys_role_permission`/`sys_user_ref`
- [x] 4.4 赋权/成员列表 + 登录 upsert / batch 补洞投影同步
- [x] 4.5 服务方法级鉴权（permission_code）与菜单按钮查询

## 5. 验证与收尾

- [ ] 5.1 SSO 跨两入口免密、停用即失效、投影失败不影响赋权（含 auth-portal 页面路径）
- [x] 5.2 核心 Service 单测与 MockMvc；前端 build/lint；lint/type-check/mvn test
- [x] 5.3 确认生产密钥不入库、auth 单体不经业务网关、业务不直读 auth 库、未引入外购 IdP/认证微服务拆分
