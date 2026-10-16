# blog-system · 设计文档索引

> 本目录是 **blog 系统内容业务**的设计文档集合（独立文档，非 openspec 提案）。
> 契约口径以根目录 `CLAUDE.md` 为准；对象存储策略以 `docs/template/object-storage-design.md` 为准。

| 文档 | 职责 | 状态 |
|------|------|------|
| [business-design.md](./business-design.md) | 业务逻辑总览：领域模型、状态机、接口、两端前端 | 设计稿 |
| [table-design.md](./table-design.md) | **库表契约**：新增/修改清单、字段、索引、种子 | 设计稿 |
| [api-design.md](./api-design.md) | **接口文档**：接口名、入参、出参、逻辑、涉及表 | 设计稿 |
| [er-diagram.html](./er-diagram.html) | 表关系 ER 图（浏览器打开） | 与 table-design 同步 |
| [prototype.html](./prototype.html) | 管理端 + 用户端页面原型（浏览器打开） | 布局参照，效力低于设计正文 |

**阅读顺序：** business-design → table-design → api-design → er-diagram → prototype。

**效力顺序：** `CLAUDE.md`（契约） > `table-design.md` / `business-design.md`（本目录正文） > HTML（ER/原型，仅展示与交互参照）。

---

## 范围一句话

文章分类 / 标签 / 主表 / 详细表；正文存 OSS；扩展 `blog-admin`；新建 `blog-portal` 用户端。

## 相关既有材料（不在本目录）

| 路径 | 关系 |
|------|------|
| `docs/template/object-storage-design.md` | MinIO/预签名/逻辑删除唯一裁剪源 |
| `docs/template/rbac-design.md` | 权限标识与菜单短号 |
| `docs/template/admin-ui-design.md` | 管理端视觉（云白） |
| `openspec/changes/scaffold-blog-system/` | 骨架横切能力（已落地边界） |
| `deploy/db/migration/blog_db/` | 既有表 SQL；内容表 SQL 落此处（实施时） |
