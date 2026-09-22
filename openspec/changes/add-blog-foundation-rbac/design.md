# Design: add-blog-foundation-rbac

## Context

- 统一认证与 14 位 ID/审计规范已定；权限**各系统自管**。
- blog：`system_code=blog`；匿名前台 + 后台登录管理。

## Goals / Non-Goals

**Goals:** 骨架、网关 AuthN、本地 RBAC、user_ref、权限码鉴权。  
**Non-Goals:** 文章业务、评论、file 上传实现、数据范围过滤引擎。

## Decisions

### D1. 工程
```
backend/blog/
  blog-common/            Result/验签/权限常量
  blog-gateway/           仅 JWT → X-User-Id
  blog-content-service/   首期唯一业务进程
```

### D2. RBAC 本地
表同基础 schema；`user_id` 逻辑指向认证中心；关联物理删；`data_scope` 预留。

### D3. 鉴权
网关 AuthN；服务 `@RequiresPermission`；`/api/v1/public/**` 放行清单。

### D4. 投影
进入 blog upsert `sys_user_ref`；展示 batch 补洞；**不用** ref.status 放行。

## Risks
- [权限码漂移] → blog-common 常量统一。  
- [公开写滥用] → 写接口强制 JWT；评论限流在业务变更。

## Migration Plan
1. 建模块与 RBAC 表。 2. 配置 JWT。 3. 种子角色权限。

## Open Questions
1. 内置角色命名（blog_admin / blog_author）。 2. 匿名评论策略细节。
