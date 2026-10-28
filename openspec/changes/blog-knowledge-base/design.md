# Design: blog-knowledge-base

> 契约效力顺序：`CLAUDE.md` > `docs/knowledge-base-design/table-design.md`（库表）> `docs/knowledge-base-design/api-design.md`（接口）> 主设计正文 > HTML（ER/原型，仅参照）。
> 本文件只承载实施层关键决策与风险；字段级 DDL / 入参出参 / 错误码明细**不在此复制**，一律引用设计文档。

## D1. 架构落点

```
blog-portal(5200)                blog-admin(5100)
 +问知识库页(需登录)              +知识库运维菜单(含文章入库)
        │                              │
      blog-gateway(9100)  /api/v1/knowledge/** 与其余 /api/** 同走 blog-service
        ▼                              ▼
┌─ blog-server 进程（19100，服务名 blog-service，不变）──────────────┐
│  blog-service 模块 ──只读契约 ArticleContentProvider──►            │
│  blog-knowledge-service 模块（入库/切块/检索/问答 + ES client 装配）│
│  AI 调用经 blog-framework 命名线程池 knowledgeAiExecutor 隔离      │
└──────────────┬──────────────────────────────┬────────────────────┘
          blog_db（含 knowledge_* 8 张表）   ES index knowledge_chunk_v1
                                        云 API（SiliconFlow bge-m3 + LLM）
```

## D2. 关键决策（均已裁决，见设计文档裁决记录）

| # | 决策 | 结论 | 理由 / 依据 |
|---|------|------|------------|
| 1 | 架构形态 | **单进程**：`blog-server` 依赖 `blog-service` + `blog-knowledge-service`，服务名/端口 19100 不变 | 对齐 CLAUDE.md §5.4；knowledge 非独立部署，未来拆进程只切 Feign |
| 2 | 库归属 | **合并进 `blog_db`**，`knowledge_*` 前缀隔离域边界；不建独立库、不引 dynamic-datasource | Q5；未来拆进程时迁表 |
| 3 | 向量检索 | **Elasticsearch 9.5.5**：dense_vector kNN + ik_smart BM25 + 原生 RRF；index 限 `knowledge_chunk_v1`；MySQL chunk 表为真相源（无 BLOB），ES 为检索镜像 | 知识库多技术文档，精确术语召回需 BM25 补足；推翻 MySQL BLOB 方案（2026-10-27） |
| 4 | ES client 落点 | **`blog-knowledge-service` 内装配**（config + RestClient bean），framework 不引入 | CLAUDE.md §2.1「例外（检索引擎 ES client）」；回升条件=第二域需要 ES |
| 5 | 入库触发 | **全部管理端手动**：文章发布不自动入库；「文章入库」页勾选提交；失效同步亦手动（按钮 + 对账） | 推翻自动方案；blog 既有逻辑零改动，取消事件 |
| 6 | blog 读取边界 | **只读契约** `ArticleContentProvider`（`blog-knowledge-api` 定义，`blog-service` 仅新增只读实现类）；薄投影表 `knowledge_article_index` 承载候选列表单库查询 | §7.3 + 表裁决 12/15；禁止改动既有 Service/Controller、禁止发布流程插 knowledge 调用 |
| 7 | AI 服务商 | **云 API**；Embedding=SiliconFlow bge-m3（dim=1024）免费额度；LLM=SiliconFlow 同账号便宜档（型号 P0-2 锁定回填 test-env）；复刻 `sys_storage_provider` 模式（DB 优先 + yml 引导 + 连通测试 + 密钥不回显）；**拆 kind=1/2 两行** | Q1 + 表裁决 5；同一时刻仅一个生效 Embedding provider，切换须重建索引 |
| 8 | 切块 | 按标题层级+段落聚合，500–800 字/chunk + 上下文增强前缀（标题/分类/标签） | Q3 首版建议值，落地后调 |
| 9 | 任务机制 | `knowledge_task` 表驱动（doc/操作级），入队即投递、指数退避 ≤max_retry=3、启动时 RUNNING→PENDING 自愈；**P0 不引定时扫描**，重跑走管理端 | 表裁决 9；不引 MQ、不做分布式定时（CLAUDE.md §2.2） |
| 10 | 双写一致性 | chunk 落 MySQL → 向量生成 → ES index 成功 → `embed_status=READY`；失败 `FAILED`+fail_reason，由对账 `article-index/sync` + 任务重跑兜底 | Q11 |
| 11 | AI 隔离 | `blog-framework` 装配命名 bean `knowledgeAiExecutor`（core=2/max=8/queue=100）+ 云 API 超时；knowledge 注入使用 | CLAUDE.md §6.9 禁止业务自建线程池；Q7 P0 进程内隔离 |
| 12 | 降级矩阵 | ES 检索不可用→503+审计告警（无降级通道）；LLM 不可用→ES match 关键词检索+模板化回答（带出处，`mode=KEYWORD_FALLBACK`）；线程池满→429（问答 `10003`/异步入库 `203103`）；Embedding 失败→如实报错 | Q6 + §6.6 降级闭环 |
| 13 | 门户接入 | blog-portal 接 auth-center OIDC（client_id `blog-portal-spa`，对齐 `user-admin-spa` 先例）；问答无匿名通道；网关 knowledge 路由不入 public-paths；SSE 透传（禁缓冲+读超时放宽）落地时一并验证 | Q2/Q4 |
| 14 | 错误码 | `BlogErrorCodeEnum` 新增 `203090–203099`（文档/入库）、`203100–203109`（检索/向量化/任务）、`203110–203114`（AI 服务商）；明细见 api-design §6 | blog 系统号 03，与既有 `203010–203087` 零撞号 |
| 15 | 权限种子 | 菜单 `1004`/`2009–2012`（sort：2009→1、2012→2、2011→3、2010→4）、按钮 `90023–90031`、`sys_role_menu 6034–6047` 绑 admin(9001)；`ingest` 与 `create` 分权、`revectorize` 与 `reindex` 分权 | 表裁决 2/4/14；续号避开既有占用与已发现的 blog 种子撞号区 |
| 16 | 候选状态 | 两维分离：`ingestState`（UNINGESTED/INGESTED/STALE/INVALID，投影表 LEFT JOIN doc 判定，指纹=live `sys_file.id`）+ `docStatus`（0–4 原样透出）；候选仅含已发布（status=1） | 表裁决 16/17 |

