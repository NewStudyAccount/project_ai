# knowledge-admin 能力规格（知识库运维）

> 字段级契约：`docs/knowledge-base-design/api-design.md` §2/§3/§6 + `table-design.md` §4（权限种子）。

## ADDED Requirements

### Requirement: 知识文档管理

系统 SHALL 提供知识文档分页 `GET /admin/knowledge/docs`（权限 `blog:knowledge:list`；keyword/source/status 筛选，orderBy 白名单），出参含向量健康度（`readyChunkCount` / `chunkCount`）与 doc 级 `failReason`。编辑 `PUT /docs/{id}` 与删除 `DELETE /docs/{id}` SHALL 仅允许 `source=2`（手动导入）文档：重传文件触发 `content_version` 变更 → 重置 PENDING 并新建 REVECTORIZE 任务（先删旧 ES chunk 文档）；删除为逻辑删 + INVALIDATE 任务清 ES（`delete_by_query`）。对文章来源文档误操作 SHALL 返回 `203095`。

#### Scenario: 文档列表含健康度

- **WHEN** 管理员查询知识文档列表
- **THEN** 每条含 `chunkCount`、`readyChunkCount`、`status`、`embedModel`、`contentVersion`，分页结构与 id 为 string

#### Scenario: 编辑非手动导入文档

- **WHEN** 对 `source=1`（文章来源）文档调用编辑或删除
- **THEN** 返回 `203095`，文档状态不变

#### Scenario: 重传触发重建

- **WHEN** 手动导入文档重新上传新文件
- **THEN** `content_version` 变更，旧 ES chunk 文档先删，doc 重置 PENDING 并异步重建

### Requirement: AI 服务商管理与密钥安全

系统 SHALL 提供 AI 服务商 CRUD（权限 `blog:ai-provider:*`）+ 连通测试 `POST /{id}/test`：`code` 唯一（重复 `203111`）；`is_default` 按 kind 维度唯一（应用层保证）；生效默认行禁止删除（`203113`）；Embedding 行 `dim` MUST 与 ES index `dims=1024` 一致（不一致 `203114`），切换 model/dim 须走全量重建索引。`api_key` SHALL 永不回显明文（出参仅 `apiKeyMasked` 掩码；编辑时留空=不变）；密钥 MUST NOT 写入仓库。

#### Scenario: 连通测试

- **WHEN** 管理员对某服务商执行连通测试
- **THEN** 返回 `{ ok, latencyMs, message }`；失败时返回 `203112`

#### Scenario: 密钥不回显

- **WHEN** 管理员查询服务商列表
- **THEN** 出参仅含 `apiKeyMasked`（如 `sk-****abcd`），任何接口不返回明文 api_key

#### Scenario: 维度不一致拦截

- **WHEN** 新增/编辑 Embedding 服务商时 `dim` 与 ES index dims=1024 不一致
- **THEN** 返回 `203114` 并提示需重建索引

#### Scenario: 默认行保护

- **WHEN** 删除当前生效的默认服务商
- **THEN** 返回 `203113`，服务商未被删除

### Requirement: 任务观测与统计

系统 SHALL 提供：任务分页 `GET /admin/knowledge/tasks`（taskType/status/docId 筛选，权限 `blog:knowledge:manage`）与重跑入口；统计 `GET /admin/knowledge/stats`（doc 各状态分布、chunk READY 比例、`esDocCount`/`esHealthy` 取 ES `_count` + 集群健康——ES 不可用时置 null/false 且接口本身仍 200、任务失败数、provider 调用量）。管理端全部写操作 SHALL 记录操作审计（操作人/时间/动作/结果）。

#### Scenario: 统计接口在 ES 故障时可用

- **WHEN** ES 不可用时请求统计
- **THEN** `esDocCount=null`、`esHealthy=false`，接口返回 200，其余统计项正常

#### Scenario: 任务筛选查询

- **WHEN** 管理员按 `status=3`（FAILED）筛选任务
- **THEN** 仅返回 FAILED 任务（含 retryCount/failReason/startTime/endTime），供重跑决策

### Requirement: 权限与菜单接入

知识库运维 SHALL 经 blog RBAC 接入（同进程复用，无跨进程校验链路）：目录 `1004` + 菜单 `2009`（知识文档，`blog:knowledge:list`）/`2012`（文章入库，`blog:knowledge:ingest`）/`2011`（手动导入，`blog:knowledge:create`）/`2010`（AI 服务商，`blog:ai-provider:list`）+ 按钮 `90023–90031`；`ingest` 与 `create` 分权、`revectorize` 与 `reindex` 分权；种子幂等（INSERT IGNORE + 按 id UPDATE）且续号避开既有占用。无对应权限位访问 SHALL 返回 403。

#### Scenario: 分权校验

- **WHEN** 仅持有 `blog:knowledge:create` 的用户调用候选文章接口
- **THEN** 返回 403（候选接口要求 `blog:knowledge:ingest`）

#### Scenario: 种子可重复执行

- **WHEN** 种子 SQL 重复执行
- **THEN** 菜单/按钮/角色绑定无重复行、无报错（幂等语义）
