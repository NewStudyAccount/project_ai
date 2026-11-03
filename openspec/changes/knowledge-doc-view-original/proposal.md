# Proposal: knowledge-doc-view-original

> 状态：提案
> 关联设计：`docs/knowledge-base-design/api-design.md`（接口/错误码）· `table-design.md`（库表）
> 上游变更：`blog-knowledge-base`（未归档；知识库域已落码，本变更补「查看原文」能力，不改入库/检索/问答链路）

---

## Why

知识文档（`knowledge_doc`）有两块来源：**source=1 博客文章同步**（源文在 `blog_article` + live `sys_file`）与 **source=2 手动上传 .md**（源文在 MinIO `knowledge/{docId}/{uuid}.md` + `knowledge_file` 登记）。当前只有 source=1 有公开链接（`docUrl=/articles/{slug}`），source=2 完全无法查看原文（`docUrl=""`，管理端仅显示「站内知识」标签）；且管理端的 docUrl 链接是相对路径，`window.open` 打到 admin 自己的域名，**实际点开是 404**（admin router 无 `/articles/:slug` 路由）——现状是「文章看不了、上传的也看不了」。

`VectorizeService.loadDoc()` 已实现「按 source 分流回源读 markdown」的取文逻辑，只是没有暴露成查看接口。本变更把该能力开成管理端「查看原文」，两种来源统一入口。

## What Changes

- **新增只读接口** `GET /api/v1/admin/knowledge/docs/{id}/content`（权限 `blog:knowledge:list`，纯读不写审计）：
  - 按 source 分流回源：source=1 经 `ArticleContentProvider.fetchArticle`（同进程只读契约）取 live markdown；source=2 经 `knowledge_file`（`doc.fileId`）→ `ObjectStorageClient.getBytes` 取 `.md` 源文
  - 出参 `KnowledgeDocContentVO`：`{ id, source, sourceId, title, contentVersion, liveVersion, stale, content, docUrl }`；`content` 为 **Markdown 源文**；`stale=true` 表示源文已变更、向量索引待重建（source=1 语义：入库指纹 `contentVersion` ≠ 当前 live 指纹 `liveVersion`）
  - `docUrl` 出 **slug 形态**公开链接（source=1，`/articles/{slug}`；接口内本就要取文章，slug 免费）；source=2 为 `""`
  - 错误：doc 不存在 `203090`；doc 已失效 `203093`；源文缺失/回源失败/文章已下架 `203096`（新增 `KNOWLEDGE_DOC_CONTENT_UNAVAILABLE`）
- **管理端（blog-admin）「查看原文」**：`DocList.vue` 操作列加「查看原文」（两种来源都有）→ el-drawer 抽屉内 **md-editor-v3 `MdPreview` 只读预览**（抽屉为主）；source=1 时抽屉底部提供「跳转前台原文」按钮（跳转为辅，新开 portal 文章页）
- **修复管理端 docUrl 链接现状**：`KnowledgeDocVO.docUrl` 不再逐行 `fetchArticle`（现为 N+1，每行读一次 MinIO 全文），改出 **id 形态公开链接** `/articles/{sourceId}`（`PublicController` 的 `idOrSlug` 天然支持 id）；前端标题列不再直接 `window.open(docUrl)`，改为打开抽屉
- **`stale` 可信化**：`VectorizeService` 向量化成功时回写 `doc.contentVersion` 为**本次源文指纹**（source=1 = 本次读到的 `liveFileId`）——否则文章编辑后重建，`contentVersion` 停留在入库时旧值，`stale` 永远为 true
- **契约文档**：`docs/knowledge-base-design/api-design.md` 增 §2.9（content 接口字段级契约）、§6 错误码表增 `203096`、`KnowledgeDocVO.docUrl` 语义注记、变更记录一行

### Non-goals（项目边界，对齐 `CLAUDE.md` §1）

- **不做用户端查看**：portal 问答引用出处（refs）的 source=2 展示保持现状（「站内知识」不可点开）；不新增登录用户可访问的 content 接口——查看范围只到管理端（用户裁决）
- 不做入库快照版本（原文以**当前源文**为准，`stale` 提示差异；不存历史正文）
- 不改入库/切块/向量化/ES/RAG 问答链路语义；不动 `knowledge_doc` 表结构（无 DDL）
- 不引入新前端依赖（`md-editor-v3` 既有）；不改 portal、auth、user 系统
- 不做 MinIO 对象公开 URL 直出（内容经接口返回，不暴露对象存储地址）

## Capabilities

### New Capabilities

- `knowledge-doc-view-original`：管理端知识文档原文查看——统一 content 接口（两种来源回源 Markdown 源文 + stale 提示）、管理端抽屉预览（主）+ 跳转前台原文（辅，仅 source=1）

### Modified Capabilities

> `openspec/specs/` 尚无已归档同名能力；下列为对未归档变更 `blog-knowledge-base` 中相关出参的**修正**，实施时以本变更 specs 为准。

- `knowledge-admin`（见 `blog-knowledge-base`）：`KnowledgeDocVO.docUrl` 改 id 形态公开链接（去 N+1）；文档列表增「查看原文」入口

## Impact

| 面 | 影响 |
|----|------|
| **API** | 新增 `GET /api/v1/admin/knowledge/docs/{id}/content`；`KnowledgeDocVO.docUrl` 形态由 slug 链接改 id 链接（仍为有效公开 URL，idOrSlug 兼容） |
| **DB** | 无表结构变更；`knowledge_doc.contentVersion` 回写时机修正（向量化成功时刷新，数据值域不变） |
| **后端** | `blog-knowledge-service`：`KnowledgeDocContentVO`、`KnowledgeDocService(+Impl)`、`KnowledgeDocController`、`VectorizeService` 成功块一行回写；`blog-common`：`BlogErrorCodeEnum` 增 `203096` |
| **前端** | `blog-admin`：`api/knowledge.ts` 增类型与方法；`views/knowledge/DocList.vue` 抽屉预览 + 跳转按钮 + 标题列交互修正 |
| **文档** | `docs/knowledge-base-design/api-design.md` §2.9/§6/变更记录 |
| **系统边界** | 仅 **blog** 系统内（blog-knowledge-service 读 blog-service 只读契约，既有通道）；不跨系统、不改网关/认证/权限位（复用 `blog:knowledge:list`） |

**兼容说明：** `docUrl` 形态变化影响所有消费方（管理端列表）；portal 问答 refs 中已 denormalize 的 ES `docUrl`（slug 形态）不动，两形态均为有效公开 URL，仅展示形态不同。
