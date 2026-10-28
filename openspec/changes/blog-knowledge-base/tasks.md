# Tasks: blog-knowledge-base

> 依据 `CLAUDE.md` §8：只按本清单实施，完成一项勾一项；不擅自修改 proposal/specs/design。
> 字段级契约唯一源：`docs/knowledge-base-design/table-design.md`（DDL/种子）与 `api-design.md`（接口/错误码/SSE 帧）；实施时先读对应小节再落码。
> 验证基线：`cd backend/blog && mvn -q compile`、`cd frontend/blog-admin|blog-portal && npm run lint && npm run type-check`（CLAUDE.md §3.2）。

## 1. 前置核验（对应 P0-0）

- [ ] 1.1 确认 ES 9.5.5 + ik 9.5.5 + Kibana 已就绪且可连通：`curl http://192.168.99.100:9200` 返回 9.5.5；`GET /_cat/plugins` 含 analysis-ik（接入信息 `docs/test-env.md` §2.5；缺则先补部署，须用户确认）
- [ ] 1.2 核对 `docs/version-baseline.md`（ES/elasticsearch-java 9.5.5）与 `CLAUDE.md` §2 检索引擎行已登记（已完成，复核即可）；确认无在途变更冲突（blog 既有变更均已归档）

## 2. 表结构与种子 SQL（对应 P0-1 之 SQL）

- [ ] 2.1 编写 `deploy/db/migration/blog_db/07_knowledge_tables.sql`：8 张表 DDL 逐字段照抄 `table-design.md` §2（`knowledge_doc`/`knowledge_chunk`/`knowledge_file`/`ai_provider`/`knowledge_chat_session`/`knowledge_chat_message`/`knowledge_task`/`knowledge_article_index`；`IF NOT EXISTS` 可重复执行；审计 5 列齐套；索引命名与数量对齐契约）
- [ ] 2.2 编写 `deploy/db/seed/2026-10-28-blog-knowledge.sql`：RBAC 菜单/按钮（`1004`/`2009–2012`/`90023–90031` + `sys_role_menu 6034–6047`，`INSERT IGNORE` + 按 id `UPDATE` 幂等）+ `ai_provider` 引导两行（`siliconflow-embed` bge-m3 dim=1024 / `siliconflow-llm`，见 `table-design.md` §4.2）
- [ ] 2.3 编写 `deploy/es/knowledge_chunk_v1.json`：ES index 定义照抄 `table-design.md` §3（settings + mappings；`PUT /knowledge_chunk_v1` 前先 `HEAD` 判存在）；创建后校验 `embedding.dims=1024`、`content.analyzer=ik_smart`
- [ ] 2.4 人工执行 SQL 与 ES index（**不可逆/生产执行前按 CLAUDE.md §7 征询**）；执行后核对 8 表存在、种子可重复执行不报错

## 3. 模块骨架与依赖（对应 P0-1）

- [ ] 3.1 新建 `backend/blog/blog-knowledge-api/pom.xml`（parent=blog-parent；依赖 blog-common；无 Entity/Mapper/实现）
- [ ] 3.2 新建 `backend/blog/blog-knowledge-service/pom.xml`：依赖 blog-knowledge-api + blog-framework + blog-common + blog-object-storage + `co.elastic.clients:elasticsearch-java`（版本取 `docs/version-baseline.md`）+ mapstruct/lombok
- [ ] 3.3 `blog-server/pom.xml` 增依赖 `blog-knowledge-service`；`blog-parent/pom.xml` `<modules>` 与 `<dependencyManagement>` 增两模块声明
- [ ] 3.4 `blog-knowledge-service` 内新建 ES client 装配（`config/`：RestClient + ElasticsearchClient bean，`@ConfigurationProperties` 绑定 `blog.knowledge.elasticsearch.*`；对齐 CLAUDE.md §2.1 例外条款，framework 不引入 ES client）
- [ ] 3.5 `blog-framework` 新增命名线程池 bean `knowledgeAiExecutor`（core=2/max=8/queue=100）+ 云 API 超时配置项（对齐既有线程池装配方式；业务模块禁止自建线程池）
- [ ] 3.6 全量编译通过：`cd backend/blog && mvn clean install -DskipTests`

## 4. AI Provider 抽象（对应 P0-2）

