# Tasks: knowledge-doc-view-original

> 依据 `CLAUDE.md` §8：只按本清单实施，完成一项勾一项；不擅自修改 proposal/specs/design。
> 字段级契约唯一源：`docs/knowledge-base-design/api-design.md` §2.9（新增）/§6；实施时先读对应小节再落码。
> 验证基线：`cd backend/blog && mvn -q compile`、`cd frontend/blog-admin && npm run lint && npm run type-check`（CLAUDE.md §3.2）。

## 1. 契约文档先行

- [x] 1.1 `docs/knowledge-base-design/api-design.md`：新增 §2.9「知识文档原文」（`GET /api/v1/admin/knowledge/docs/{id}/content` 字段级契约：`content`/`contentVersion`/`liveVersion`/`stale`/`docUrl`）；§2.1 `docUrl` 语义改 id 形态注记；§6 错误码表增 `203096`；变更记录加一行

## 2. 后端（blog-common / blog-knowledge-service）

- [x] 2.1 `BlogErrorCodeEnum` 增 `KNOWLEDGE_DOC_CONTENT_UNAVAILABLE(203096, "知识文档原文不可用")`（文档段 203090–203099 内）
- [x] 2.2 新建 `vo/KnowledgeDocContentVO`（id/source/sourceId/title/contentVersion/liveVersion/stale/content/docUrl；id 出参 String）
- [x] 2.3 `KnowledgeDocService(+Impl)` 增 `content(String id)`：source=1 经 `ArticleContentProvider.fetchArticle` 取 Markdown 源文 + slug（`docUrl=/articles/{slug}`，live 指纹=liveFileId）；source=2 经 `doc.fileId` → `knowledge_file` → `ObjectStorageClient.getBytes` 取 `.md` 源文（live 指纹=fileId）；失效 doc 报 `203093`，回源失败/文章下架/对象缺失报 `203096`；**纯读，不改 doc 状态**
- [x] 2.4 `KnowledgeDocController` 增 `GET /{id}/content`（`blog:knowledge:list` 权限口径，OpenAPI 注解）
- [x] 2.5 `KnowledgeDocServiceImpl.resolveDocUrl`（分页 `toVO` 用）改 id 形态 `/articles/{sourceId}`，去掉逐行 `fetchArticle`（N+1）
- [x] 2.6 `VectorizeService` 向量化成功块回写 `doc.contentVersion` = 本次源文指纹（source=1 本次 liveFileId / source=2 fileId），`stale` 判定收敛
- [x] 2.7 `cd backend/blog && mvn -q compile` 通过

## 3. 管理端（blog-admin）

- [x] 3.1 `src/api/knowledge.ts`：增 `KnowledgeDocContent` 类型与 `docContent(id)` 方法
- [x] 3.2 `src/views/knowledge/DocList.vue`：操作列增「查看原文」（两种来源）→ el-drawer + `MdPreview` 只读预览；`stale=true` 显示「源文已变更，向量索引待重建」提示；source=1 抽屉内「跳转前台原文」新窗口打开 `docUrl`
- [x] 3.3 `DocList.vue` 标题列去掉 `window.open(row.docUrl)` 相对路径直开（404 现状），链接点击改为打开抽屉
- [x] 3.4 `cd frontend/blog-admin && npm run lint && npm run type-check` 通过

## 4. 联调回归

- [ ] 4.1 冒烟：source=1 / source=2 各查一次原文（内容正确、source=1 有跳转按钮且跳到 portal 文章页、source=2 无）；stale 提示在文章编辑后可见、重建后消失
- [ ] 4.2 分页列表无逐行回源（N+1 消除）；docUrl 为 id 形态且可打开
- [ ] 4.3 失效 doc / 文章下架 / 对象缺失场景返回对应错误码且 doc 状态不变
