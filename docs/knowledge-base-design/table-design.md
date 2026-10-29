# knowledge-base · 库表设计（新增表 / 种子 / ES index）

> 状态：**设计稿**；字段级契约唯一源为本文件，主设计 [knowledge-base-design.md](./knowledge-base-design.md) §6 为草案（本文件定稿后以本文件为准）
> 口径：审计 5 字段 / 16 位发号 / `id` 对外 String / 无物理外键 / `uk_` 不做写幂等 / 单表索引 ≤5（`CLAUDE.md` §6.4–6.6）
> 落点：MySQL（`blog_db`）与 ES（index `knowledge_chunk_v1`）；实施 SQL 与索引脚本**人工执行**，生产执行前须按 `CLAUDE.md` §7 征询
> 相关：ER 图 [knowledge-er-diagram.html](./knowledge-er-diagram.html) · 接口契约（待建 `api-design.md`）

---

## 1. 结论清单（先看这个）

### 1.1 既有表：需要修改吗？

| 既有表 | 结论 | 说明 |
|--------|------|------|
| `blog_article` / `blog_article_tag` / `blog_article_detail` | **不改** | 知识来源，仅经 `ArticleContentProvider` 只读回拉（标题/分类/标签/正文） |
| `sys_file` | **不改** | 文章正文对象登记仍归 `sys_file`；手动导入源文走新表 `knowledge_file`，**不混登记、不另建平行 OSS 表** |
| `sys_sequence` | **不改** | 16 位发号沿用 |
| `sys_menu` / `sys_role` / `sys_user_role` / `sys_role_menu` | **不改（仅种子 INSERT）** | knowledge 菜单/权限位为**种子数据**，短号见 §4.1 |
| `sys_storage_provider` | **不改** | AI 服务商是**另一套**配置（`ai_provider`），不复用存储服务商表 |

**结论：既有业务表零改动；新增 8 张表 + 1 个 ES index + RBAC 种子。**

### 1.2 需要新增的表（8 张，全部落 `blog_db`）

| # | 表名 | 用途 | 必要性 | 设计正文 |
|---|------|------|--------|---------|
| 1 | `knowledge_doc` | 知识文档（入库单元，两来源） | 必须 | §6.1 |
| 2 | `knowledge_chunk` | 知识块（切块真相源，无向量 BLOB） | 必须 | §6.2 |
| 3 | `knowledge_file` | 手动导入源文的对象登记 | 必须 | §6.3 |
| 4 | `ai_provider` | AI 服务商配置（Embedding/LLM） | 必须 | §6.4 |
| 5 | `knowledge_chat_session` | 问答会话（P0 最小形态） | 必须 | §6.5 |
| 6 | `knowledge_chat_message` | 问答消息（Q/A + 引用 + 耗时） | 必须 | §6.5 |
| 7 | `knowledge_task` | 入库/重建/失效/对账任务（可重放、可观测、可重跑） | 必须 | 本文件 §2.6（**新增，见 §5 裁决 9**） |
| 8 | `knowledge_article_index` | **薄投影表**：blog 已发布文章只读投影（候选列表 / 待更新判定的单库数据源） | 必须 | 本文件 §2.7（**新增，见 §5 裁决 15**） |

### 1.3 库与组件落点

| 项 | 取值 |
|----|------|
| MySQL 库 | **`blog_db`**（**Q5 已裁决**：合并，不建独立 `knowledge_db`、不引 dynamic-datasource；`knowledge_*` 表前缀隔离域边界，未来拆进程时迁表） |
| MySQL 建表 SQL | `deploy/db/migration/blog_db/07_knowledge_tables.sql`（续既有 06 号） |
| MySQL 种子 SQL | `deploy/db/seed/2026-10-28-blog-knowledge.sql`（日期前缀对齐既有命名） |
| ES index | `knowledge_chunk_v1`（**仅此一个 index**，不扩 blog 业务检索）；初始化脚本建议 `deploy/es/knowledge_chunk_v1.json` + Kibana Dev Tools / curl 执行 |
| ES client 落点 | `blog-knowledge-service`（**CLAUDE.md §2.1「例外（检索引擎 ES client）」**，见主设计 §5.3 依赖行） |

---

## 2. 新增表结构（DDL）

