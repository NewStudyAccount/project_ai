# knowledge-base · 接口文档（知识库 / 文章入库 / RAG 问答）

> 状态：**设计稿** · 与 [knowledge-base-design.md](./knowledge-base-design.md)（主设计）、[table-design.md](./table-design.md)（库表契约）对齐
> 契约口径：`CLAUDE.md` §6.2 / §6.4（返回体、错误码、分页、Long→String、校验、幂等）
> 鉴权：纯网关（`X-User-Id` / `X-User-Name`）；admin 权限码 `blog:knowledge:*` / `blog:ai-provider:*`
> 路由：统一经 blog-gateway(9100) `/api/v1/**` → `lb://blog-service`(19100)，由 `blog-knowledge-service` 处理

---

## 0. 全局约定

| 项 | 约定 |
|----|------|
| 前缀 | `/api/v1` |
| 返回体 | `{ "code": number, "msg": string, "data": T }` |
| 分页出参 | `{ "records": T[], "total": number, "size": number, "current": number }` |
| 分页入参 | `current`（默认 1，≥1）/ `size`（默认 10，1–500 → **本域业务 clamp 200**）；`orderBy`/`order` **必须白名单**，禁止拼进 SQL |
| id 类型 | 出参一律 **string**（16 位发号字符串化）；入参传字符串 |
| 业务失败 | HTTP **200** + `code` 非 0；未登录 401；无权限 403；限流 429；ES 不可用 503 |
| 内容类型 | `application/json`（除：手动导入 multipart；问答 `text/event-stream`） |
| 幂等 | 标注 **幂等** 的写接口须带 `Idempotent-Key`（或 `X-Request-Id`）；重复请求返回首次 `Result` |
| 时间 | `yyyy-MM-dd HH:mm:ss`，时区 `Asia/Shanghai` |
| 枚举 | 库里存 code，接口只传/回 code；前端按 code 映射文案，**不解析号段** |
| 审计 | 管理端全部写操作记审计（操作人/时间/动作/结果） |

### 0.1 本域业务错误码段位（blog 系统 03 → `203xxx`）

> 与既有 `BlogErrorCodeEnum`（已用 `203010–203087` / `203900`）**零撞号**；新段随实现加入 `BlogErrorCodeEnum`。

| 段位 | 域 |
|------|----|
| `203090–203099` | 知识文档 / 文章入库 |
| `203100–203109` | 检索（ES）/ 向量化 / 任务 |
| `203110–203119` | AI 服务商（`ai_provider`） |
| `203900` | 重复请求（既有，复用） |

明细见 **§6**。

### 0.2 状态枚举（接口透出值）

| 枚举 | 取值 | 说明 |
|------|------|------|
| `ingestState` | `UNINGESTED` / `INGESTED` / `STALE` / `INVALID` | 入库状态（两维之一，判定见 [table-design.md](./table-design.md) §2.7） |
| `docStatus` | `0` PENDING / `1` VECTORIZING / `2` READY / `3` FAILED / `4` INVALID | 向量化进度（两维之二，原样透出） |
| `taskType` | `INGEST` / `REVECTORIZE` / `INVALIDATE` / `FULL_REINDEX` / `SYNC_ARTICLES` | 任务类型 |
| `taskStatus` | `0` PENDING / `1` RUNNING / `2` SUCCESS / `3` FAILED / `4` DEAD | 任务状态 |
| `source` | `1` 文章 / `2` 手动导入 / `3` 文件导入（P1） | 知识来源 |

---

## 1. 管理端 · 文章入库（`blog:knowledge:ingest`）

### 1.1 候选文章分页

| 项 | 内容 |
|----|------|
| **接口名** | 候选文章分页（文章入库页列表） |
| **方法/路径** | `GET /api/v1/admin/knowledge/candidates` |
| **权限** | `blog:knowledge:ingest` |
| **逻辑** | 薄投影表 `knowledge_article_index` LEFT JOIN `knowledge_doc`，**单库精确分页/筛选/统计** |
| **涉及表** | `knowledge_article_index`、`knowledge_doc` |

