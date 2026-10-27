# knowledge-base-design · 设计文档索引

> 本目录是 **知识库 + 向量化 + AI 组织展示**的设计文档集合（独立文档，非 openspec 提案）。
> 定位：blog 系统建成后的**知识库业务域**——博客知识入库、向量化、AI 检索与组织展示。
> 契约口径以根目录 `CLAUDE.md` 为准；对象存储策略以 `docs/object-storage-design/object-storage-design.md` 为准。

| 文档 | 职责 | 状态 |
|------|------|------|
| [knowledge-base-design.md](./knowledge-base-design.md) | 主设计：背景、数据流、领域模型、库表草案、接口草案、选型对比、开放问题 | **探索稿 v0.2（主要裁决已定，剩余开放问题见 §9）** |
| [knowledge-er-diagram.html](./knowledge-er-diagram.html) | 表关系 ER 图（浏览器打开） | 与主设计 §6 同步 |
| [knowledge-prototype.html](./knowledge-prototype.html) | 页面原型：admin 知识文档 / 手动导入 / AI 服务商 + portal 问知识库（浏览器打开，tab 切换） | 布局参照，效力低于设计正文 |

**阅读顺序：** knowledge-base-design.md → knowledge-er-diagram.html → knowledge-prototype.html。

**效力顺序：** `CLAUDE.md`（契约） > 本目录正文 > HTML（ER/原型，仅展示与交互参照）。

---

## 范围一句话

**独立 knowledge-service(19101) + knowledge_db**：已发布文章自动入库 + **手动导入** → Markdown 切块（chunk）→ Embedding **云 API** 向量化（MySQL 存向量，应用层余弦检索）→ LLM 组织生成（RAG 问答，SSE 流式）→ blog-portal **登录用户**问答；blog 系统适配变动（事件推送、内部接口、portal SSO、gateway 路由）；blog-admin 嵌知识库运维菜单。

## 状态：探索稿 v0.2

已裁决（2026-10-27）：向量存储=MySQL（ES 重议后维持，见主设计 §5.1 升级路径）｜独立 knowledge-service（**blog 系统内服务**）+ blog 适配｜云 API，Embedding=SiliconFlow bge-m3 免费额度｜手动导入进 P0｜问答不允许匿名｜**权限沿用 blog RBAC**。
剩余待裁决：LLM 厂商与模型、SSE 透传 PoC、切块参数、portal SSO client——见主设计 §9。

> **流程约定：未获用户明确指示，不发起 openspec 提案**；本目录设计文档独立演进，提案时点由用户决定。

## 相关既有材料（不在本目录）

| 路径 | 关系 |
|------|------|
| `docs/blog-system/` | 文章域设计（知识来源）；table-design / api-design / er-diagram / prototype 口径参照 |
| `docs/object-storage-design/object-storage-design.md` | 对象存储唯一裁剪源（provider 多服务商模式先例） |
| `docs/template/rbac-design.md` | 权限标识与菜单短号 |
| `docs/template/tokens-aurora.css` | 管理端/用户端视觉 token（原型取值唯一来源） |
| `docs/port-registry.md` | 端口唯一登记处（新服务/新前端须先登记） |
| `docs/test-env.md` | 组件接入与测试账密（AI provider 接入信息将来登记于此） |