> 对齐 `docs/object-storage-design` 与 blog 域 DDL 写法：`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`、`IF NOT EXISTS` 可重复执行、审计 5 列齐套。
> 下列 DDL 中**与主设计 §6 草案不一致处已在 §5 登记**（不隐式改契约）。

### 2.0 `knowledge_doc` 知识文档

```sql
CREATE TABLE IF NOT EXISTS knowledge_doc (
  id BIGINT NOT NULL COMMENT '16 位发号',
  source TINYINT NOT NULL COMMENT '1=文章 / 2=手动导入 / 3=文件导入(P1)',
  source_id BIGINT NULL DEFAULT NULL COMMENT 'source=1 时=blog_article.id；source=2/3 时为 NULL（不写 0，否则撞唯一键）',
  title VARCHAR(200) NOT NULL COMMENT '知识标题',
  content_version VARCHAR(64) NOT NULL DEFAULT '' COMMENT '内容版本指纹：文章=live sys_file.id；手动=knowledge_file.id；变更触发重建',
  file_id BIGINT NOT NULL DEFAULT 0 COMMENT 'source=2/3 时 → knowledge_file.id',
  tags VARCHAR(255) NOT NULL DEFAULT '' COMMENT '标签（逗号分隔）',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 PENDING / 1 VECTORIZING / 2 READY / 3 FAILED / 4 INVALID',
  chunk_count INT NOT NULL DEFAULT 0 COMMENT '切块数（READY 口径）',
  fail_reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'doc 级失败原因（见 §5 裁决 6）',
  embed_model VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'embedding 模型（重建后覆盖；与 ES dims 校验）',
  embed_dim INT NOT NULL DEFAULT 0 COMMENT '向量维度',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 未删 / 1 已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_knowledge_doc_source (source, source_id),
  KEY idx_knowledge_doc_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档';
```

**唯一键语义（必须遵守）：** `source=1` 同一文章**复用既有 doc 行（UPDATE）**，不重复 INSERT；`source=2/3` 时 `source_id=NULL`——MySQL 唯一索引不对 NULL 去重，故多份手动导入互不冲突。

### 2.1 `knowledge_chunk` 知识块（业务字段真相源，无向量 BLOB）

```sql
CREATE TABLE IF NOT EXISTS knowledge_chunk (
  id BIGINT NOT NULL COMMENT '16 位发号；= ES 文档 _id',
  doc_id BIGINT NOT NULL COMMENT '→ knowledge_doc.id',
  seq INT NOT NULL DEFAULT 0 COMMENT '块序号',
  heading VARCHAR(200) NOT NULL DEFAULT '' COMMENT '所属标题链',
  content TEXT NOT NULL COMMENT '块文本（含上下文增强前缀）；同步至 ES content 字段',
  token_count INT NOT NULL DEFAULT 0 COMMENT '估算 token 数',
  embed_status TINYINT NOT NULL DEFAULT 0 COMMENT '0 PENDING / 1 VECTORIZING / 2 READY / 3 FAILED（READY = 向量生成 + ES index 成功）',
  fail_reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '失败原因（provider/ES 错误码）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 未删 / 1 已删',
  PRIMARY KEY (id),
  KEY idx_knowledge_chunk_doc (doc_id, seq),
  KEY idx_knowledge_chunk_status (embed_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识块（向量在 ES，本表为业务真相源）';
```

**不存 embedding BLOB**：向量 + content 镜像存 ES（§3）；ES index 失败可凭本表 `content` 重建向量并重 index。

### 2.2 `knowledge_file` 知识对象登记（手动导入源文）

```sql
CREATE TABLE IF NOT EXISTS knowledge_file (
  id BIGINT NOT NULL COMMENT '16 位发号',
  bucket VARCHAR(64) NOT NULL COMMENT '桶（共用 blog 桶）',
  object_key VARCHAR(255) NOT NULL COMMENT 'knowledge/{docId}/{uuid}.md',
  original_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '上传原始文件名',
  mime_type VARCHAR(64) NOT NULL COMMENT 'text/markdown',
  file_size BIGINT NOT NULL DEFAULT 0 COMMENT '字节数（展示用）',
  biz_type VARCHAR(32) NOT NULL COMMENT 'KNOWLEDGE_SOURCE',
  biz_id BIGINT NOT NULL DEFAULT 0 COMMENT '→ knowledge_doc.id',
  storage_code VARCHAR(64) NOT NULL COMMENT '对齐 sys_storage_provider.code / sys_file.storage_code（列宽 64）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_knowledge_file_biz (biz_type, biz_id),
  KEY idx_knowledge_file_key (object_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识对象登记（手动导入源文）';
```