**入参（Query）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| current / size | long | 否 | 分页（size 业务上限 200） |
| keyword | string | 否 | 文章标题模糊 |
| categoryId | string | 否 | 分类筛选 |
| ingestState | string | 否 | `UNINGESTED` \| `INGESTED` \| `STALE` \| `INVALID`；空=全部 |
| orderBy | string | 否 | 白名单：`publish_time`（默认）\| `article_update_time` |
| order | string | 否 | `asc` \| `desc`（默认 desc） |

**出参 `data`：PageResult\<CandidateVO\>**

| 字段 | 类型 | 说明 |
|------|------|------|
| articleId | string | `blog_article.id` |
| title | string | 文章标题 |
| categoryId | string | 分类 id；`0`=未分类 |
| categoryName | string | 分类名（快照） |
| publishTime | string | 最近发布时间 |
| updateTime | string | 文章 `update_time` 快照 |
| contentVersion | string | 当前 live 正文 `sys_file.id`（**内容指纹**） |
| ingestState | string | 见 §0.2 |
| docId | string \| null | 已入库时的 `knowledge_doc.id` |
| docStatus | number \| null | 见 §0.2（未入库为 `null`） |
| chunkCount | number | 已入库块数（未入库 `0`） |
| lastIngestTime | string \| null | `knowledge_doc.update_time` |

**`ingestState` 判定表（唯一规则）**

| 条件 | ingestState |
|------|-------------|
| 无 `knowledge_doc(source=1, source_id=articleId, deleted=0)` | `UNINGESTED` |
| 有 doc 且 `doc.status = 4` | `INVALID` |
| 有 doc 且 `doc.content_version = 当前 live_file_id` | `INGESTED` |
| 有 doc 且指纹不等 | `STALE` |

> 边界：`doc.deleted=1` 视为 `UNINGESTED`；文章 live 正文为空（`content_file_id=0`）→ `contentVersion="0"`，前端禁用勾选（提交时返回 `203092`）。

**错误**：投影表为空 → 正常返回空列表（前端空态引导「同步文章索引」）。

### 1.2 候选统计（页头）

| 项 | 内容 |
|----|------|
| **方法/路径** | `GET /api/v1/admin/knowledge/candidates/summary` |
| **权限** | `blog:knowledge:ingest` |
| **逻辑** | 同 §1.1 口径，按 `ingestState` 聚合（全量，不受分页影响） |

**出参 `data`**

| 字段 | 类型 | 说明 |
|------|------|------|
| total | number | 投影表总行数（= 已发布文章数） |
| uningested | number | 未入库 |
| ingested | number | 已入库（指纹一致） |
| stale | number | 待更新 |
| invalid | number | 已失效 |

### 1.3 同步文章索引 　**幂等（建议）**

| 项 | 内容 |
|----|------|
| **接口名** | 同步文章索引（投影表刷新 + 失效同步） |
| **方法/路径** | `POST /api/v1/admin/knowledge/article-index/sync` |
| **权限** | `blog:knowledge:ingest` |
| **逻辑** | 经只读契约分页拉取已发布文章（status=1）→ 按 `article_id` upsert + **删除本次集合外的行**（全量覆盖）→ 再执行失效同步：`doc.source_id` 不在投影表 → doc 置 INVALID + ES `doc_status=4` |
| **涉及表** | `knowledge_article_index`、`knowledge_doc`、`knowledge_task`（INVALIDATE） |

**入参**：无（或 `{ "full": true }` 保留扩展位，P0 恒为全量）

**出参 `data`**

| 字段 | 类型 | 说明 |
|------|------|------|
| pulled | number | 本次拉取的已发布文章数 |
| inserted | number | 新增投影行 |
| updated | number | 更新投影行（指纹/标题/分类变化） |
| removed | number | 移出投影行（文章已下架/删除） |
| invalidated | number | 触发失效的知识文档数 |

> 说明：投影 upsert 为**同步动作**（不落任务）；`invalidated > 0` 时按 §2.8 落 `knowledge_task(INVALIDATE)` 异步执行 ES 更新。

