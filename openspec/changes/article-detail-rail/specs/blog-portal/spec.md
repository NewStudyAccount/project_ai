# blog-portal · Delta（article-detail-rail）

> 基线：`openspec/changes/redesign-portal-pages/specs/blog-portal/spec.md`（未归档；其上游 `blog-content-domain`）。  
> 本 delta **MODIFIED**「文章详情阅读体验」（无侧栏条款改为右侧悬浮轨）并 **ADDED**「详情右侧悬浮轨」；其余需求不变。视觉 token 口径仍受 `portal-aurora-visual` 约束。

## MODIFIED Requirements

### Requirement: 文章详情阅读体验
详情页 SHALL 使用双列布局：约 760px 阅读列 + 240px 右侧悬浮轨（约 1120px 容器）：文章头（标题、作者/发布时间/分类/wordCount 元信息、标签）、正文区、底部（标签 + 返回列表）。正文区 MUST NOT 自带 Markdown 转换（对齐既有「注入服务端渲染 HTML」需求）。视口 <1200px 时右轨 MUST 隐藏，阅读列单列展示。

#### Scenario: 阅读列呈现
- **WHEN** 访客打开已发布文章详情
- **THEN** 正文在约 760px 阅读列内展示，标题与元信息层级清晰，右侧悬浮轨不侵入阅读列

#### Scenario: 详情底部
- **WHEN** 阅读至文末
- **THEN** 可见文章标签与「返回文章列表」入口

#### Scenario: 窄屏降级
- **WHEN** 视口宽度 <1200px
- **THEN** 右轨隐藏、阅读列单列，无横向滚动；面包屑/文末标签/文末返回入口仍可用

## ADDED Requirements

### Requirement: 详情右侧悬浮轨
详情页右侧 SHALL 提供三块悬浮（sticky）卡片：① 分类/标签（当前文章分类链接至 `/articles?category=`、标签 chips 链接至 `/articles?tag=`；无分类且无标签时该块优雅隐藏）；② 目录（由注入后的渲染 HTML 扫描 h2/h3 生成、层级可辨、点击平滑滚动至对应标题；正文无 h2/h3 时该块优雅收起）；③ 返回文章列表入口。标题缺锚点 id 时前端 SHALL 补锚点（不改服务端渲染输出、不重新解析 Markdown）。

#### Scenario: 目录点击跳转
- **WHEN** 正文含多个 h2/h3 标题，访客点击目录条目
- **THEN** 页面平滑滚动至对应标题，标题可见（滚动偏移避开固定头部）

#### Scenario: 目录收起
- **WHEN** 正文不含 h2/h3 标题
- **THEN** 目录块优雅收起，其余两块正常展示

#### Scenario: 分类标签入口
- **WHEN** 文章带分类与标签
- **THEN** 悬浮轨①块展示分类与标签，点击分别跳转对应筛选列表

#### Scenario: 锚点补全不破坏注入
- **WHEN** 服务端渲染 HTML 的标题无 id
- **THEN** 前端补唯一锚点 id 后跳转可用；正文 HTML 语义（除锚点 id 外）不被改写