### 2.3 `ai_provider` AI 服务商配置（复刻 `sys_storage_provider` 模式）

```sql
CREATE TABLE IF NOT EXISTS ai_provider (
  id BIGINT NOT NULL COMMENT '16 位发号',
  code VARCHAR(32) NOT NULL COMMENT '服务商标识，系统内唯一',
  kind TINYINT NOT NULL COMMENT '1=Embedding / 2=LLM / 3=兼容',
  type VARCHAR(32) NOT NULL COMMENT 'SILICONFLOW / DASHSCOPE / ZHIPU / OPENAI',
  name VARCHAR(64) NOT NULL COMMENT '显示名',
  base_url VARCHAR(255) NOT NULL DEFAULT '' COMMENT '云 API 端点（留空由 yml 引导覆盖）',
  model VARCHAR(64) NOT NULL COMMENT '模型名',
  dim INT NOT NULL DEFAULT 0 COMMENT '向量维度（kind 含 Embedding 时必填）',
  api_key VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'dev/test 明文或引导留空；**生产加密另开变更**',
  is_default TINYINT NOT NULL DEFAULT 0 COMMENT 'kind 维度唯一默认（应用层保证，无 DB 约束）',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_provider_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 服务商配置（Embedding / LLM）';
```

**约束（应用层，DB 不建约束）：** 同一知识库同一时刻**只允许一个生效 Embedding provider**（dim/model 混存会破坏检索语义）；切换 provider 必须走「重建索引」。

### 2.4 `knowledge_chat_session` 问答会话

```sql
CREATE TABLE IF NOT EXISTS knowledge_chat_session (
  id BIGINT NOT NULL COMMENT '16 位发号',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0 COMMENT '归属用户 = 审计 create_by（不另设 user_id，见 §5 裁决 3）',
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_knowledge_chat_session_user (create_by, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识问答会话';
```

### 2.5 `knowledge_chat_message` 问答消息

```sql
CREATE TABLE IF NOT EXISTS knowledge_chat_message (
  id BIGINT NOT NULL COMMENT '16 位发号',
  session_id BIGINT NOT NULL COMMENT '→ knowledge_chat_session.id',
  role CHAR(1) NOT NULL COMMENT 'Q 提问 / A 回答',
  content TEXT NOT NULL COMMENT '消息正文（回答为完整文本，非流式片段）',
  refs JSON NULL DEFAULT NULL COMMENT '引用来源数组 [{chunkId,docId,docTitle,docUrl,score}]',
  cost_ms INT NOT NULL DEFAULT 0 COMMENT '生成耗时（ms；Q 为 0）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_knowledge_chat_message_session (session_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识问答消息';
```

> P0 仅落库审计与展示；**多轮上下文不属于当前范围**（`sessionId` 不作为检索的对话上下文）。

### 2.6 `knowledge_task` 任务表（**新增，见 §5 裁决 9**）

```sql
CREATE TABLE IF NOT EXISTS knowledge_task (
  id BIGINT NOT NULL COMMENT '16 位发号',
  task_type VARCHAR(32) NOT NULL COMMENT 'INGEST 入库+向量化 / REVECTORIZE 单文档重建 / INVALIDATE 下架/删除同步 / FULL_REINDEX 全量重建 / SYNC_ARTICLES 对账补偿',
  doc_id BIGINT NOT NULL DEFAULT 0 COMMENT '目标文档；全局任务（FULL_REINDEX / SYNC_ARTICLES）为 0',
  payload JSON NULL DEFAULT NULL COMMENT '任务参数（source/sourceId/action/contentVersion 等）',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 PENDING / 1 RUNNING / 2 SUCCESS / 3 FAILED / 4 DEAD（超重试上限）',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
  max_retry INT NOT NULL DEFAULT 3 COMMENT '重试上限（对齐 CLAUDE.md §6.11：指数退避最多 3 次）',
  next_retry_time DATETIME NULL DEFAULT NULL COMMENT '下次可执行时间（退避留痕/重跑依据）',
  fail_reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '失败原因',
  start_time DATETIME NULL DEFAULT NULL COMMENT '本次执行开始',
  end_time DATETIME NULL DEFAULT NULL COMMENT '本次执行结束',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_knowledge_task_status_next (status, next_retry_time),
  KEY idx_knowledge_task_doc (doc_id, task_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库任务（入库/重建/失效/对账）';
```

