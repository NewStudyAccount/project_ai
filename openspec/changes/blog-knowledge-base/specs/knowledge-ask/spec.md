# knowledge-ask 能力规格（RAG 流式问答）

> 字段级契约：`docs/knowledge-base-design/api-design.md` §4（SSE 帧格式、降级矩阵）。

## ADDED Requirements

### Requirement: 登录态 RAG 流式问答

`POST /api/v1/knowledge/ask` SHALL 仅对登录用户开放（网关 JWT 鉴权；路由 MUST NOT 进入 public-paths，无匿名通道）。问答链路 SHALL 为：问题向量化（Embedding 云 API）→ ES `knn(embedding) + match(content) + filter(doc_status=2)` 原生 RRF 混合检索 Top-K → 组装上下文（chunk content + ES denormalize 的 doc_title/doc_url 出处，全链路 MUST NOT 回 MySQL 取正文）→ LLM 流式生成 → SSE 返回，答案末尾附引用来源列表。`sessionId` 仅用于会话落库与限流审计，MUST NOT 作为检索的对话上下文。

#### Scenario: 正常问答带出处

- **WHEN** 登录用户提交问题且知识库有相关已入库内容
- **THEN** SSE 依次返回 `meta`（含 sessionId/mode=RAG/model）、多个 `delta`、一次 `refs`（含 chunkId/docId/docTitle/docUrl/score）、`done`（含 messageId/costMs/refCount）

#### Scenario: 无命中

- **WHEN** 检索无任何命中
- **THEN** 正常结束：`refs` 帧为空数组，答案为 LLM 模板回复「未检索到相关内容」

#### Scenario: 匿名访问拒绝

- **WHEN** 未登录用户调用问答接口
- **THEN** 返回 401，不进入 SSE

### Requirement: SSE 帧契约

响应 SHALL 为 `text/event-stream; charset=UTF-8`（含 `X-Accel-Buffering: no` 配合网关禁缓冲），帧格式 `event: <name>\n` + `data: <单行 JSON>\n\n`，每 15s 无数据发注释心跳，MUST NOT 使用 `retry` 与多行 `data`。流开始前的故障（鉴权/限流/ES 不可用/参数非法）SHALL 直接返回 JSON `Result` 不进入 SSE；流开始后的故障 SHALL 发 `event: error` 后关闭连接（前端保留已收内容）。

#### Scenario: 流开始前 ES 不可用

- **WHEN** 问答请求到达时 ES 检索不可用
- **THEN** 返回 HTTP 503 + `code=203100`，不进入 SSE 流

#### Scenario: 流中途故障

- **WHEN** SSE 已开始输出后 LLM 连接中断
- **THEN** 发送 `event: error`（含 code/msg）后关闭连接；会话落库仅 Q 行

#### Scenario: 心跳保活

- **WHEN** LLM 生成间隔超过 15 秒
- **THEN** 服务端发送注释心跳帧维持连接

### Requirement: 降级矩阵

系统 SHALL 按以下矩阵降级：LLM 云 API 不可用 → `meta.mode=KEYWORD_FALLBACK`，`delta` 输出 ES match 命中的原文片段 + 模板化说明（「AI 组织暂不可用，以下为关键词检索结果」），末尾仍发 `refs` + `done`（不算 error）；Embedding 失败（问题向量化不可用）→ 流开始前 503 + `203101`（语义检索无降级路径）；AI 线程池满 → 429 + `10003`；ES 检索不可用 → 503 + `203100`（唯一入口，无降级）。

#### Scenario: LLM 不可用降级

- **WHEN** 问答时 LLM 云 API 不可用但 ES 正常
- **THEN** 首帧 `mode=KEYWORD_FALLBACK`，输出关键词命中片段与模板化说明，正常 refs + done 结束

#### Scenario: Embedding 不可用

- **WHEN** 问题向量化调用失败
- **THEN** 流开始前返回 503 + `203101`，不产出降级质量的答案

#### Scenario: 线程池满

- **WHEN** `knowledgeAiExecutor` 队列已满时新问答到达
- **THEN** 返回 429 + `10003`（msg「服务繁忙，请稍后重试」）

### Requirement: 问答限流与会话落库

问答 SHALL `@RateLimit` 限流：5 次/分钟/用户 + 20 次/天/用户，超限 429 + `10003`（msg 区分「请求过于频繁」/「今日配额已用尽」）。每个问答 SHALL 落库：`knowledge_chat_session`（归属用户 = 审计 `create_by`）+ `knowledge_chat_message`（Q 行 + A 行，A 行含 refs JSON 与 cost_ms）；正常 done 前完成写入，error/中断时仅落 Q 行。问题长度 SHALL 校验 1–500 字（超长 `30001`）。

#### Scenario: 超出分钟限流

- **WHEN** 同一用户 1 分钟内发起第 6 次问答
- **THEN** 返回 429 + `10003`，msg 提示请求过于频繁

#### Scenario: 超出日配额

- **WHEN** 同一用户当日累计发起第 21 次问答
- **THEN** 返回 429 + `10003`，msg 提示今日配额已用尽

#### Scenario: 会话留痕

- **WHEN** 一次问答正常完成
- **THEN** 落库一条 Q 消息与一条 A 消息（含 refs、cost_ms），归属会话与用户正确