### 1.4 从文章入库（批量）　**幂等**

| 项 | 内容 |
|----|------|
| **接口名** | 从文章入库（选择指定文章进行向量化） |
| **方法/路径** | `POST /api/v1/admin/knowledge/docs/from-articles` |
| **权限** | `blog:knowledge:ingest` |
| **逻辑** | 逐篇校验 → `knowledge_doc` upsert(`source=1`,`status=PENDING`) → 落 `knowledge_task(INGEST)` 并**立即投递** `knowledgeAiExecutor`；**一步到位**：入库 → 切块 → 向量化 → ES index |
| **涉及表** | `knowledge_doc`、`knowledge_task`、`knowledge_article_index`（读） |

**入参（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| articleIds | string[] | 是 | 文章 id 列表；**≤ 100 条/次**（超出返回 `30001`） |

**出参 `data`（批量结果明细）**

| 字段 | 类型 | 说明 |
|------|------|------|
| submitted | number | 受理成功数 |
| skipped | number | 跳过数 |
| rejected | number | 拒绝数 |
| accepted | array | `[{ articleId, docId, taskId, reused: boolean }]`——`reused=true` 表示复用既有 doc 行（重建） |
| skippedItems | array | `[{ articleId, reason }]`，`reason` ∈ `ALREADY_INGESTED`（内容未变）\| `IN_PROGRESS`（已有 PENDING/RUNNING 任务） |
| rejectedItems | array | `[{ articleId, reason, code }]`，`reason` ∈ `NOT_PUBLISHED`（403/下架/草稿）\| `NO_CONTENT`\| `NOT_FOUND`，`code` = §6 对应错误码 |

**语义**：**受理即返回**（入库/向量化为异步任务），前端据 `accepted[].docId/docStatus` 轮询或回列表查看进度。

**幂等**：同一 `Idempotent-Key` 重复提交返回首次结果；不同 key 但同一文章在途时 → 该篇进 `skippedItems`（`IN_PROGRESS`），不报错。

**错误**

| 场景 | code / HTTP |
|------|-------------|
| 文章不存在 | `203060`（HTTP 200） |
| 文章未发布 | `203092`（HTTP 200，`rejectedItems` 逐条） |
| 无正文（live 为空） | `203092` |
| 列表为空 / 超 100 条 | `30001`（HTTP 200） |

---

## 2. 管理端 · 知识文档（`blog:knowledge:list` 等）

### 2.1 知识文档分页

| 项 | 内容 |
|----|------|
| **方法/路径** | `GET /api/v1/admin/knowledge/docs` |
| **权限** | `blog:knowledge:list` |
| **涉及表** | `knowledge_doc`、`knowledge_chunk`（统计） |

**入参（Query）**：`current` / `size` / `keyword`(标题) / `source`(1/2/3) / `status`(docStatus) / `orderBy`(白名单：`update_time` 默认｜`chunk_count`) / `order`

**出参 `data`：PageResult\<KnowledgeDocVO\>**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | doc id |
| source | number | 1 文章 / 2 手动导入 |
| sourceId | string | 文章 id（source=1）；否则 `null` |
| title | string | |
| tags | string | 逗号分隔 |
| status | number | docStatus（§0.2） |
| chunkCount | number | 块数 |
| readyChunkCount | number | `embed_status=2` 块数（**向量健康度** = ready/chunkCount） |
| failReason | string | doc 级失败原因（`status=3` 时有值） |
| embedModel | string | 生效 embedding 模型 |
| embedDim | number | 维度 |
| contentVersion | string | 内容指纹 |
| docUrl | string | 文章公开 URL；手动导入为 `""` |
| updateTime | string | |

### 2.2 手动导入（文件上传）　**幂等**

