# knowledge-base-design · 设计文档索引

> 本目录是 **知识库 + 向量化 + AI 组织展示**的设计文档集合（独立文档，非 openspec 提案）。
> 定位：blog 系统建成后的**知识库业务域**——博客知识入库、向量化、AI 检索与组织展示。
> 契约口径以根目录 `CLAUDE.md` 为准；对象存储策略以 `docs/object-storage-design/object-storage-design.md` 为准。

| 文档 | 职责 | 状态 |
|------|------|------|
| [knowledge-base-design.md](./knowledge-base-design.md) | 主设计：背景、数据流、领域模型、库表草案、接口草案、选型对比、开放问题 | **探索稿 v0.5（开放问题清零）** |
| [table-design.md](./table-design.md) | **库表契约**：8 张新增表 DDL（含 `knowledge_task`、薄投影表 `knowledge_article_index`）、ES index 定义、RBAC/AI 服务商种子、17 项裁决记录 | 设计稿（表级裁决已落定） |
| [api-design.md](./api-design.md) | **接口契约**：逐字段入参/出参、SSE 帧格式、错误码段位（`203090–203114`） | 设计稿 |
| [knowledge-er-diagram.html](./knowledge-er-diagram.html) | 表关系 ER 图（浏览器打开） | 与主设计 §6 / table-design 同步 |
| [knowledge-prototype.html](./knowledge-prototype.html) | 页面原型：admin 文章入库 / 知识文档 / 手动导入 / AI 服务商 + portal 问知识库（浏览器打开，tab 切换） | 布局参照，效力低于设计正文 |
| [knowledge-dataflow.html](./knowledge-dataflow.html) | 全链路数据流图：入库 / 向量化入 ES / 检索生成 / 同步治理 | 与主设计 §3 同步 |
| [knowledge-vector.html](./knowledge-vector.html) | 向量化原理科普：Embedding / 余弦 / 近邻 / 混合召回 | 说明材料，非契约 |

**阅读顺序：** knowledge-base-design.md → table-design.md → api-design.md → knowledge-er-diagram.html → knowledge-prototype.html。

**效力顺序：** `CLAUDE.md`（契约） > table-design.md（库表）/ api-design.md（接口） > 主设计正文 > HTML（ER/原型，仅展示与交互参照）。

---

## 范围一句话

**单进程 `blog-server` 承载 `blog-service` + `blog-knowledge-service` + `blog_db` 内 `knowledge_*` 表 + Elasticsearch**：blog-admin **「文章入库」页勾选已发布文章（手动触发，发布不自动入库）** + **手动导入** → Markdown 切块（chunk）→ Embedding **云 API** 向量化 → 向量+content 入 ES index `knowledge_chunk_v1`（kNN + BM25 RRF 混合检索）→ LLM 组织生成（RAG 问答，SSE 流式）→ blog-portal **登录用户**问答；同进程经**只读契约** `ArticleContentProvider` 解耦（**无事件、blog 既有逻辑零改动**；portal SSO、gateway 路由适配）；AI 调用进程内独立线程池隔离；blog-admin 嵌知识库运维菜单。

## 状态：探索稿 v0.5（开放问题清零）

已裁决（2026-10-27）：**向量检索 = Elasticsearch**（index `knowledge_chunk_v1`，dense_vector kNN + text BM25 + RRF 原生混合检索；推翻早前 MySQL BLOB 方案；MySQL chunk 表去 embedding BLOB 存业务字段真相源，向量+content 镜像存 ES）｜**单进程** `blog-server` 承载 `blog-service` + `blog-knowledge-service`（19100，knowledge 非独立部署）｜云 API，Embedding=SiliconFlow bge-m3 免费额度｜手动导入进 P0｜问答不允许匿名｜**权限沿用 blog RBAC**（同进程直用）｜**AI 调用进程内独立线程池隔离**。
补充裁决（2026-10-28，Q1-Q11 全部落定，开放问题清零）：**Q8-Q11**——ES 经 CLAUDE.md §2 技术栈登记、ES client 落 blog-knowledge-service、P0 装 ik 分词器、双写补偿 FAILED+对账重 index；**Q1-Q7**——LLM=SiliconFlow 同账号便宜档起步（型号 P0-2 锁定）、SSE 透传 P0-7 落地时一并验证、切块首版按建议值（500–800 字/chunk）、portal SSO 对齐 user-admin-spa 先例、**knowledge_db 合并进 blog_db**（不引入 dynamic-datasource）、AI 降级=关键词检索+模板化回答/池满 429、线程池隔离=P0 独立池+超时（监控后议拆进程）。详见主设计 §9。

追加裁决（2026-10-28，入库流程推翻）：**文章发布不再自动入库**，改由 blog-admin **「文章入库」独立菜单**勾选已发布文章手动触发向量化（一步到位：入库+切块+向量化+ES index）；下架/删除的 ES 失效同步**亦改手动**（管理端按钮 + 对账）；**blog 既有逻辑零改动**（`blog-service` 仅新增只读实现类，取消 `ArticleKnowledgeEvent` 事件）；新增权限 `blog:knowledge:ingest` + 菜单 `2012`。

追加裁决（2026-10-28，候选列表契约）：新增**薄投影表 `knowledge_article_index`**（已发布文章只读投影），候选列表 / `ingestState` 筛选 / 统计在 knowledge 单库精确完成；候选列表**仅含已发布文章**；状态**两维分离**（`ingestState` + `docStatus`）。表级 17 项裁决、**8 张表** DDL 见 [table-design.md](./table-design.md)。

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