**任务生命周期（P0 口径）：**

| 环节 | 约定 |
|------|------|
| 入队 | **管理端操作**（文章入库 / 文件导入 / 重建 / 失效 / 对账）→ 写 `status=0` → **立即投递** `knowledgeAiExecutor` 执行，不等轮询（**无自动事件来源**，见 §5 裁决 11–14） |
| 去重 | **应用层保证**（DB 不建约束）：同 `(doc_id, task_type)` 已存在 `PENDING/RUNNING` 则跳过入队 |
| 执行 | `0 → 1 RUNNING → 2 SUCCESS`；瞬时失败（超时/5xx）由 `framework` 重试机制按 §6.11 指数退避重试 ≤ `max_retry` |
| 失败终态 | 仍失败 → `3 FAILED`（记 `retry_count`/`fail_reason`）；超上限 → `4 DEAD` |
| 重跑 | 管理端「重跑失败任务 / 重建文档 / 对账同步」把任务重置 `0` 并重新投递（`FAILED/DEAD` 均可重跑） |
| 启动恢复 | 进程启动时把遗留 `RUNNING` 置回 `PENDING`，避免卡死 |
| 定期扫描 | **P0 不引入**（`CLAUDE.md` §2.2 不做分布式定时）；P1 若需定期扫描，**须先改 `CLAUDE.md` §2.2/§2 技术栈登记**，并处理多实例互斥（`blog-server` 当前单实例假设） |

> 与 `knowledge_chunk.embed_status` 的分工：**任务表 = doc/操作级调度与留痕**；`embed_status` = chunk 级向量化状态（真相源）。二者不互为状态源，仅 `task.doc_id` 关联。

### 2.7 `knowledge_article_index` 薄投影表（**新增，见 §5 裁决 15**）

> 依据 `CLAUDE.md` §5.6.1「跨服务只读展示 → 薄投影表」：knowledge 侧**只读投影** blog 已发布文章，使「文章入库」页的候选列表、`ingestState` 筛选与统计**在 knowledge 单库内完成**（分页/排序/统计精确），无需跨域读表。

```sql
CREATE TABLE IF NOT EXISTS knowledge_article_index (
  id BIGINT NOT NULL COMMENT '16 位发号',
  article_id BIGINT NOT NULL COMMENT 'blog_article.id',
  title VARCHAR(200) NOT NULL COMMENT '文章标题快照',
  category_id BIGINT NOT NULL DEFAULT 0 COMMENT '分类 id',
  category_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT '分类名快照',
  publish_time DATETIME NULL DEFAULT NULL COMMENT '最近发布时间',
  article_update_time DATETIME NULL DEFAULT NULL COMMENT '文章 update_time 快照',
  live_file_id BIGINT NOT NULL DEFAULT 0 COMMENT '当前 live 正文 sys_file.id（= blog_article_detail.content_file_id，**内容指纹**）',
  word_count INT NOT NULL DEFAULT 0 COMMENT '线上字数',
  synced_at DATETIME NOT NULL COMMENT '本行同步时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0,
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_knowledge_article_index_article (article_id),
  KEY idx_knowledge_article_index_pub (publish_time),
  KEY idx_knowledge_article_index_cat (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已发布文章只读投影（供文章入库页）';
```

**候选列表查询（knowledge 单库）：**

```sql
-- 驱动表 = 投影表；LEFT JOIN doc 计算 ingestState（UNINGESTED / INGESTED / STALE / INVALID）
SELECT i.article_id, i.title, i.category_id, i.category_name, i.publish_time, i.article_update_time,
       i.live_file_id, d.id AS doc_id, d.status AS doc_status, d.chunk_count, d.update_time AS last_ingest_time,
       CASE
         WHEN d.id IS NULL                       THEN 'UNINGESTED'
         WHEN d.status = 4                       THEN 'INVALID'
         WHEN d.content_version = i.live_file_id THEN 'INGESTED'
         ELSE 'STALE'
       END AS ingest_state
FROM knowledge_article_index i
LEFT JOIN knowledge_doc d
       ON d.source = 1 AND d.source_id = i.article_id AND d.deleted = 0
WHERE i.deleted = 0
  AND (:categoryId IS NULL OR i.category_id = :categoryId)
  AND (:keyword   IS NULL OR i.title LIKE CONCAT('%', :keyword, '%'))
  AND (:ingestState IS NULL OR :ingestState = (
        CASE WHEN d.id IS NULL THEN 'UNINGESTED'
             WHEN d.status = 4 THEN 'INVALID'
             WHEN d.content_version = i.live_file_id THEN 'INGESTED'
             ELSE 'STALE' END))     -- 精确分页/计数
ORDER BY i.publish_time DESC;   -- 白名单：publish_time | article_update_time
```