| 项 | 内容 |
|----|------|
| **方法/路径** | `POST /api/v1/admin/knowledge/docs` |
| **权限** | `blog:knowledge:create` |
| **Content-Type** | `multipart/form-data` |
| **逻辑** | 逐文件：类型/大小校验 → put MinIO `knowledge/{docId}/{uuid}.md` → 登记 `knowledge_file` → doc(`source=2`,`status=PENDING`) → `knowledge_task(INGEST)`；**一文件一 doc**（§5 裁决 10） |
| **涉及表** | `knowledge_file`、`knowledge_doc`、`knowledge_task` |

**入参（form-data）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| files | file[] | 是 | 仅 `.md` / `text/markdown`；单文件 ≤ 10MB；多选 ≤ 20 个 |
| tags | string | 否 | 逗号分隔，写入全部 doc |

**出参 `data`**：`{ submitted, accepted:[{originalName, docId, taskId}], rejected:[{originalName, reason, code}] }`
`reason` ∈ `TYPE_NOT_ALLOWED`(`203031`) \| `TOO_LARGE`(`203032`) \| `EMPTY_CONTENT`(`203064`)

### 2.3 编辑手动导入

| 项 | 内容 |
|----|------|
| **方法/路径** | `PUT /api/v1/admin/knowledge/docs/{id}` |
| **权限** | `blog:knowledge:update` |
| **逻辑** | 仅 `source=2`；`file` 重新上传 → `content_version` 变 → 重置 PENDING 并新建 `REVECTORIZE` 任务（先删旧 ES chunk 文档）；仅改 `tags` 则只更新 doc |

**入参**：`multipart/form-data`：`file?`（新源文）、`tags?`
**出参 `data`**：`{ id, contentVersion, status, taskId? }`
**错误**：非手动导入来源 → `203095`

### 2.4 删除知识文档

| 项 | 内容 |
|----|------|
| **方法/路径** | `DELETE /api/v1/admin/knowledge/docs/{id}` |
| **权限** | `blog:knowledge:delete` |
| **逻辑** | 仅 `source=2`（文章来源由「失效/对账」处理）；逻辑删 doc + `knowledge_file` → `knowledge_task(INVALIDATE)` 清 ES 文档（`delete_by_query`） |

**出参 `data`**：`{ id, taskId }`

### 2.5 单文档重建 　**幂等（建议）**

| 项 | 内容 |
|----|------|
| **方法/路径** | `POST /api/v1/admin/knowledge/docs/{id}/revectorize` |
| **权限** | `blog:knowledge:revectorize` |
| **逻辑** | 重切 + 重向量化 + ES 重 index（先删 ES 旧 chunk 文档）；doc 重置 PENDING |

**出参 `data`**：`{ docId, taskId }`
**错误**：文档不存在 `203090`；已失效 `203093`

### 2.6 全量重建索引 　**幂等（建议）**

| 项 | 内容 |
|----|------|
| **方法/路径** | `POST /api/v1/admin/knowledge/reindex` |
| **权限** | `blog:knowledge:reindex` |
| **逻辑** | `knowledge_task(FULL_REINDEX)`：删 ES index 重建 → MySQL `embed_status` 全重置 → 重切 + 重向量化。**重建窗口内检索不可用**（P0 接受停机；P2 改 alias 滚动） |

**出参 `data`**：`{ taskId, docTotal }`（异步，前端提示"重建中，检索暂不可用"）

### 2.7 任务分页 / 重跑

| 项 | 内容 |
|----|------|
| **方法/路径** | `GET /api/v1/admin/knowledge/tasks` |
| **权限** | `blog:knowledge:manage` |
| **入参** | `current` / `size` / `taskType` / `status`(0–4) / `docId` |
| **出参** | `PageResult<TaskVO>`：`{ id, taskType, docId, docTitle, status, retryCount, maxRetry, failReason, startTime, endTime, createTime }` |

| 项 | 内容 |
|----|------|
| **接口名** | 重跑失败任务 |
| **方法/路径** | `POST /api/v1/admin/knowledge/tasks/{id}/rerun` |
| **权限** | `blog:knowledge:manage` |
| **逻辑** | 仅 `FAILED(3)` / `DEAD(4)`；重置 `status=0`、`retryCount=0` 并重新投递 |
| **出参** | `{ taskId, status }`；错误：任务不存在 `203104`、状态不允许 `203105` |

