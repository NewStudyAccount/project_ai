# Design: knowledge-doc-view-original

> 关联：`proposal.md` · 规格 `specs/` · 契约源 `docs/knowledge-base-design/api-design.md` · `table-design.md`
> 约束：`CLAUDE.md` §5.4–5.6 模块边界、§6.4.1 统一返回体、§6.4.2 错误码、§5.2 纯网关鉴权、§2.2 不引平行依赖

---

## Context

知识文档两来源的源文分布与既有取文通道（`blog-knowledge-base` 已落码）：

| 来源 | 源文位置 | 既有回源通道 | 现状「查看原文」 |
|------|----------|--------------|------------------|
| source=1 文章 | `blog_article` + live `sys_file`（MinIO） | `ArticleContentProvider.fetchArticle(sourceId)` → Markdown 源文 + slug | `docUrl=/articles/{slug}`（管理端相对路径打开 404；portal refs 可用） |
| source=2 手动 | MinIO `knowledge/{docId}/{uuid}.md` + `knowledge_file` | `doc.fileId` → `knowledge_file` → `ObjectStorageClient.getBytes(storageCode, objectKey)` | 无（`docUrl=""`） |

`VectorizeService.loadDoc()`（`VectorizeService.java:241-274`）已实现按 source 分流读源文——**取文能力已存在，缺的只是查看接口**。注意 `loadDoc` 失败分支会把 doc 置 FAILED（向量化语义），查看是纯读场景，**不可复用其副作用**，取文逻辑独立实现。

现状代码锚点（实施对照）：

| 层 | 位置 |
|----|------|
| 文档服务 | `KnowledgeDocService/Impl`（分页 `toVO` 内 `resolveDocUrl` 逐行 `fetchArticle` = N+1） |
| Controller | `KnowledgeDocController`（`/api/v1/admin/knowledge/docs`） |
| 取文参考 | `VectorizeService.loadDoc()`（source 分流，失败置 FAILED——只参考分流，不复用副作用） |
| 错误码 | `BlogErrorCodeEnum` 知识库段 203090–203099（203094/203096 空位） |
| 前端 | `blog-admin/src/views/knowledge/DocList.vue`（`openDoc(row.docUrl)` 相对路径 404）；`md-editor-v3` 既有（`MdEditor.vue`），`MdPreview` 同库自带 |

**用户裁决（2026-11-03）：** ① 查看范围只到管理端；② 抽屉为主、跳转为辅。

## Goals / Non-Goals

**Goals:**

- 两种来源统一「查看原文」：`GET /admin/knowledge/docs/{id}/content` 回 Markdown 源文 + `stale` 提示
- 管理端抽屉预览（`MdPreview` 只读）为主；source=1 抽屉内「跳转前台原文」为辅
- 修复 `KnowledgeDocVO.docUrl` 的 N+1（逐行 fetchArticle 读 MinIO 全文）与管理端 404 链接
- `stale` 可信：向量化成功时回写 `contentVersion` 为本次源文指纹

**Non-Goals:**

- 不做用户端（portal refs）source=2 查看；不做入库快照/历史版本
- 不改表结构、不改入库/检索/问答链路；不引新依赖；不做对象存储公开 URL 直出

## Decisions

| # | 决策 | 备选与否决理由 |
|---|------|----------------|
| 1 | **统一 content 接口 + 站内抽屉预览** | 备选 MinIO 公开 URL 直出否决：`.md` 是 attachment 强制下载、内部知识源文裸暴露公开桶不安全；备选后端渲染 HTML 否决：admin 已有 md-editor-v3（含 `MdPreview`），回 markdown 前端渲染更简 |
| 2 | **原文 = 当前源文**（非入库快照），`stale` 标识差异 | 入库全文未落库（仅 chunk）；重建快照需新增存储，超出「查看」需求。`stale` = source=1 时 `contentVersion`（入库指纹）≠ `liveVersion`（当前 live 指纹）；source=2 恒 false（重传即换 `fileId`，`contentVersion` 同步变） |
| 3 | **查看接口独立取文，不复用 `loadDoc`** | `loadDoc` 失败分支有「doc 置 FAILED」副作用（向量化语义）；查看是纯读，失败应报错不改状态 |
| 4 | **`KnowledgeDocVO.docUrl` 改 id 形态 `/articles/{sourceId}`** | 现状 slug 形态需逐行 `fetchArticle`（N+1，每行读一次 MinIO 全文）；`PublicController` 的 `idOrSlug` 支持 id 直查，id 形态免回源且是有效公开 URL。content 接口出参仍出 slug 形态（接口内本就取了文章，slug 免费，利于展示） |
| 5 | **`stale` 可信化：向量化成功时回写 `contentVersion`** | 不回写则文章编辑→重建后 `contentVersion` 仍是入库旧值，`stale` 永真。`VectorizeService` 成功块回写「本次源文指纹」（source=1 = 本次 `liveFileId`；source=2 = `fileId`） |
| 6 | **权限复用 `blog:knowledge:list`，纯读不写审计** | 查看属列表能力的自然延伸；审计只覆盖关键写（`CLAUDE.md` §6.4.5 口径）。`content` 接口自管取文错误码，不新增权限位、不改种子 |
| 7 | **错误码 203096 `KNOWLEDGE_DOC_CONTENT_UNAVAILABLE`** | 203090–203099 文档段内空位分配（203094 保留不动）；覆盖源文对象缺失、回源失败、文章已下架（`fetchArticle` 返回 null）三类「原文拿不到」场景 |

## Risks / Trade-offs

- [docUrl 形态变化（slug→id 链接）] → 接受：两形态均为有效公开 URL；portal refs 已 denormalize 的 slug 链路不动；管理端消费方随本变更同步
- [查看接口直读 MinIO/回源，大文件 10MB 传输] → 接受：管理端低频操作；`.md` 纯文本，抽屉渲染无压力
- [stale 仅覆盖 source=1] → 接受：source=2 重传语义即「新版本新指纹」，doc 状态（PENDING/READY）已表达重建进度
- [文章下架后原文不可看] → 接受：与 `fetchArticle` 只读契约（仅已发布）一致；doc 已有 INVALID 状态机与重建路径

## Migration Plan

无 DDL、无数据回填。部署即生效；`contentVersion` 回写修正对存量数据自然收敛（下次向量化后指纹刷新）。