- **`ingestState` 两维分离**（§5 裁决 17）：`ingestState` 只答「入/未入/待更新/已失效」；向量化进度由 `docStatus`（0 PENDING / 1 VECTORIZING / 2 READY / 3 FAILED / 4 INVALID）单列透出。
- **判定口径**：`content_version` 指纹 = 文章当前 live 正文 `sys_file.id`（与 `knowledge_doc.content_version` 定义一致）；重发布但正文未变 → 仍为 `INGESTED`（不误报待更新）。
- **只保留 `status=1` 的行**（§5 裁决 16）：因此 `doc.source_id NOT IN 投影表` ⇒ 文章已下架/删除 ⇒ 对账时可置 doc INVALID + ES `doc_status=4`。

**同步机制（三个触发点）：**

| 触发 | 动作 |
|------|------|
| 管理端「同步文章索引」/ 对账按钮 | 经只读契约**分页拉取**已发布文章投影 → 按 `article_id` **upsert**；并**删除本次集合外的行**（全量覆盖语义）；随后执行失效同步（`doc.source_id` 不在投影表 → doc INVALID + ES 置 4） |
| 入库成功（`knowledge_task` SUCCESS） | 刷新该行 `live_file_id` / `article_update_time` / `synced_at`（增量） |
| 首次使用（投影表为空） | 候选页空态提示「请先同步文章索引」，**不自动全量拉取** |

- 量级：文章千级、`size=200` 分页循环拉取；幂等（`uk_knowledge_article_index_article`）。
- 该同步为**管理端同步动作**（投影 upsert 不落 `knowledge_task`）；其中"失效同步"若涉及 doc 重建/ES 更新，则按 §2.6 落 `knowledge_task(INVALIDATE)`。
- **blog 侧仍只读**：投影数据来自 §7.3 只读契约，blog 一行业务逻辑不改。

---

## 3. ES index `knowledge_chunk_v1`（检索索引）

> 检索唯一入口；`knowledge_chunk` 为真相源，本 index 为检索镜像。**重建 = 删 index 重建**。

```json
PUT /knowledge_chunk_v1
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "refresh_interval": "1s"
  },
  "mappings": {
    "properties": {
      "chunk_id":   { "type": "keyword" },
      "doc_id":     { "type": "long" },
      "heading":    { "type": "text", "analyzer": "ik_smart" },
      "content":    { "type": "text", "analyzer": "ik_smart" },
      "embedding":  { "type": "dense_vector", "dims": 1024,
                      "index": true, "similarity": "cosine",
                      "index_options": { "type": "hnsw", "m": 16, "ef_construction": 100 } },
      "source":     { "type": "byte" },
      "tags":       { "type": "keyword" },
      "doc_status": { "type": "byte" },
      "doc_title":  { "type": "text", "analyzer": "ik_smart" },
      "doc_url":    { "type": "keyword" }
    }
  }
}
```

