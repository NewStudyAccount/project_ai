-- blog_db · 知识库域 8 张表（blog-knowledge-base）
-- 契约唯一源：docs/knowledge-base-design/table-design.md §2（本文件逐字段照抄，不私自改列）
-- 可重复执行（IF NOT EXISTS）；执行：USE blog_db 后人工执行；生产执行前须按 CLAUDE.md §7 征询
-- ES index（knowledge_chunk_v1）不在本文件：见 deploy/es/knowledge_chunk_v1.json

USE blog_db;

-- ============================================================
-- 1) knowledge_doc 知识文档（table-design.md §2.0）
-- ============================================================
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
  fail_reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'doc 级失败原因',
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

-- ============================================================
-- 2) knowledge_chunk 知识块（业务字段真相源，无向量 BLOB；table-design.md §2.1）
-- ============================================================
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

-- ============================================================
-- 3) knowledge_file 知识对象登记（手动导入源文；table-design.md §2.2）
-- ============================================================
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

-- ============================================================
-- 4) ai_provider AI 服务商配置（table-design.md §2.3）
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_provider (
  id BIGINT NOT NULL COMMENT '16 位发号',
  code VARCHAR(32) NOT NULL COMMENT '服务商标识，系统内唯一',
  kind TINYINT NOT NULL COMMENT '1=Embedding / 2=LLM / 3=兼容',
  type VARCHAR(32) NOT NULL COMMENT 'SILICONFLOW / DASHSCOPE / ZHIPU / OPENAI',
  name VARCHAR(64) NOT NULL COMMENT '显示名',
  base_url VARCHAR(255) NOT NULL DEFAULT '' COMMENT '云 API 端点（留空由 yml 引导覆盖）',
  model VARCHAR(64) NOT NULL COMMENT '模型名',
  dim INT NOT NULL DEFAULT 0 COMMENT '向量维度（kind 含 Embedding 时必填）',
  api_key VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'dev/test 明文或引导留空；生产加密另开变更',
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

-- ============================================================
-- 5) knowledge_chat_session 问答会话（table-design.md §2.4）
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_chat_session (
  id BIGINT NOT NULL COMMENT '16 位发号',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NOT NULL DEFAULT 0 COMMENT '归属用户 = 审计 create_by（不另设 user_id）',
  update_by BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_knowledge_chat_session_user (create_by, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识问答会话';

-- ============================================================
-- 6) knowledge_chat_message 问答消息（table-design.md §2.5）
-- ============================================================
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

-- ============================================================
-- 7) knowledge_task 任务表（table-design.md §2.6）
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_task (
  id BIGINT NOT NULL COMMENT '16 位发号',
  task_type VARCHAR(32) NOT NULL COMMENT 'INGEST 入库+向量化 / REVECTORIZE 单文档重建 / INVALIDATE 下架/删除同步 / FULL_REINDEX 全量重建 / SYNC_ARTICLES 对账补偿',
  doc_id BIGINT NOT NULL DEFAULT 0 COMMENT '目标文档；全局任务（FULL_REINDEX / SYNC_ARTICLES）为 0',
  payload JSON NULL DEFAULT NULL COMMENT '任务参数（source/sourceId/action/contentVersion 等）',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 PENDING / 1 RUNNING / 2 SUCCESS / 3 FAILED / 4 DEAD（超重试上限）',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
  max_retry INT NOT NULL DEFAULT 3 COMMENT '重试上限（指数退避最多 3 次）',
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

-- ============================================================
-- 8) knowledge_article_index 薄投影表（table-design.md §2.7）
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_article_index (
  id BIGINT NOT NULL COMMENT '16 位发号',
  article_id BIGINT NOT NULL COMMENT 'blog_article.id',
  title VARCHAR(200) NOT NULL COMMENT '文章标题快照',
  category_id BIGINT NOT NULL DEFAULT 0 COMMENT '分类 id',
  category_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT '分类名快照',
  publish_time DATETIME NULL DEFAULT NULL COMMENT '最近发布时间',
  article_update_time DATETIME NULL DEFAULT NULL COMMENT '文章 update_time 快照',
  live_file_id BIGINT NOT NULL DEFAULT 0 COMMENT '当前 live 正文 sys_file.id（= blog_article_detail.content_file_id，内容指纹）',
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
