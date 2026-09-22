# Tasks: add-blog-foundation-rbac

## 1. 工程与 blog-common 基础契约

- [x] 1.1 创建 `blog-common` / `blog-gateway` / `blog-content-service` Maven 模块（blog 不依赖 platform-common）
- [x] 1.2 `blog-common` §6.4 契约：Result/分页、错误码与 BizException、全局异常、PageQuery 与排序白名单
- [x] 1.3 `blog-common` 审计与序列化：BaseEntity、MetaObjectHandler、CurrentUser、14 位 IdService、Long→String
- [x] 1.4 `blog-common` 认证与文档：JwtVerifier（同 auth iss）、UserHeaders、Swagger 公共配置
- [x] 1.5 网关 JWT 过滤：验签、401、透传 X-User-Id；public 路径放行清单

## 2. 前端壳（design D5 · 统一认证 SSO）

- [x] 2.1 `blog-admin` 工程壳：Vite+Vue3+TS+Pinia+EP；目录 api/views/stores/router/types/utils
- [x] 2.2 `blog-admin` 统一 axios 解包 + token 策略（对齐 auth-portal）；id 为 string
- [x] 2.3 `blog-admin` 布局与 RBAC 页骨架（角色/权限/用户-角色），权限码对齐 BlogPermissions
- [x] 2.4 `blog-portal` 匿名壳：公开 API 客户端 + 首页占位
- [ ] 2.5 **SSO 登录**：去掉本地账密页；未登录跳 `/auth/sso/authorize`，callback 换 JWT

## 3. RBAC 与投影

- [x] 3.1 `blog_db` 迁移 RBAC + `sys_user_ref` + `sys_sequence`
- [x] 3.2 角色/权限/用户-角色管理接口（system_code=blog）
- [x] 3.3 权限码鉴权注解/切面进 blog-common；权限常量对齐 sys_permission
- [x] 3.4 登录 upsert user_ref；作者展示 batch 补洞
- [ ] 3.5 Redis：权限码缓存 + 赋权失效（blog-content-service）

## 4. 种子与验证

- [x] 4.1 local/test 种子角色与权限码（blog_admin/author 等）
- [x] 4.2 单测：赋权幂等、无权限 403、投影失败不影响授权、返回体/分页/Long 序列化
- [x] 4.3 确认无 password 字段、无物理 FK；业务模块无平行 Result/异常/分页实现
