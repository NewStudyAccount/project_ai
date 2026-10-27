# knowledge-base-design · 设计文档索引

> 本目录是 **知识库 + 向量化 + AI 组织展示**的设计文档集合（独立文档，非 openspec 提案）。
> 定位：blog 系统建成后的**知识库业务域**——博客知识入库、向量化、AI 检索与组织展示。
> 契约口径以根目录 `CLAUDE.md` 为准；对象存储策略以 `docs/object-storage-design/object-storage-design.md` 为准。

| 文档 | 职责 | 状态 |
|------|------|------|
| [knowledge-base-design.md](./knowledge-base-design.md) | 主设计：背景、数据流、领域模型、库表草案、接口草案、选型对比、开放问题 | **探索稿 v0.5（开放问题清零）** |
| [knowledge-er-diagram.html](./knowledge-er-diagram.html) | 表关系 ER 图（浏览器打开） | 与主设计 §6 同步 |
| [knowledge-prototype.html](./knowledge-prototype.html) | 页面原型：admin 知识文档 / 手动导入 / AI 服务商 + portal 问知识库（浏览器打开，tab 切换） | 布局参照，效力低于设计正文 |

**阅读顺序：** knowledge-base-design.md → knowledge-er-diagram.html → knowledge-prototype.html。

**效力顺序：** `CLAUDE.md`（契约） > 本目录正文 > HTML（ER/原型，仅展示与交互参照）。

---

## 范围一句话

**单进程 `blog-server` 承载 `blog-service` + `blog-knowledge-service` + `blog_db` 内 `knowledge_*` 表 + Elasticsearch**：已发布文章自动入库 + **手动导入** → Markdown 切块（chunk）→ Embedding **云 API** 向量化 → 向量+content 入 ES index `knowledge_chunk_v1`（kNN + BM25 RRF 混合检索）→ LLM 组织生成（RAG 问答，SSE 流式）→ blog-portal **登录用户**问答；同进程经 Spring 事件 + 接口注入解耦（portal SSO、gateway 路由适配）；AI 调用进程内独立线程池隔离；blog-admin 嵌知识库运维菜单。

## 状态：探索稿 v0.5（开放问题清零）

已裁决（2026-10-27）：**向量检索 = Elasticsearch**（index `knowledge_chunk_v1`，dense_vector kNN + text BM25 + RRF 原生混合检索；推翻早前 MySQL BLOB 方案；MySQL chunk 表去 embedding BLOB 存业务字段真相源，向量+content 镜像存 ES）｜**单进程** `blog-server` 承载 `blog-service` + `blog-knowledge-service`（19100，knowledge 非独立部署）｜云 API，Embedding=SiliconFlow bge-m3 免费额度｜手动导入进 P0｜问答不允许匿名｜**权限沿用 blog RBAC**（同进程直用）｜**AI 调用进程内独立线程池隔离**。
补充裁决（2026-10-28，Q1-Q11 全部落定，开放问题清零）：**Q8-Q11**——ES 经 CLAUDE.md §2 技术栈登记、ES client 落 blog-knowledge-service、P0 装 ik 分词器、双写补偿 FAILED+对账重 index；**Q1-Q7**——LLM=SiliconFlow 同账号便宜档起步（型号 P0-2 锁定）、SSE 透传 P0-7 落地时一并验证、切块首版按建议值（500–800 字/chunk）、portal SSO 对齐 user-admin-spa 先例、**knowledge_db 合并进 blog_db**（不引入 dynamic-datasource）、AI 降级=关键词检索+模板化回答/池满 429、线程池隔离=P0 独立池+超时（监控后议拆进程）。详见主设计 §9。

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