| 项 | 约定 |
|----|------|
| 文档 `_id` | = `knowledge_chunk.id`（16 位发号字符串化），幂等覆盖写 |
| `doc_url` 取值 | 文章 doc = 公开详情 URL；**手动导入 doc = 空值**（portal 引用降级为纯文本标题 +「站内知识」标记，不暴露 admin 路由；§5 裁决 7） |
| `doc_status` | **与 `knowledge_doc.status` 严格同源**：0 PENDING / 1 VECTORIZING / 2 READY / 3 FAILED / 4 INVALID；检索 filter 仅放行 `2` |
| 分词 | `content` / `heading` 用 `ik_smart`（**P0 装 ik 分词器插件**，版本随 ES 9.5.5 严格对齐）；`ik_max_word` 留 P1 对比调优 |
| 检索 DSL | `knn(embedding)` + `match(content)`（BM25）双路各取 max(K×4,20) 候选窗 + `filter(doc_status=2)` → **应用层 RRF 融合**（k=60；ES 原生 `rank.rrf` 属铂金 license、basic 报 `security_exception`，2026-10-29 裁决改应用层融合）→ Top-K（含 `content`/`doc_title`/`doc_url`，出处无需回 MySQL） |
| 分片/副本 | 测试单机：1 分片 0 副本；**生产：副本 ≥1** |
| dims 校验 | `dims=1024` 必须与生效 `ai_provider.dim` 一致；换 model/dim 必须「重建索引」 |
| 演进 | index 名带 `_v1`；mapping 破坏性变更须升 `_v2` + alias 滚动（**P2**，P0 全量重建有停机窗口） |
| 降级 | ES 不可用 → 问答统一 503 + 审计告警（ES 为检索唯一入口，无降级通道，不产出低质答案） |

---

## 4. 种子清单

### 4.1 RBAC 菜单 / 按钮（`sys_menu`）

> 号段遵循 `docs/template/rbac-design.md` §2.0：目录 `100x`、二级菜单 `200x`、按钮 `90000x`。
> **占用现状（blog 已用，2026-10-29 连库核对）：** `1001–1003`、`2001–2009`、`90001–90026`、`sys_role_menu 6001–6038`（其中 `2009`/`90023–90026`/`6034–6038` 为 storage-provider 域占用，原续号计划与之冲突，已改号）。
> **本域续号：** `1004`、`2010–2013`、`90027–90035`、`sys_role_menu 6039–6052`。

| id | parent | type | 名称 | permission | path / component |
|----|--------|------|------|-----------|------------------|
| 1004 | 0 | 1 | 知识库 | — | 目录（icon 待定） |
| 2013 | 1004 | 2 | 知识文档 | `blog:knowledge:list` | `/knowledge/docs` · `views/knowledge/DocList.vue` |
| 2012 | 1004 | 2 | **文章入库** | `blog:knowledge:ingest` | `/knowledge/ingest` · `views/knowledge/ArticleIngest.vue` |
| 2011 | 1004 | 2 | 手动导入 | `blog:knowledge:create` | `/knowledge/import` · `views/knowledge/ImportDoc.vue` |
| 2010 | 1004 | 2 | AI 服务商 | `blog:ai-provider:list` | `/knowledge/ai-providers` · `views/knowledge/AiProviderList.vue` |
| 90032 | 2013 | 3 | 编辑知识 | `blog:knowledge:update` | — |
| 90033 | 2013 | 3 | 删除知识 | `blog:knowledge:delete` | — |
| 90034 | 2013 | 3 | 重建本文 | `blog:knowledge:revectorize` | 单文档重切 + 重向量化（§5 裁决 2） |
| 90035 | 2013 | 3 | 重建索引 | `blog:knowledge:reindex` | 全量重建（删 index 重建，有停机窗口） |
| 90027 | 2013 | 3 | 知识库运维 | `blog:knowledge:manage` | 对账同步 + 统计/ES 健康度 + 重跑失败任务 |
| 90028 | 2010 | 3 | 新建服务商 | `blog:ai-provider:create` | — |
| 90029 | 2010 | 3 | 编辑服务商 | `blog:ai-provider:update` | — |
| 90030 | 2010 | 3 | 删除服务商 | `blog:ai-provider:delete` | — |
| 90031 | 2010 | 3 | 测试连通 | `blog:ai-provider:test` | — |

- 菜单排序：`2013` sort=1 / `2012` sort=2 / `2011` sort=3 / `2010` sort=4。
- `component` 路径实施时须与 `blog-admin` 路由（`componentByPath`）对齐；原型 tab 与菜单对应：知识文档 `2013` / 手动导入 `2011` / AI 服务商 `2010`（原型需补「文章入库」tab，见 [knowledge-prototype.html](./knowledge-prototype.html)）。
- **文章入库**（§5 裁决 14）与**手动导入**（§5 裁决 4）均为独立菜单页、各自独立权限位（`ingest` / `create`），均为**手动触发**。
- `sys_role_menu` 绑定 `admin(9001)`：`6039→1004`、`6040→2013`、`6041→2012`、`6042→2011`、`6043→2010`、`6044–6047→90032–90035`、`6048→90027`、`6049–6052→90028–90031`。
- 种子写法沿用 `INSERT IGNORE` + 按 id `UPDATE`（幂等，可重复执行）。