### 2.8 统计

| 项 | 内容 |
|----|------|
| **方法/路径** | `GET /api/v1/admin/knowledge/stats` |
| **权限** | `blog:knowledge:manage` |
| **出参 `data`** | `{ docTotal, docByStatus:{pending,vectorizing,ready,failed,invalid}, chunkTotal, chunkReadyRatio, esDocCount, esHealthy:boolean, taskFailed, providerCalls:{embed,chat} }` |

> `esDocCount` / `esHealthy` 取自 ES（`_count` + 集群健康）；ES 不可用时置 `null`/`false`，接口本身仍 200。

---

## 3. 管理端 · AI 服务商（`blog:ai-provider:*`）

| 接口 | 方法/路径 | 权限 | 说明 |
|------|-----------|------|------|
| 分页 | `GET /api/v1/admin/knowledge/ai-providers` | `blog:ai-provider:list` | 入参 `kind?` / `status?`；出参见下表 |
| 新增 | `POST /api/v1/admin/knowledge/ai-providers` | `blog:ai-provider:create` | body 见下；`code` 唯一（`203111`）；**幂等** |
| 编辑 | `PUT /api/v1/admin/knowledge/ai-providers/{id}` | `blog:ai-provider:update` | `apiKey` 为空=不变；置 `is_default=1` 需同 kind 唯一 |
| 删除 | `DELETE /api/v1/admin/knowledge/ai-providers/{id}` | `blog:ai-provider:delete` | 生效默认行禁止删除（`203113`） |
| 连通测试 | `POST /api/v1/admin/knowledge/ai-providers/{id}/test` | `blog:ai-provider:test` | 出参 `{ ok, latencyMs, message }`；失败 `203112` |

**出参 `data`（AiProviderVO）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | |
| code | string | 唯一标识 |
| kind | number | 1 Embedding / 2 LLM / 3 兼容 |
| type | string | `SILICONFLOW` / `DASHSCOPE` / `ZHIPU` / `OPENAI` |
| name | string | 显示名 |
| baseUrl | string | 云 API 端点（空=取 yml 引导） |
| model | string | 模型名 |
| dim | number | 向量维度（kind 含 Embedding 必填 >0） |
| apiKeyMasked | string | `sk-****abcd`（**明文永不回显**） |
| isDefault | number | 0/1（kind 维度唯一） |
| status | number | 1 启用 / 0 停用 |

**写接口入参**：`{ code, kind, type, name, baseUrl, model, dim, apiKey?(空=不变), isDefault, status }`
**约束**：Embedding 行切换 `model`/`dim` 后必须「全量重建索引」（返回提示文案，不强制阻断）；`dim` 必须与 ES `dims=1024` 一致，否则 `203114`。

---

## 4. 用户端 · 问知识库（登录用户）

### 4.1 `POST /api/v1/knowledge/ask`（SSE）

| 项 | 内容 |
|----|------|
| **接口名** | 问知识库（RAG 流式问答） |
| **权限** | 登录态（网关 JWT；**无匿名通道**）；`/api/v1/knowledge/**` 不入 public-paths |
| **请求头** | `Accept: text/event-stream`；`Idempotent-Key` **不需要**（重复提问属用户意图） |
| **限流** | `@RateLimit`：**5 次/分钟/用户** + **20 次/天/用户**；超限 HTTP **429** + `code=10003`（`msg` 区分"请求过于频繁"/"今日配额已用尽，请明日再试"） |
| **逻辑** | q 向量化 → ES `knn + match` + `filter(doc_status=2)` → RRF → Top-K → 组装上下文 → LLM 流式 → 落库会话 |
| **涉及表** | `knowledge_chat_session`、`knowledge_chat_message`（ES 为检索源，全链路不回 MySQL 取正文） |

**入参（Body）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| question | string | 是 | 1–500 字（超长 `30001`） |
| sessionId | string | 否 | 续用既有会话；空=新建并首帧回传。**仅用于会话落库与限流审计，不作为检索上下文**（无多轮记忆） |

