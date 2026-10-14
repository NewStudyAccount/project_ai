## Purpose

管理博客内容分类（树，最多 2 级）与标签（扁平）的创建、查询、更新、停用与受约束删除，支撑文章归类与筛选。

## ADDED Requirements

### Requirement: 分类树查询
系统 SHALL 提供管理端分类查询（`GET /api/v1/admin/categories`，权限 `blog:category:list`）：支持按 `status`、`name` 过滤，返回最多 2 级的树结构（含 `id` String、`parentId`、`name`、`slug`、`description`、`sort`、`status`，可选 `articleCount`、`children`）。

#### Scenario: 查询全部分类树
- **WHEN** 管理员请求分类列表且不带筛选
- **THEN** 返回 `deleted=0` 的分类组装的树（≤2 级），`id` 为 String

#### Scenario: 按名称与状态筛选
- **WHEN** 管理员传入 `name` 或 `status` 筛选
- **THEN** 仅返回匹配节点，并仍保持树形结构

### Requirement: 创建分类
系统 SHALL 支持创建分类（`POST /api/v1/admin/categories`，权限 `blog:category:create`，关键写 `@Idempotent`）：入参 `name*`、`slug*`（全局唯一）、`parentId?`（默认 0）、`description?`、`sort?`、`status?`；应用层校验父节点存在且层级 ≤2、同级 `name` 不重复、`slug` 唯一；成功写审计（`target_type=CATEGORY`）。

#### Scenario: 创建子分类成功
- **WHEN** 在合法父分类下提交唯一 `slug` 与同级不重复 `name`
- **THEN** 返回新建 CategoryVO，审计记录创建动作

#### Scenario: 超过两级
- **WHEN** 将新分类挂到已是第 2 级的节点下
- **THEN** 返回业务/校验段错误码，不落库

#### Scenario: slug 冲突
- **WHEN** 提交已存在的 `slug`
- **THEN** 返回 slug 冲突错误语义，不落库

### Requirement: 更新分类
系统 SHALL 支持更新分类（`PUT /api/v1/admin/categories/{id}`，权限 `blog:category:update`，关键写 `@Idempotent`）：不得挂到自己或子孙下；层级仍 ≤2；`name`/`slug` 冲突校验；成功记审计。

#### Scenario: 更新名称与排序
- **WHEN** 修改合法字段且无冲突
- **THEN** 更新成功并记审计

#### Scenario: 非法父节点
- **WHEN** 将节点父级改为自身或其子孙
- **THEN** 返回明确错误码，数据不变

### Requirement: 删除分类
系统 SHALL 支持逻辑删除分类（`DELETE /api/v1/admin/categories/{id}`，权限 `blog:category:delete`，关键写 `@Idempotent`）：存在子分类或文章引用时返回 `CATEGORY_IN_USE`；否则 `deleted=1` 并记审计。

#### Scenario: 存在子分类不可删
- **WHEN** 删除仍有子分类的节点
- **THEN** 返回 `CATEGORY_IN_USE`，不删除

#### Scenario: 无子且无文章可删
- **WHEN** 删除叶子且无 `blog_article.category_id` 引用的分类
- **THEN** 逻辑删除成功并记审计

### Requirement: 标签管理
系统 SHALL 提供标签分页查询（`GET /api/v1/admin/tags`，`blog:tag:list`）、创建（`blog:tag:create`）、更新（`blog:tag:update`）、删除（`blog:tag:delete`）：`name` 与 `slug` 全局唯一；列表可含引用数；删除须无 `blog_article_tag` 关联，否则 `TAG_IN_USE`；关键写 `@Idempotent` + 审计。

#### Scenario: 创建标签
- **WHEN** 提交不重复的 `name`/`slug`
- **THEN** 创建成功并记审计

#### Scenario: 被引用标签不可删
- **WHEN** 删除仍被文章关联的标签
- **THEN** 返回 `TAG_IN_USE`，标签仍存在

#### Scenario: 停用标签
- **WHEN** 将标签 `status` 置 0
- **THEN** 管理端仍可查；用户端不展示（见 `blog-public-read`）