- [ ] 4.1 Entity/Mapper：`ai_provider`（实体落 blog-knowledge-service，含审计字段与 `@TableLogic`）
- [ ] 4.2 `EmbeddingProvider` / `LLMProvider` 接口 + `SiliconFlowProvider` 实现（OpenAI 兼容端点；embed 批量接口 + chat 流式接口；超时经 framework 配置）；DB 优先 + yml `blog.ai.providers[]` 引导默认
- [ ] 4.3 锁定 LLM 具体型号（SiliconFlow 便宜档，按当时在售清单 + 免费额度余量）并回填 `docs/test-env.md`（**须与用户确认型号**）
- [ ] 4.4 管理端 AI 服务商 CRUD + 连通测试接口（`/admin/knowledge/ai-providers*`，字段与错误码对齐 api-design §3：`apiKeyMasked` 永不回显明文、`dim` 与 ES dims 不一致返回 `203114`、默认行禁删 `203113`）

## 5. 只读契约与 blog 适配（对应 P0-3）

- [ ] 5.1 `blog-knowledge-api` 定义 `ArticleContentProvider`（两个方法：①分页拉取已发布文章投影 ②按 id 回拉正文+标题/分类/标签元数据；DTO 对齐 table-design §2.7 投影字段与 api-design §1.1）
- [ ] 5.2 `blog-service` 新增**一个只读实现类**（读 `blog_article`/`blog_article_detail`/`blog_article_tag` + 复用 `ArticleContentStorageService.readContent`）；**不改任何既有类、不发事件**
- [ ] 5.3 grep 复核：`git diff` 中 blog-service 仅新增 1 个实现类文件，无既有业务文件改动

## 6. 文章入库链路（对应 P0-4）

- [ ] 6.1 Entity/Mapper：`knowledge_doc` / `knowledge_chunk` / `knowledge_article_index` / `knowledge_task`（含枚举：docStatus、taskType、taskStatus；`BaseEnum` 对齐 CLAUDE.md §6.13）
- [ ] 6.2 投影表同步服务：`POST /admin/knowledge/article-index/sync`（分页拉取 status=1 已发布 → upsert + 全量覆盖 → 失效同步分支落 `knowledge_task(INVALIDATE)`；出参 pulled/inserted/updated/removed/invalidated 对齐 api-design §1.3）
- [ ] 6.3 候选页接口：`GET /candidates`（投影表 LEFT JOIN doc 单库分页/筛选/统计，SQL 对齐 table-design §2.7；orderBy 白名单）+ `GET /candidates/summary`（四态聚合）
- [ ] 6.4 入库接口：`POST /docs/from-articles`（≤100 条/次；逐篇校验 → doc upsert(source=1, source_id=articleId, 复用行语义) → `knowledge_task(INGEST)` 立即投递 `knowledgeAiExecutor`；出参 accepted/skipped/rejected 明细对齐 api-design §1.4；`@Idempotent`）
- [ ] 6.5 切块器：按标题层级+段落聚合，500–800 字/chunk + 上下文增强前缀（标题/分类/标签）；`token_count` 估算
- [ ] 6.6 异步向量化任务执行器：逐 chunk 批量调 `EmbeddingProvider.embed` → index 进 ES（`_id`=chunk.id 幂等覆盖）→ 成功回写 `embed_status=READY`，失败 `FAILED`+fail_reason；doc 状态机（PENDING→VECTORIZING→READY/FAILED）；任务重试退避 ≤max_retry=3、超限 DEAD；启动时遗留 RUNNING→PENDING 自愈
- [ ] 6.7 `BlogErrorCodeEnum` 新增 `203090–203099` 段（对齐 api-design §6）

## 7. 手动导入（对应 P0-5）

- [ ] 7.1 Entity/Mapper：`knowledge_file`
- [ ] 7.2 `POST /admin/knowledge/docs`（multipart：files ≤20 个 `.md`、单文件 ≤10MB、tags?）：类型/大小校验 → 经 `blog-object-storage` put MinIO `knowledge/{docId}/{uuid}.md` → 登记 knowledge_file → doc(source=2, **source_id=NULL**, PENDING) → `knowledge_task(INGEST)`；一文件一 doc；`@Idempotent`（api-design §2.2）
- [ ] 7.3 编辑/删除：`PUT /docs/{id}`（仅 source=2；重传触发 REVECTORIZE，先删旧 ES chunk 文档）、`DELETE /docs/{id}`（逻辑删 + INVALIDATE 清 ES；错误 `203095`）
- [ ] 7.4 单文档重建 `POST /docs/{id}/revectorize`（权限 `blog:knowledge:revectorize`）与全量重建 `POST /reindex`（FULL_REINDEX 任务：删 ES index 重建 + MySQL 重置；提示停机窗口）

## 8. ES 检索 + RAG 问答（对应 P0-6）

