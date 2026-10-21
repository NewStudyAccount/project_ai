# blog-portal · Delta（redesign-portal-pages）

> 基线：`openspec/changes/blog-content-domain/specs/blog-portal/spec.md`（未归档）。  
> 本 delta 以 **ADDED** 追加「页面布局与阅读体验」需求（布局/阅读体验归内容域能力，`portal-aurora-visual` Purpose 划界）；既有「信息架构与只读边界 / 详情寻址 / 站点文案」需求**不变**。视觉 token 口径仍受 `portal-aurora-visual` 约束。

## ADDED Requirements

### Requirement: 全局页面壳与响应式
blog-portal SHALL 采用统一页壳：站点头部（白底、高 60px）含品牌名（site.title）、五导航（首页/文章/分类/标签/关于，当前页高亮）与标题搜索入口（keyword → `/articles`）；页脚展示 site.copyright。布局 SHALL 响应式：≥1200px 双栏；<1200px 侧栏下沉主列下方；<768px 文章卡纵排、hero 收敛。全部色值 SHALL 经 token 引用或 token 派生（`portal-aurora-visual` 口径，不新增色值）。

#### Scenario: 导航高亮与搜索跳转
- **WHEN** 访客在任意页点击导航或提交搜索
- **THEN** 当前页导航高亮；搜索跳转 `/articles?keyword=…` 并触发标题筛选

#### Scenario: 窄屏可用
- **WHEN** 视口宽度 <768px
- **THEN** 无横向滚动，文章卡纵排，正文与导航可读可用

### Requirement: 首页布局
首页 SHALL 由四区组成：hero（site.title + site.description + 主按钮「开始阅读」）、置顶精选区、最新文章列表、侧栏（分类树含计数、标签云、站点简介）。数据 SHALL 仅来自既有公开接口（articles / categories / tags / site-config）。

#### Scenario: 置顶精选区
- **WHEN** 存在 isTop=1 的已发布文章
- **THEN** 以精选卡呈现（含封面位与「置顶」徽标，主卡 + 副卡布局）；无置顶文时该区优雅收起不占位

#### Scenario: 侧栏内容
- **WHEN** 首页加载完成
- **THEN** 侧栏分类展示 articleCount、标签云按 articleCount 权重展示、站点简介来自 site-config

### Requirement: 文章列表布局
列表页 SHALL 展示：筛选状态 chips（分类/标签/keyword，可单独清除与全部清除）、文章卡流（封面缩略、标题、摘要、元信息、标签）、分页器（current/size）、「共 N 篇」计数（取 `PageResult.total`）；筛选参数沿用 `?category=&tag=&keyword=`。空结果 SHALL 展示空态（含清除筛选入口）。

#### Scenario: 筛选状态可视
- **WHEN** 以 `?category=…&keyword=…` 访问列表
- **THEN** 顶部 chips 分别展示各筛选项，单独清除后即时刷新结果

#### Scenario: 空态
- **WHEN** 筛选结果为空
- **THEN** 展示空态提示与清除筛选入口，不出现空白卡片区

#### Scenario: 分页
- **WHEN** 总数超过单页条数
- **THEN** 展示分页器并可翻页，计数与分页行为对应 `GET /public/articles` 的 current/size/total

### Requirement: 文章详情阅读体验
详情页 SHALL 使用居中阅读列（约 760px，无侧栏）：文章头（标题、作者/发布时间/分类/wordCount 元信息、标签）、正文区、底部（标签 + 返回列表）。正文区 MUST NOT 自带 Markdown 转换（对齐既有「注入服务端渲染 HTML」需求）。

#### Scenario: 阅读列呈现
- **WHEN** 访客打开已发布文章详情
- **THEN** 正文在约 760px 阅读列内展示，标题与元信息层级清晰，页面无侧栏干扰

#### Scenario: 详情底部
- **WHEN** 阅读至文末
- **THEN** 可见文章标签与「返回文章列表」入口

### Requirement: 正文排版体系
正文区 `.markdown` 排版 SHALL 覆盖服务端渲染 HTML 元素全集：h1–h6、段落、链接、图片、有序/无序列表、引用、行内代码、围栏代码块、分隔线、表格、粗体/斜体。正文行高 SHALL ≥1.8；图片 SHALL 限宽且圆角；引用 SHALL 有强调底色与侧条；代码块 SHALL 深底展示；表格 SHALL 表头强调并带斑马纹。

#### Scenario: 全元素排版
- **WHEN** 正文含标题/图片/引用/代码块/表格（如 slug 测试文）
- **THEN** 各元素按排版体系呈现，层级、间距、行高一致且互不破版

#### Scenario: 代码块语言标签
- **WHEN** 服务端渲染 HTML 的代码块带 `language-*` class
- **THEN** 代码块角部展示对应语言标签；无语言标注时角标优雅省略

### Requirement: 封面图渲染
文章卡与精选卡 SHALL 渲染封面：`coverFileId>0` 时经 `GET /api/v1/public/files/{id}` 取图并以缩略（圆角、限宽）展示；`coverFileId=0` 或图片加载失败 SHALL 展示 CSS 渐变占位。封面加载 MUST NOT 阻塞标题/摘要文本渲染。

#### Scenario: 有封面
- **WHEN** 文章 coverFileId>0
- **THEN** 卡片展示对应封面图

#### Scenario: 无封面与加载失败降级
- **WHEN** coverFileId=0 或封面请求失败/超时
- **THEN** 展示渐变占位，无破图图标、布局不错乱

### Requirement: 分类与标签页布局
分类页 SHALL 以网格卡展示启用分类（最多 2 级：根分类卡含名称/计数，子分类以 chips 内嵌）；标签页 SHALL 以标签云展示（视觉权重随 articleCount，附计数）。计数口径与 `GET /public/categories`、`GET /public/tags` 一致。

#### Scenario: 分类网格
- **WHEN** 访客打开 `/categories`
- **THEN** 分类以卡片网格展示，子分类内嵌 chips，计数可见

#### Scenario: 标签权重
- **WHEN** 访客打开 `/tags`
- **THEN** articleCount 越大的标签视觉权重越高，计数可见

### Requirement: 关于页布局
关于页 SHALL 以居中阅读卡展示 site.about；site.about 缺失时 SHALL 优雅降级（空态文案，不阻塞页面），并展示 site.title / site.copyright。

#### Scenario: 关于文案展示
- **WHEN** site.about 存在
- **THEN** 居中阅读卡内展示文案

#### Scenario: 关于文案缺失
- **WHEN** site.about 为空
- **THEN** 页面正常展示空态文案，不报错不空白