### 4.2 `ai_provider` 引导默认

```sql
-- 引导默认（code/type/model/dim/is_default 落库；base_url/api_key 由 yml 引导或管理端录入，禁止明文密钥入仓）
INSERT IGNORE INTO ai_provider
  (id, code, kind, type, name, base_url, model, dim, api_key, is_default, status, create_by, update_by)
VALUES
  (2026102800000001, 'siliconflow-embed', 1, 'SILICONFLOW', 'SiliconFlow Embedding（默认）',
   '', 'BAAI/bge-m3', 1024, '', 1, 1, 0, 0),
  (2026102800000002, 'siliconflow-llm', 2, 'SILICONFLOW', 'SiliconFlow LLM（默认）',
   '', '', 0, '', 1, 1, 0, 0);
```

- **已裁决（§5 裁决 5）：** Embedding 与 LLM **各一行**（`kind=1` / `kind=2`）——`ai_provider.model` 是单列，无法同时承载两个模型；`kind=3`「兼容」语义保留但首版不用。
- `is_default` 按 kind 维度唯一（应用层保证）：两行各为其 kind 的默认行；**换 Embedding 行的 model/dim 必须走全量重建索引**。
- LLM 具体型号（`siliconflow-llm.model`）按 **Q1** 于 P0-2 接入时锁定并回填 `docs/test-env.md`。
- `base_url` 建议 `https://api.siliconflow.cn/v1`（OpenAI 兼容端点），由 yml `blog.ai.providers[]` 引导提供，DB 优先。

### 4.3 ES index

非 SQL：按 §3 的 `PUT` 语句在 Kibana Dev Tools / curl 手工执行；创建后校验 `GET /knowledge_chunk_v1/_mapping` 含 `embedding.dims=1024`、`content.analyzer=ik_smart`。

---

## 5. 与设计正文的偏差 / 裁决记录（17 项已全部落定，2026-10-28）

| # | 项 | 裁决方向 | 落点 |
|---|----|----------|------|
| 1 | `knowledge_file.storage_code` 列宽 | **VARCHAR(64)**（主设计 §6.3 草案写 32） | §2.2；对齐 `sys_storage_provider.code` / `sys_file.storage_code` |
| 2 | 单文档重建权限位 | **独立 `blog:knowledge:revectorize`**（与全量 `reindex` 分权） | §4.1 `90034`；主设计 §7.1 权限位列表补一项 |
| 3 | 会话归属用户列 | **不另设**，取审计 `create_by` | §2.4；消除与审计 5 列重复 |
| 4 | 手动导入页形态 | **独立菜单页 `2011`**（权限位 = `blog:knowledge:create`） | §4.1；对齐原型 tab |
| 5 | `ai_provider` 模型承载 | **拆 `kind=1` / `kind=2` 两行**（主设计 §5.2 的 `kind=3` 单行改） | §2.3、§4.2；主设计 §5.2 seed 同步改 |
| 6 | doc 级失败原因 | **加列** `fail_reason VARCHAR(500)` | §2.0；主设计 §6.1 草案同步 |
| 7 | 手动导入 doc 的 `doc_url` | **存空值**，portal 引用降级为纯文本标题 +「站内知识」标记（不暴露 admin 路由） | §3；前端契约待 `api-design.md` |
| 8 | `tags` 与 `blog_tag` 关系 | **保持逗号分隔串**（知识标签与站点标签解耦；ES 侧按 `tags` keyword 过滤） | §2.0；将来要运营筛选再升 M:N（加表，另开变更） |
| 9 | 对账/重试机制 | **新增 `knowledge_task` 表**（任务表 = doc/操作级调度与留痕；`chunk.embed_status` = chunk 级真相源；P0 不引定时，重跑走管理端） | §2.6；主设计 §5.3①、§5.4 措辞同步 |
| 10 | 手动导入批量粒度 | **一文件一 doc**（一次提交多行） | §2.0 `source=2`；标题默认取文件名 |
| 11 | 文章入库触发方式 | **推翻自动，改手动**：发布后不自动入库；管理端「文章入库」页勾选已发布文章触发 | 主设计 §3/§5.3/§5.4/§8 已改写；§2.6 入队来源 |
| 12 | blog 读取边界 | **只读契约**：`blog-knowledge-api` 定义 `ArticleContentProvider`，`blog-service` 仅新增只读实现类；**不改 blog 既有逻辑、不发事件** | 主设计 §7.3；§2.6 无事件来源 |
| 13 | 下架/删除的 ES 失效 | **同样手动**：管理端「失效」按钮 + 对账 `article-index/sync`（失效同步分支） | 主设计 §3④/§5.4；[api-design.md](./api-design.md) §1.3 |
| 14 | 入库入口与权限 | **独立菜单 `2012` + 独立权限 `blog:knowledge:ingest`**（与文件导入的 `create` 分权） | §4.1 |
| 15 | 候选列表的 `ingestState` 服务端筛选 | **新增薄投影表 `knowledge_article_index`**（`CLAUDE.md` §5.6.1 允许），候选列表/筛选/统计全部 knowledge 单库精确完成；blog 侧仍只读 | §2.7、§1.2 |
| 16 | 候选列表范围 | **仅 `status=1` 已发布文章**（投影表只保留已发布行）；已下架/删除的失效处理走「知识文档」页 + 对账 | §2.7 |
| 17 | 状态维度 | **两维分离**：`ingestState`（UNINGESTED/INGESTED/STALE/INVALID）+ `docStatus`（0/1/2/3/4 原样透出） | §2.7 |