### 4.2 SSE 帧格式（**契约**）

响应头：`Content-Type: text/event-stream; charset=UTF-8`、`Cache-Control: no-cache`、`X-Accel-Buffering: no`（配合网关禁缓冲）

帧格式：`event: <name>\n` + `data: <单行 JSON>\n\n`；每 15s 无数据时发注释心跳 `: ping\n\n`；**不使用** `retry` 与多行 `data`。

| event | data（JSON） | 时机 |
|-------|--------------|------|
| `meta` | `{ sessionId, mode, model }` | **首帧**。`mode` ∈ `RAG`（正常）/ `KEYWORD_FALLBACK`（LLM 不可用降级） |
| `delta` | `{ text }` | 增量答案片段（可多次）；追加即可 |
| `refs` | `{ refs:[ { chunkId, docId, docTitle, docUrl, score } ] }` | `delta` 结束后、`done` 之前**发送一次**；无命中时 `refs: []` |
| `done` | `{ messageId, sessionId, costMs, refCount }` | 正常结束，服务端随后关闭连接 |
| `error` | `{ code, msg }` | **流已开始后**发生故障；发送后关闭（前端保留已收内容 + 提示） |

**示例**

```
event: meta
data: {"sessionId":"2026102800000123","mode":"RAG","model":"Qwen/Qwen3-8B"}

event: delta
data: {"text":"ES 的混合检索用"}

event: delta
data: {"text":" RRF 把 kNN 与 BM25 两路排名融合。"}

event: refs
data: {"refs":[{"chunkId":"2026102800000451","docId":"2026102800000111","docTitle":"Elasticsearch 混合检索","docUrl":"/articles/es-hybrid-search","score":0.83}]}

event: done
data: {"messageId":"2026102800000452","sessionId":"2026102800000123","costMs":3120,"refCount":3}
```

**错误与降级（与 HTTP 状态配合）**

| 时机 | 表现 |
|------|------|
| **流开始前**（鉴权/限流/ES 不可用/参数非法） | 直接返回 **JSON `Result`**：401 / 403 / 429(`10003`) / 503(`203100`) / 200+`30001`，**不进入 SSE** |
| **流开始前 · AI 线程池满** | `429` + `code=10003`（`msg`「服务繁忙，请稍后重试」）；异步入库侧池满则为 `203103`（不阻塞用户问答） |
| **流开始后 ES/LLM 故障** | `event: error` + `{code,msg}`，关闭连接 |
| LLM 不可用（降级） | `meta.mode="KEYWORD_FALLBACK"`，`delta` 输出 ES `match` 命中的原文片段 + 模板化说明（「AI 组织暂不可用，以下为关键词检索结果」），末尾仍发 `refs` + `done`（**不算 error**） |
| Embedding 不可用（问题向量化失败） | 流开始前 `503` + `203101`（语义检索无降级路径） |
| 无命中 | 正常 `done`，`refs: []`，答案由 LLM 按模板回复"未检索到相关内容" |

**落库**：`done` 前写入 `knowledge_chat_message`（Q 行 + A 行，A 行含 `refs` JSON、`cost_ms`）；`error`/中断时只落 Q 行（A 行内容为已生成片段，`refs` 为空）。

### 4.3 `GET /api/v1/knowledge/related/{articleId}`（P2）

基于向量相似的相关文章推荐（ES kNN）；P0 不实现，仅占位。

---

## 5. 权限位与菜单对照

| 权限位 | 用途 | 菜单/按钮（[table-design.md](./table-design.md) §4.1） |
|--------|------|------|
| `blog:knowledge:list` | 知识文档列表 | 菜单 `2009` |
| `blog:knowledge:ingest` | 文章入库页（含候选/同步/提交） | 菜单 `2012` |
| `blog:knowledge:create` | 手动导入 | 菜单 `2011` |
| `blog:knowledge:update` / `delete` | 编辑 / 删除知识 | 按钮 `90023` / `90024` |
| `blog:knowledge:revectorize` | 单文档重建 | 按钮 `90025` |
| `blog:knowledge:reindex` | 全量重建 | 按钮 `90026` |
| `blog:knowledge:manage` | 任务/统计/对账 | 按钮 `90027` |
| `blog:ai-provider:list / create / update / delete / test` | AI 服务商 | 菜单 `2010` + 按钮 `90028–90031` |

