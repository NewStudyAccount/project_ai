# Tasks: add-blog-foundation-rbac

## 1. 工程骨架

- [ ] 1.1 创建 `blog-common` / `blog-gateway` / `blog-content-service` Maven 模块
- [ ] 1.2 创建 `frontend/blog-admin`、`frontend/blog-portal` Vite 壳（Vue3+TS+Pinia+EP）
- [ ] 1.3 网关 JWT 过滤：验签、401、透传 X-User-Id；public 路径放行清单

## 2. RBAC 与投影

- [ ] 2.1 `blog_db` 迁移 RBAC + `sys_user_ref` + `sys_sequence`
- [ ] 2.2 角色/权限/用户-角色管理接口（system_code=blog）
- [ ] 2.3 权限码鉴权注解/切面；权限常量进 blog-common
- [ ] 2.4 登录 upsert user_ref；作者展示 batch 补洞

## 3. 种子与验证

- [ ] 3.1 local/test 种子角色与权限码（blog_admin/author 等）
- [ ] 3.2 单测：赋权幂等、无权限 403、投影失败不影响授权
- [ ] 3.3 确认无 password 字段、无物理 FK
