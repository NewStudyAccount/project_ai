# Proposal: blog-knowledge-base

## Why

blog 系统内容域（分类/标签/文章/正文 MinIO/两端前端）已交付。下一阶段引入**知识库业务域**：将博客沉淀的知识入库、切块、向量化，形成可语义检索的向量索引，并以 RAG 问答（SSE 流式）向登录用户呈现——「问知识库，答案有出处」。

设计已完成并全部裁决落定（开放问题清零）：`docs/knowledge-base-design/`（主设计 v0.5 + table-design 8 张表 17 项裁决 + api-design 字段级契约）；Elasticsearch 9.5.5 已按 CLAUDE.md §2 流程登记技术栈与版本基线（`docs/version-baseline.md`）与接入信息（`docs/test-env.md` §2.5）。本变更将设计转为可实施任务。

## What Changes

- **新建两个业务模块**（`backend/blog/` 下，对齐「启动模块 + 业务模块分离」范式）：
  - `blog-knowledge-api`：同进程只读契约 `ArticleContentProvider`（投影分页拉取 + 正文/元数据回拉 DTO）；无 Entity/Mapper/实现
  - `blog-knowledge-service`：知识库纯业务模块（入库/切块/向量化/检索/问答 + **ES client 装配**，按 CLAUDE.md §2.1「例外（检索引擎 ES client）」落点）
  - `blog-server` pom 增依赖 `blog-knowledge-service`（单进程承载，服务名/端口 19100 不变）；`blog-parent` pom 增聚合与版本声明
- **数据库**（落 `blog_db`，不建独立库、不引 dynamic-datasource）：
  - `deploy/db/migration/blog_db/07_knowledge_tables.sql`：新增 8 张表（`knowledge_doc` / `knowledge_chunk`（业务字段真相源，无向量 BLOB）/ `knowledge_file` / `ai_provider` / `knowledge_chat_session` / `knowledge_chat_message` / `knowledge_task` / 薄投影表 `knowledge_article_index`）
  - `deploy/db/seed/2026-10-28-blog-knowledge.sql`：RBAC 菜单/按钮种子（`1004`、`2009–2012`、`90023–90031`、`sys_role_menu 6034–6047`）+ `ai_provider` 引导默认两行（`siliconflow-embed` bge-m3 dim=1024 / `siliconflow-llm`）
- **Elasticsearch**：index `knowledge_chunk_v1`（dense_vector kNN + ik_smart BM25 + RRF 原生混合检索；`deploy/es/knowledge_chunk_v1.json` 初始化脚本）；MySQL chunk 表为真相源，ES 为检索镜像
- **文章入库链路**（管理端手动触发，blog 既有逻辑零改动）：`blog-service` 仅新增一个只读实现类（`ArticleContentProvider`），不发事件、不改发布/下架/删除流程；投影表同步（`article-index/sync`）→ 候选页（`candidates`/`candidates/summary`）→ 勾选提交 `from-articles` → `knowledge_task(INGEST)` 一步到位（入库+切块+向量化+ES index）
- **手动导入**：管理端 `.md` 上传/重传/删除 → MinIO 源文 + `knowledge_file` 登记 → 一文件一 doc
- **ES 检索 + RAG 问答**：kNN+BM25+RRF Top-K（出处 denormalize 于 ES，全链路不回 MySQL）→ LLM 流式生成 → `POST /api/v1/knowledge/ask`（SSE，登录用户，限流 20 次/天 + 5 次/分钟）；降级矩阵闭环（ES down→503、LLM down→关键词检索+模板化回答、池满→429、Embedding 失败→如实报错）
- **blog 适配**：blog-portal 接 auth-center SSO（client_id `blog-portal-spa`，对齐 `user-admin-spa` 先例）；blog-gateway 增路由 `/api/v1/knowledge/**` → `lb://blog-service`（不入 public-paths；SSE 透传：禁缓冲 + 读超时放宽，落地时一并验证）
- **AI 线程池**：`blog-framework` 装配命名 bean `knowledgeAiExecutor`（core=2/max=8/queue=100）+ 云 API 超时配置；knowledge 模块注入使用，禁止自建线程池（CLAUDE.md §6.9）
- **前端**：blog-admin 增知识库运维菜单（文章入库/知识文档/手动导入/AI 服务商 + 重建/统计）；blog-portal 增问答页（登录态 + 流式 UI + 引用展示）

Non-goals（项目边界，对齐设计 §1.2 与 CLAUDE.md §1）：

- **P1/P2 不做**：PDF/Word 文件导入扩展、问答会话记录完善、ES 分词调优（ik_max_word/同义词）、双向定期对账、token 预算裁剪、入库回拉竞态防护、知识地图/综述/相关推荐/自动打标、alias+reindex 滚动重建、`related` 接口
- 不拆 knowledge 独立进程/独立启动模块（单进程裁决；AI 隔离 P0 用进程内独立线程池+超时）
- 不引入 pgvector/Milvus/Qdrant；ES 不扩 blog 业务检索（index 边界限 `knowledge_chunk_v1`）
- 不做 Embedding/LLM 自托管；生产 `ai_provider.api_key` 加密另开变更
- 不引 MQ、不做分布式定时（`knowledge_task` P0 重跑走管理端）
- 不改 blog 既有业务逻辑（只读契约，仅新增一个只读实现类）；不动 auth/user 系统
- 问答多轮上下文不做（`sessionId` 仅落库与限流审计）

## Capabilities

### New Capabilities

- `knowledge-ingest`: 知识入库能力——候选文章（薄投影表驱动，ingestState/docStatus 两维分离）、同步文章索引、从文章入库（异步任务一步到位）、手动导入（.md 上传）、单文档重建/全量重建/失效同步/任务重跑
- `knowledge-ask`: RAG 流式问答能力——SSE 帧契约（meta/delta/refs/done/error）、混合检索（kNN+BM25+RRF）、LLM 降级矩阵、登录限流
- `knowledge-admin`: 知识库运维能力——知识文档管理、AI 服务商 CRUD 与连通测试（密钥不回显、dim 校验）、任务观测与重跑、统计与 ES 健康度

### Modified Capabilities

（无既有 spec 修改；blog 既有能力零改动）

## Impact

- **代码**：新建 `blog-knowledge-api` + `blog-knowledge-service` 两模块；`blog-server`/`blog-parent` pom 各增依赖声明；`blog-service` 仅新增 1 个只读实现类；`blog-framework` 新增 `knowledgeAiExecutor` 装配；`blog-gateway` 增 1 条路由；`BlogErrorCodeEnum` 增 `203090–203114` 段；blog-admin/blog-portal 各增页面
- **数据**：`blog_db` 新增 8 张表 + RBAC/`ai_provider` 种子；ES 新增 index `knowledge_chunk_v1`；既有表零改动
- **API**：新增 `/api/v1/admin/knowledge/**`（管理端，挂 RBAC 权限位 + 审计）与 `/api/v1/knowledge/ask`（登录用户 SSE）；无既有接口变更
- **部署**：测试环境 ES 9.5.5 + ik 9.5.5 + Kibana 9.5.5 已就绪（`docs/test-env.md` §2.5）；SQL/ES index 人工执行（不引迁移框架，CLAUDE.md §2.2）
- **协调**：字段级契约唯一源为 `docs/knowledge-base-design/table-design.md`（库表）与 `api-design.md`（接口）；本变更制品不复制字段明细，只引用
