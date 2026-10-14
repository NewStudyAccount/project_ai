## Purpose

管理并对外暴露用户端站点配置（站名、描述、备案/版权、关于文案），支撑 portal 首页与关于页文案来源。

## ADDED Requirements

### Requirement: 公开读取站点配置
系统 SHALL 提供 `GET /api/v1/public/site-config`：返回预置键映射（至少 `title`、`description`、`copyright`、`about`）；无需登录；可选缓存（须 TTL）。

#### Scenario: 读取默认站点信息
- **WHEN** 访客打开用户端首页
- **THEN** 可获得站点标题/描述/版权/关于文案

### Requirement: 管理端读写站点配置
系统 SHALL 提供 `GET /api/v1/admin/site-config`（权限 `blog:site:list`）与 `PUT /api/v1/admin/site-config`（权限 `blog:site:update`，`@Idempotent`）：按 `config_key` UPSERT `blog_site_config`；已知键至少含 `site.title` / `site.description` / `site.copyright` / `site.about`；写操作记审计（`target_type=SITE_CONFIG`）。

#### Scenario: 更新站点标题
- **WHEN** 管理员提交 `site.title` 新值
- **THEN** UPSERT 成功并记审计；公开接口返回新值

#### Scenario: 无权限
- **WHEN** 无 `blog:site:update` 的登录用户提交保存
- **THEN** 返回 HTTP 403
