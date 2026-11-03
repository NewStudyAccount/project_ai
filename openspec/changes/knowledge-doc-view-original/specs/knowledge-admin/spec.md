# knowledge-admin · Delta（knowledge-doc-view-original）

> 基线：`openspec/changes/blog-knowledge-base/specs/knowledge-admin/spec.md`（未归档）。
> 本 delta 仅修正知识文档分页出参与列表交互，任务/统计/重建能力不动。

## MODIFIED Requirements

### Requirement: 知识文档分页

`GET /api/v1/admin/knowledge/docs` 分页出参中 `docUrl` SHALL 出 **id 形态**公开链接（source=1 为 `/articles/{sourceId}`；source=2 为 `""`），MUST NOT 为取 slug 而逐行回源（去 N+1）。

#### Scenario: 分页不触发逐行回源

- **WHEN** 管理员分页查询知识文档
- **THEN** 出参 `docUrl` 为 id 形态链接，服务端不因列表渲染逐行拉取文章正文
