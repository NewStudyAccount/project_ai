# knowledge-ingest 能力规格（知识入库）

> 字段级契约：`docs/knowledge-base-design/table-design.md`（库表）+ `api-design.md` §1/§2（接口）。

## ADDED Requirements

### Requirement: 候选文章由薄投影表驱动

「文章入库」候选列表 SHALL 以只读投影表 `knowledge_article_index`（仅含已发布 status=1 文章）LEFT JOIN `knowledge_doc` 在 knowledge 单库内完成分页、筛选与统计，MUST NOT 跨域读 blog 表。入库状态 SHALL 两维分离：`ingestState`（UNINGESTED/INGESTED/STALE/INVALID，判定指纹 = 文章当前 live 正文 `sys_file.id`）+ `docStatus`（0–4 原样透出）。`orderBy` MUST 白名单（`publish_time` 默认 | `article_update_time`）。投影表为空时 MUST 返回空列表（前端空态引导「同步文章索引」），MUST NOT 自动全量拉取。

#### Scenario: 未入库文章展示

- **WHEN** 投影表存在已发布文章且无对应 `knowledge_doc(source=1)`
- **THEN** 该行 `ingestState=UNINGESTED`、`docId=null`、`docStatus=null`、`chunkCount=0`

#### Scenario: 正文变更判定待更新

- **WHEN** 文章已入库且当前 live `sys_file.id` 与 `doc.content_version` 不一致
- **THEN** 该行 `ingestState=STALE`

#### Scenario: 重发布但正文未变不误报

- **WHEN** 文章重新发布但 live 正文文件未变（指纹相等）
- **THEN** 该行 `ingestState=INGESTED`

#### Scenario: 投影表空态

- **WHEN** 首次使用、投影表无数据时请求候选列表
- **THEN** 返回空分页结果，不自动发起全量拉取

### Requirement: 同步文章索引（管理端手动）

`POST /api/v1/admin/knowledge/article-index/sync`（权限 `blog:knowledge:ingest`）SHALL 经只读契约分页拉取已发布文章（status=1）→ 按 `article_id` upsert 并删除本次集合外的行（全量覆盖语义）→ 执行失效同步：`doc.source_id` 不在投影表 → doc 置 INVALID 且落 `knowledge_task(INVALIDATE)` 异步更新 ES `doc_status=4`。投影 upsert 为同步动作，MUST NOT 落任务表。

#### Scenario: 全量覆盖同步

- **WHEN** 管理员点击同步文章索引
- **THEN** 投影表与当前已发布文章集合一致（新增/更新/移除），返回 `{ pulled, inserted, updated, removed, invalidated }`

#### Scenario: 下架文章触发失效

- **WHEN** 同步后发现某 doc 的 `source_id` 已不在投影表（文章已下架/删除）
- **THEN** 该 doc 置 INVALID，异步任务将 ES 文档 `doc_status` 置 4（不物理删），检索 filter 排除

### Requirement: 从文章入库（一步到位异步）

`POST /api/v1/admin/knowledge/docs/from-articles`（权限 `blog:knowledge:ingest`，`@Idempotent`）SHALL 接收 `articleIds[]`（≤100 条/次）：逐篇校验已发布且有 live 正文 → `knowledge_doc` upsert(`source=1`)（同一文章复用既有 doc 行 UPDATE，不重复 INSERT）→ 落 `knowledge_task(INGEST)` 并立即投递异步执行 入库→切块→向量化→ES index。受理即返回批量明细（accepted/skippedItems/rejectedItems）；已入库且指纹未变 → skipped(`ALREADY_INGESTED`)；同文章已有 PENDING/RUNNING 任务 → skipped(`IN_PROGRESS`)。入库流程 MUST NOT 触发 blog 任何既有逻辑变更或事件。

#### Scenario: 勾选入库成功受理

- **WHEN** 管理员勾选 3 篇已发布文章提交入库
- **THEN** 同步返回 `submitted=3` 及各 `docId/taskId`，后台异步完成切块、向量化与 ES index，最终 doc `status=READY`