> **连带修正（已同步）：** 主设计 §5.2 引导 seed 由 `kind=3` 单行改为 Embedding/LLM 两行；§7.1 权限位补 `revectorize` 与 `ingest`、新增 `candidates` / `candidates/summary` / `from-articles` / `tasks/{id}/rerun` 接口；§5.3①/§5.4 的「任务表」口径统一为 `knowledge_task`；**`ArticleKnowledgeEvent` 已废弃**（主设计 §4 概念表、§7.3 已改）；§7.3 只读契约收敛为「投影分页拉取 + 正文/元数据回拉」两个方法；**表数 7 → 8**。**新增** `knowledge_article_index`（§2.7），§4.1 的 `2012` 页含「同步文章索引」页内动作（权限同为 `blog:knowledge:ingest`，不新增按钮短号）。

**遗留（非本域 / 后续期）：**

1. **[非本域] blog 种子撞号：** `deploy/db/seed/2026-10-20-blog-storage-provider.sql` 的短号（`2005` / `90012–90015` / `6018–6022`）与 `2026-10-13-blog-content-domain.sql` **撞号**，`INSERT IGNORE` 会使「存储服务商」菜单实际未入库 —— 本域续号已避开，blog 侧需另行修复。
2. **[P1] 定期扫描：** 若需周期性对账/重跑（`knowledge_task` 扫描），须**先改 `CLAUDE.md` §2.2 / §2 技术栈登记**（当前不做分布式定时），并处理多实例互斥（现 `blog-server` 单实例假设）。

---

## 6. 执行约定

1. **顺序：** MySQL 建表（`07_knowledge_tables.sql`：**8 张**）→ RBAC 种子 + `ai_provider` 种子 → ES index 创建 → 部署后**首次「同步文章索引」**（投影表空态）→ 服务启动自检。
2. **可重复执行：** DDL 用 `IF NOT EXISTS`；种子用 `INSERT IGNORE` + 按 id `UPDATE`；ES index 创建前先判存在（`HEAD /knowledge_chunk_v1`）。
3. **不可逆操作：** 删 ES index、全量重建、`DELETE` 类语句执行前须按 `CLAUDE.md` §7 征询；生产执行前同样征询。
4. **不引 DB 迁移框架**（`CLAUDE.md` §2.2）：SQL 随提案提供并人工执行。
5. **任务自愈：** 应用启动时把 `knowledge_task` 中遗留的 `RUNNING` 置回 `PENDING`（见 §2.6），避免进程重启后任务卡死。
6. **联调冒烟：** 建表 + 种子 + index 就绪后，用「同步文章索引 → 管理端勾选已发布文章入库 → doc/chunk READY → ES 文档数一致 → `/knowledge/ask` 返回带出处答案」做最小闭环验证（验收清单待 `api-design.md` 一并定稿）。