## D3. 模块与包结构（对齐 CLAUDE.md §5.4–5.6）

```
backend/blog/
├── blog-knowledge-api/          # 纯契约：ArticleContentProvider + DTO；无 Entity/Mapper/实现
├── blog-knowledge-service/      # controller/ service/impl/ mapper/ entity/ dto/ vo/
│   └── config/                  # ES client 装配（RestClient/ElasticsearchClient bean）
│   │                              enums/ constants/ converter/(MapStruct)
├── blog-service/                # 仅新增 provider 只读实现类（service/provider/ 或对齐既有包惯例）
├── blog-framework/              # 新增 knowledgeAiExecutor 装配（对齐既有线程池装配方式）
└── blog-server/                 # pom 增依赖 blog-knowledge-service；启动类/配置不动
```

- 实体归属：`knowledge_*` / `ai_provider` 实体落 `blog-knowledge-service/entity/`；禁止跨模块共享 Entity。
- 依赖方向：`blog-knowledge-service` → `blog-knowledge-api` + `blog-framework` + `blog-common` + `blog-object-storage`（读 MinIO 源文）+ `elasticsearch-java` 9.5.5（版本取 `docs/version-baseline.md`）。
- `blog-service` 不依赖 knowledge 模块（契约接口在 api 模块，实现类在本模块，DI 由 Spring 注入）。

## D4. 配置

- `blog-server` 三份 `application-*.yml` 增配置段：`blog.knowledge.*`（ES 地址/线程池参数/AI provider yml 引导 `blog.ai.providers[]`，kebab-case + `@ConfigurationProperties`，禁散落 `@Value`）。
- ES 接入信息（192.168.99.100:9200，免密，xpack 关闭）真相源 `docs/test-env.md` §2.5；`ai_provider` 的 base_url/api_key 由 yml 引导 + 管理端录入，**禁止明文密钥入仓**（CLAUDE.md §6.7）。

## D5. 风险与缓解

| 风险 | 缓解 |
|------|------|
| ES 9.5.5 + ik 插件版本漂移 | ik 版本随 ES 严格对齐（基线已锁 9.5.5）；index 创建后校验 `embedding.dims=1024`、`content.analyzer=ik_smart` |
| 双写不一致（MySQL vs ES） | MySQL 为真相源；ES index 失败 `embed_status=FAILED` + 对账/重跑兜底（Q11）；chunk content 仅重建时变 |
| 全量重建停机窗口 | P0 接受（页面提示「重建中，检索暂不可用」）；P2 改 alias+reindex 滚动 |
| 云 API 免费额度耗尽 | 批量节流 + 失败可重试；`knowledge_task` 退避重试；配额限流（20/天+5/分钟）控成本 |
| SSE 经网关被缓冲/超时断流 | 路由预配置（响应禁用缓冲、读超时放宽至流式上限）+ `X-Accel-Buffering: no`；P0-7 真实链路验证（Q2） |
| 手动导入 `source_id` 撞唯一键 | `source=2/3` 时 `source_id=NULL`（唯一索引不对 NULL 去重）；文章来源复用 doc 行 UPDATE |
| 手动导入 doc_url 暴露 admin 路由 | 手动导入 `doc_url` 存空，portal 引用降级为纯文本标题+「站内知识」标记（表裁决 7） |
| 投影表空态误导 | 候选页空态提示「请先同步文章索引」，不自动全量拉取 |
| blog 种子撞号（既有遗留） | 本域续号 `1004/2009–2012/90023–90031/6034–6047` 已避开；既有 `2026-10-20-blog-storage-provider.sql` 撞号属 blog 侧遗留，另行修复（非本域） |

## D6. 不在本次范围

- P1/P2 全部项（见 proposal Non-goals）；auth/user 系统改动；blog 既有业务逻辑改动；`CLAUDE.md` 技术栈再变更（ES 行已登记完成）。