#### Scenario: 重复提交内容未变

- **WHEN** 对已入库且 `content_version` 未变的文章再次提交入库
- **THEN** 该篇进入 `skippedItems`（reason=`ALREADY_INGESTED`），不重复建任务

#### Scenario: 未发布或无正文拒绝

- **WHEN** 提交的 articleIds 中含未发布或 live 正文为空的文章
- **THEN** 该篇进入 `rejectedItems`（code=`203092`），其余合法文章正常受理

#### Scenario: 超批量上限

- **WHEN** `articleIds` 超过 100 条或为空
- **THEN** 返回校验错误 `30001`，不受理任何文章

### Requirement: 手动导入（一文件一 doc）

`POST /api/v1/admin/knowledge/docs`（权限 `blog:knowledge:create`，multipart，`@Idempotent`）SHALL 仅接受 `.md`（`text/markdown`，单文件 ≤10MB，多选 ≤20 个）：逐文件 put MinIO `knowledge/{docId}/{uuid}.md` → 登记 `knowledge_file` → 创建 doc(`source=2`，`source_id=NULL`，标题默认取文件名去扩展名，status=PENDING) → `knowledge_task(INGEST)`。一文件一 doc。手动导入 doc 的 `doc_url` SHALL 存空值（portal 引用降级为纯文本标题 +「站内知识」标记）。

#### Scenario: 批量上传成功

- **WHEN** 管理员一次上传 3 个 .md 文件并带 tags
- **THEN** 创建 3 个独立 doc（共享 tags），MinIO 各存一份源文，异步完成向量化

#### Scenario: 类型或大小非法

- **WHEN** 上传含非 .md 文件或超 10MB 文件
- **THEN** 该文件进入 rejected（`203031`/`203032`），其余合法文件正常受理

### Requirement: 重建与任务重跑

系统 SHALL 支持：单文档重建 `POST /docs/{id}/revectorize`（权限 `blog:knowledge:revectorize`，重切+重向量化+ES 重 index，先删 ES 旧 chunk 文档）、全量重建 `POST /reindex`（权限 `blog:knowledge:reindex`，删 ES index 重建 + MySQL embed_status 全重置；窗口内检索不可用）、任务重跑 `POST /tasks/{id}/rerun`（权限 `blog:knowledge:manage`，仅 FAILED/DEAD 可重跑，重置后重新投递）。任务生命周期 SHALL 为 PENDING→RUNNING→SUCCESS/FAILED/DEAD，指数退避 ≤max_retry=3；进程启动时 MUST 将遗留 RUNNING 置回 PENDING。文章来源文档的编辑/删除 MUST NOT 允许（仅手动导入可编辑/删除，错误 `203095`）。

#### Scenario: 单文档重建

- **WHEN** 管理员对 READY 文档执行 revectorize
- **THEN** 旧 ES chunk 文档先删，doc 重置 PENDING，异步完成后恢复 READY

#### Scenario: 重跑失败任务

- **WHEN** 管理员对 FAILED 任务执行 rerun
- **THEN** 任务重置 PENDING、retryCount=0 并重新投递执行

#### Scenario: 非 FAILED 任务不可重跑

- **WHEN** 对 SUCCESS(2) 或 RUNNING(1) 任务执行 rerun
- **THEN** 返回 `203105`，任务状态不变

#### Scenario: 启动自愈

- **WHEN** 进程重启时 `knowledge_task` 存在遗留 RUNNING 任务
- **THEN** 启动过程将其置回 PENDING（可被重跑/重投），不永久卡死

### Requirement: 双写一致性与降级口径

chunk 向量化 SHALL 遵循：落 MySQL（真相源）→ 生成向量 → ES index 成功 → `embed_status=READY`；ES index 失败 → `embed_status=FAILED` + fail_reason，由对账同步与任务重跑兜底。异步入库侧 AI 线程池满时 SHALL 返回 `203103`（不阻塞用户问答链路）。ES 为检索唯一入口，MUST NOT 提供降级检索通道。