> 问答（§4）无独立权限位，仅要求登录态。

---

## 6. 本域错误码明细（`BlogErrorCodeEnum` 新增项）

| code | 枚举名 | 文案 | 典型场景 |
|------|--------|------|----------|
| 203090 | KNOWLEDGE_DOC_NOT_FOUND | 知识文档不存在 | 编辑/重建/删除 |
| 203091 | KNOWLEDGE_DOC_ALREADY_INGESTED | 该文章已入库且内容未变化 | `from-articles` 跳过 |
| 203092 | KNOWLEDGE_ARTICLE_NOT_INGESTABLE | 文章未发布或无正文，不可入库 | 候选校验 |
| 203093 | KNOWLEDGE_DOC_INVALID | 知识文档已失效，请先重新入库 | 重建失效文档 |
| 203095 | KNOWLEDGE_DOC_SOURCE_NOT_MANUAL | 仅手动导入的知识可编辑/删除 | 对 source=1 误操作 |
| 203100 | KNOWLEDGE_SEARCH_UNAVAILABLE | 检索服务暂不可用 | ES 故障（HTTP 503） |
| 203101 | KNOWLEDGE_EMBED_FAILED | 文本向量化失败 | Embedding 云 API 失败 |
| 203102 | KNOWLEDGE_LLM_UNAVAILABLE | AI 组织服务暂不可用 | LLM 故障（降级时**不抛**，仅记日志） |
| 203103 | KNOWLEDGE_TASK_QUEUE_FULL | 任务队列已满，请稍后重试 | 异步入库投递失败 |
| 203104 | KNOWLEDGE_TASK_NOT_FOUND | 任务不存在 | 重跑 |
| 203105 | KNOWLEDGE_TASK_STATE_INVALID | 仅失败任务可重跑 | 重跑 |
| 203110 | AI_PROVIDER_NOT_FOUND | AI 服务商不存在 | CRUD |
| 203111 | AI_PROVIDER_CODE_EXISTS | 服务商编码已存在 | 新增 |
| 203112 | AI_PROVIDER_TEST_FAILED | 连通性测试失败 | 测试 |
| 203113 | AI_PROVIDER_IN_USE | 生效默认服务商，禁止删除/停用 | 删除/停用 |
| 203114 | AI_PROVIDER_DIM_MISMATCH | 向量维度与索引不一致，需重建索引 | 切换 Embedding 模型 |
| 203900 | （既有）DUPLICATE_REQUEST_IN_PROGRESS | 请求处理中，请勿重复提交 | 幂等冲突 |

> 系统段 `10001–10004`、校验段 `30001` 沿用 `SysErrorCodeEnum`，本域不新增。
> 新增业务码须先在本节分配，再落 `BlogErrorCodeEnum`；`docs/error-code-ranges.md` 只登记**系统号**（03=blog），号段明细以本文件 + 枚举为准。

---

## 7. 效力与变更

- 效力顺序：`CLAUDE.md`（契约） > [table-design.md](./table-design.md)（库表） > 本文件（接口） > 主设计 §7（草案） > HTML 原型。
- 本文件定稿后，主设计 §7 的接口草案以本文件为准；字段/枚举/错误码变更须同步 `BlogErrorCodeEnum` 与前端类型定义。
- 未定稿项（明确留给实现/后续）：`related`（P2）、问答多轮上下文、`sessionId` 的会话列表查询接口。

### 变更记录

| 日期 | 变更 |
|------|------|
| 2026-10-28 | 建立：文章入库（`candidates` / `candidates/summary` / `article-index/sync` / `from-articles`）、知识文档、AI 服务商、SSE 问答帧格式、错误码段位（`203090–203114`） |