- [ ] 8.1 检索服务：kNN(embedding) + match(content, ik_smart BM25) + filter(doc_status=2) → ES 原生 RRF → Top-K(K=5)；出处取 ES denormalize 的 doc_title/doc_url（全链路不回 MySQL）；ES 不可用 → `203100`(HTTP 503) + 审计告警
- [ ] 8.2 RAG prompt 组装（chunk.content + 出处）+ LLM 流式调用（`knowledgeAiExecutor` 承载；15s 心跳注释帧）
- [ ] 8.3 `POST /knowledge/ask`（SSE）：帧格式严格对齐 api-design §4.2（meta/delta/refs/done/error；首帧 meta 含 sessionId/mode/model）；流开始前错误返回 JSON Result（401/403/429/503/30001）；`KEYWORD_FALLBACK` 降级分支（LLM 不可用 → match 片段+模板化说明）
- [ ] 8.4 会话落库：`knowledge_chat_session` / `knowledge_chat_message`（done 前写 Q 行+A 行含 refs JSON/cost_ms；error/中断只落 Q 行；归属用户=审计 create_by）
- [ ] 8.5 限流：`@RateLimit` 5 次/分钟/用户 + 20 次/天/用户（超限 429 + `10003`，msg 区分）；问题 1–500 字校验（`30001`）
- [ ] 8.6 `BlogErrorCodeEnum` 新增 `203100–203109` 段

## 9. 任务观测与统计（api-design §2.7/§2.8）

- [ ] 9.1 `GET /admin/knowledge/tasks`（taskType/status/docId 筛选分页）+ `POST /tasks/{id}/rerun`（仅 FAILED/DEAD；重置重投；错误 `203104`/`203105`）
- [ ] 9.2 `GET /admin/knowledge/stats`（doc/chunk 分布、READY 比例、esDocCount/esHealthy 取 ES `_count`+集群健康，ES 不可用置 null/false 仍 200）
- [ ] 9.3 `GET /admin/knowledge/docs` 文档分页（含 readyChunkCount 向量健康度；错误码 203090–203095 对齐）

## 10. blog 适配③④（对应 P0-7）

- [ ] 10.1 auth-center 注册 client `blog-portal-spa`（对齐 `user-admin-spa` 先例：注册/回调/种子）；接入信息登记 `docs/test-env.md`
- [ ] 10.2 blog-gateway 增路由 `/api/v1/knowledge/**` → `lb://blog-service`；**不入 public-paths**；SSE 预配置（响应禁用缓冲、读超时放宽至流式上限）
- [ ] 10.3 blog-portal 接 SSO 登录（登录态守卫）；验证问答链路 SSE 经网关不断流（Q2 真实链路验证）

## 11. 前端页面（对应 P0-8/P0-9）

- [ ] 11.1 blog-admin：动态路由/菜单对接种子（2009 知识文档 / 2012 文章入库 / 2011 手动导入 / 2010 AI 服务商）；`src/api` 分包 + axios 统一封装（Result 解包、Long→string）
- [ ] 11.2 文章入库页：候选列表（ingestState 标签 + docStatus 进度）+ 同步文章索引按钮 + 勾选批量入库（跳过/拒绝明细反馈）
- [ ] 11.3 知识文档页（列表/状态/健康度/重建/失效）+ 手动导入页（.md 多选上传/重传/删除）+ AI 服务商页（CRUD/连通测试/密钥掩码显示）
- [ ] 11.4 任务列表页 + 统计页（ES 健康度）
- [ ] 11.5 blog-portal 问答页：登录态 + SSE 流式 UI（delta 追加/refs 引用展示/KEYWORD_FALLBACK 提示/中断与错误处理；手动导入引用降级「站内知识」标记）
- [ ] 11.6 前端检查：`cd frontend/blog-admin && npm run lint && npm run type-check`；`cd frontend/blog-portal && npm run lint && npm run type-check`

## 12. 收尾验收（最小闭环冒烟）

- [ ] 12.1 后端全量编译：`cd backend/blog && mvn clean install -DskipTests`
- [ ] 12.2 冒烟闭环：同步文章索引 → 勾选已发布文章入库 → doc/chunk READY → ES 文档数与 MySQL READY 数一致 → `/knowledge/ask` 返回带出处答案（portal 登录态）
- [ ] 12.3 降级验证：停 LLM（或密钥失效）→ `mode=KEYWORD_FALLBACK`；停 ES → 问答 503 `203100`
- [ ] 12.4 权限验证：无 `blog:knowledge:ingest` 权限访问候选接口 → 403；匿名调 `/knowledge/ask` → 401；限流触发 → 429
- [ ] 12.5 grep 复核无越界改动：blog 既有业务零改动、ES 仅 `knowledge_chunk_v1` 一个 index、framework 无 ES client、无明文密钥入仓
- [ ] 12.6 回填 tasks.md 勾选；记入 `.workbuddy/memory/`；交付总结（端点清单/启动命令/遗留事项）
