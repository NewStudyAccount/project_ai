# blog-admin-crud-ui · 博客管理端实体页与 CrudPage 骨架

## Purpose

约束 blog-admin 实体页（分类 / 标签 / 文章 / 文件 / 存储服务商 / 审计）的页面形态与配置驱动 CrudPage 通用骨架：筛选、分页、D-Form 校验、删除保护、详情面板、首页概览。**只约束形态层**；业务字段与动作语义归在途变更能力（`blog-content-domain`、`blog-object-storage-*` 等），本 spec MUST NOT 被解读为改变其需求。

## ADDED Requirements

### Requirement: 配置驱动的 CrudPage 通用骨架
blog-admin SHALL 提供系统内唯一的 `components/common/CrudPage.vue` 与 `CrudForm.vue`，消费声明式 PageSchema 渲染 P-List 全量形态（page-card + toolbar + el-table + pagination + D-Form）。业务页面文件 SHALL 只声明差异（config + ≤ 30 行模板接线），MUST NOT 各自重写骨架；页面既有业务动作（上传、测试连通等）SHALL 经 PageSchema 的自定义 action 扩展点接线，MUST NOT 骨架外另起交互体系。骨架样式 SHALL 只用「云白」token。

#### Scenario: 业务页由 schema 驱动渲染
- **WHEN** 业务页传入合法 PageSchema（columns / filters / form / api / permissionPrefix / 自定义 actions）
- **THEN** CrudPage 渲染筛选、数据表、分页与弹窗，业务页无重复骨架代码

#### Scenario: 自定义业务动作经扩展点接线
- **WHEN** 页面存在上传、测试连通、设默认等业务动作
- **THEN** 动作经 PageSchema 的 primaryActions / rowActions 声明接线，交互（确认、toast、刷新）由骨架统一提供

#### Scenario: 无权限按钮整钮不渲染
- **WHEN** 当前用户权限集不包含某操作的权限标识（`system:resource:action`）
- **THEN** 对应按钮不渲染（非 disabled）

### Requirement: 分类与标签管理全量形态
分类管理页与标签管理页 SHALL 为 P-List 全量形态：关键词/状态筛选（变更即查、页码重置 1）、分页（`current` 从 1 起）、新建/编辑 D-Form（名称等必填字段校验失败红字、编辑回显、不暴露 id）、删除经 danger 二次确认且文案含对象名；当后端返回引用约束类业务错误时 SHALL 展示其 `msg`（如「存在文章引用」），MUST NOT 前端自行判定引用关系。

#### Scenario: 新建分类校验
- **WHEN** 用户提交时名称为空
- **THEN** 字段下方红字提示，弹窗不关闭、不发请求

#### Scenario: 删除被引用的分类
- **WHEN** 用户确认删除一个仍被文章引用的分类
- **THEN** 展示后端返回的业务错误 `msg`（HTTP 200 + 业务 code 分流），列表不变更

### Requirement: 文章列表形态收敛
文章列表 SHALL 为 P-List 全量形态：标题/分类/状态筛选、分页、状态软胶囊（草稿/发布态映射后端枚举 desc）、行内操作（编辑/发布/下线/删除等，以现有契约为准）均经二次确认或防重反馈。本要求 SHALL NOT 改变文章内容、编辑器与发布语义（归 `blog-content-domain` / `adopt-markdown-editor`）。

#### Scenario: 按标题与状态筛选
- **WHEN** 用户输入标题关键词并选择状态
- **THEN** 列表按条件刷新、页码重置 1，文章内容与编辑行为不变

#### Scenario: 危险操作确认
- **WHEN** 用户执行删除/下线类行内操作
- **THEN** 经 danger 二次确认后执行，toast ≤ 2s 并刷新当前页

### Requirement: 文件与服务商列表形态收敛
文件管理页与存储服务商页 SHALL 在**不改变业务动作与字段语义**的前提下收敛为统一形态：筛选条布局、表格、分页、D-Form 弹窗样式、状态软胶囊、二次确认与 toast 反馈走 CrudPage 骨架；SK/密钥类敏感输入保持只写掩码回显；上传、测试连通、设默认等动作的请求逻辑 MUST 保持现状（归 `blog-object-storage-*`）。

#### Scenario: 形态收敛不改行为
- **WHEN** 该两页完成形态收敛
- **THEN** 既有业务动作（上传/测试连通/设默认/删除）的请求参数与结果处理与收敛前一致，仅布局与反馈样式变化

#### Scenario: 密钥掩码回显
- **WHEN** 编辑存储服务商且后端返回掩码后的 SK
- **THEN** 表单以掩码展示，不回显明文；留空保存保持原值（现状语义不变）

### Requirement: 审计列表与详情面板
审计列表 SHALL 为审计形态 P-List：时间范围（beginTime/endTime）+ 操作人/模块/结果筛选；长文本省略 + hover 全文；行内「详情」打开 D-Panel 键值对展示，TraceId 仅当后端出参提供时展示，MUST NOT 前端自造。

#### Scenario: 时间范围筛选
- **WHEN** 用户选择起止日期并查询
- **THEN** 列表仅返回范围内记录，分页重算

#### Scenario: 详情展示
- **WHEN** 用户点击行内「详情」
- **THEN** D-Panel 键值对展示记录字段，未提供 TraceId 时不展示该项

### Requirement: 危险操作反馈与首页概览
删除/下线类操作 MUST 经二次确认；成功后 SHALL toast（≤ 2s）并刷新当前页，当前页删空自动回退一页；表单提交按钮 SHALL loading 防重。Dashboard SHALL 呈现系统说明卡与快捷入口（文章/分类/标签/文件/服务商）；统计卡片仅当现有接口可提供数据时展示，MUST NOT 虚构数据或新增统计接口，MUST NOT 引入图表库。

#### Scenario: 删除后回退页码
- **WHEN** 用户删除当前页最后一条记录
- **THEN** toast「删除成功」且列表回退上一页（若存在）

#### Scenario: 首页降级
- **WHEN** 无可用统计接口
- **THEN** 首页仅展示说明卡与快捷入口
