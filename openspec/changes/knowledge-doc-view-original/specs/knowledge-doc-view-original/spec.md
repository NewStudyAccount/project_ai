# knowledge-doc-view-original · 知识文档原文查看

> 管理端查看知识文档原文的接口与语义契约；字段级契约落 `docs/knowledge-base-design/api-design.md` §2.9。

## ADDED Requirements

### Requirement: 原文内容接口（统一两种来源）

系统 SHALL 提供 `GET /api/v1/admin/knowledge/docs/{id}/content`（权限 `blog:knowledge:list`）：按来源回源返回 **Markdown 源文**——source=1 经文章只读契约取 live 正文，source=2 经 `knowledge_file`（`doc.fileId`）读 MinIO 源文对象；出参含 `contentVersion`（入库指纹）、`liveVersion`（当前源文指纹）、`stale`、`docUrl`。查看 MUST 为纯读：失败只返回错误码，MUST NOT 修改 doc 状态。

#### Scenario: 查看文章来源原文

- **WHEN** 管理员对 source=1 的知识文档调用 content 接口
- **THEN** 返回文章 live 正文 Markdown 源文，`docUrl` 为 `/articles/{slug}` 形态公开链接；`stale=true` 当且仅当入库指纹 ≠ 当前 live 指纹

#### Scenario: 查看手动导入原文

- **WHEN** 管理员对 source=2 的知识文档调用 content 接口
- **THEN** 返回 `.md` 源文内容，`docUrl` 为 `""`，`stale=false`

#### Scenario: 原文不可用

- **WHEN** 文档已失效（INVALID）、文章已下架/删除、或源文对象缺失/读取失败
- **THEN** 返回业务错误（失效 `203093`；其余 `203096` KNOWLEDGE_DOC_CONTENT_UNAVAILABLE），doc 状态不被修改

### Requirement: 入库指纹随向量化刷新

系统 SHALL 在向量化成功时将 `knowledge_doc.contentVersion` 回写为**本次源文指纹**（source=1 = 本次读取的 live 正文 `sys_file.id`；source=2 = `knowledge_file.id`），保证 `stale` 判定基于最近一次成功入库内容。

#### Scenario: 文章编辑后重建指纹收敛

- **WHEN** 文章正文变更后触发重建且向量化成功
- **THEN** `contentVersion` 更新为新 live 指纹，再次查看原文时 `stale=false`

### Requirement: 管理端查看原文入口（抽屉为主、跳转为辅）

`blog-admin` 知识文档列表 SHALL 为每行（两种来源）提供「查看原文」操作：抽屉内只读 Markdown 预览（`MdPreview`）；source=1 时抽屉内另提供「跳转前台原文」新窗口打开 `docUrl`。列表标题列 MUST NOT 以相对路径直接 `window.open(docUrl)`（现状 404）。

#### Scenario: 打开查看原文抽屉

- **WHEN** 管理员点击某行「查看原文」
- **THEN** 抽屉展示该文档 Markdown 渲染预览；source=1 显示「跳转前台原文」按钮，source=2 不显示

#### Scenario: stale 提示

- **WHEN** 查看的文档 `stale=true`
- **THEN** 抽屉内显示「源文已变更，向量索引待重建」类提示
