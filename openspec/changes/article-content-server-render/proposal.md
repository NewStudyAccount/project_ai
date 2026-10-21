# Proposal: article-content-server-render

> 状态：提案  
> 关联设计：`docs/blog-system/business-design.md` §3.3 读路径 / **§E.5 渲染边界（已按本变更改写）**；`docs/blog-system/api-design.md` §0 / §6.4；`docs/version-baseline.md`（flexmark-java 0.64.8 已登记）  
> 上游变更：`blog-content-domain`（内容域，尚未归档）、`article-content-markdown-only`（正文唯一 Markdown，尚未归档）；本变更**只调整渲染职责归属与公开详情 `content` 语义**，不动正文存储格式与业务域模型

---

## Why

正文 Markdown→HTML 转换当前在**浏览器**里由 `blog-portal` 自研的 60 行极简解析器（`src/utils/markdown.ts`）完成，只支持标题/粗斜体/行内代码/围栏代码/无序列表/段落——图片 `![alt](url)`、链接、表格、引用、有序列表、分隔线全部缺失，文章正文图片显示为裸文本（缺陷已复现：`![...](...)` 被原样输出为 `<p>` 文本，永不生成 `<img>`）。同时管理端预览（md-editor-v3）与用户端渲染是**两套渲染器**，编辑器所见非门户所得。业务已裁决（路线 B）：**渲染职责移到服务端**，标准库统一转换，公开详情直接返回 HTML，前端只注入。

## What Changes

- **BREAKING（接口语义）**：`GET /api/v1/public/articles/{idOrSlug}` 出参 `content` 由「Markdown 源文」改为「**服务端渲染后的 HTML**」；`GET …/content-url` 仍指向 Markdown 源文对象（导出/留档用），常规阅读走详情渲染 HTML
- **新增（服务端渲染）**：`blog-framework` 封装 Markdown→HTML 渲染器（**flexmark-java 0.64.8**：`flexmark` + `flexmark-ext-tables`，版本已登记 `docs/version-baseline.md`）；配置 **escapeHtml**（源文原生 HTML 一律转义为文本）+ **sanitizeUrls**（`javascript:` 等可执行协议丢弃），维持「HTML 一律视为文本」安全契约（business-design §E.2）
- **语法覆盖补齐**：标题/段落/有序·无序列表/引用/围栏代码/行内代码/链接/**图片**/粗斜体/分隔线/表格（对齐 business-design §E.2 允许语法全集）
- **前端**：`blog-portal` **移除**自研 `utils/markdown.ts` 渲染逻辑，详情页改为注入服务端返回的 HTML（`v-html`）；类型 `ArticleDetailVO.content` 语义改为 HTML（字段名不变）
- **不变**：管理端读写 `content` 仍为 Markdown 源文（`ArticleAdminDetailVO`，编辑回写）；OSS 存储仍只存 **Markdown 源文**（`text/markdown`、`.md` 对象）；草稿双轨/状态机/权限码/`sys_file` 登记/500KB 限制全部不动
- **文档**：`docs/blog-system/business-design.md`（§E.5 改写为服务端渲染）、`api-design.md`（§0/§6.4 出参语义）、`docs/version-baseline.md`（flexmark 登记）**已先行更新**；本变更落地代码并回写实现偏差

### Non-goals（项目边界，对齐 `CLAUDE.md` §1 / §2.2）

- **不做**发布时预转存 `.html` 对象、SSR/预渲染、SEO 优化（接口返回 HTML 对 SPA SEO 无增益，另议）
- **不改**存储契约：OSS 不落 HTML 对象、不加格式列、不动 `sys_file` 语义
- **不做**管理端编辑器预览切换为服务端渲染（md-editor-v3 内建预览保留，仅辅助）
- **不引入**所见即所得编辑器、平行 UI/框架选型；不引入 `flexmark-all` 全家桶（仅核心 + 表格扩展）
- **不改**草稿双轨（draft/live）、发布/下架状态机、权限码、幂等/限流横切口径
- **不把**服务清单/表结构/依赖清单细节写入 `CLAUDE.md`（版本登记走 `docs/version-baseline.md`）

## Capabilities

### New Capabilities

- `article-content-server-render`: 正文服务端渲染——Markdown→HTML 转换（flexmark，读取时转换）、转义（原生 HTML 视为文本）与 URL 消毒（禁可执行协议）、§E.2 全语法覆盖、渲染器单一封装落点、公开详情 `content`=HTML 与管理端源文的字段语义分端

### Modified Capabilities

> `openspec/specs/` 尚无已归档同名能力；下列为对未归档变更 `blog-content-domain` 中规格的需求修订，实施时以本变更 specs 为准（仅文档与 tasks 状态位，不改其 proposal/design 既有裁决框架）。

- `blog-public-read`（见 `blog-content-domain`）：公开文章详情 `content` 由「线上 Markdown 源文」改为「服务端渲染 HTML」（忽略草稿轨不变）
- `blog-portal`（见 `blog-content-domain`）：详情页由「Markdown 渲染」改为「注入服务端渲染 HTML」，移除自研渲染器

## Impact

| 面 | 影响 |
|----|------|
| **API** | `GET /api/v1/public/articles/{idOrSlug}` 出参 `content` 语义变更（**BREAKING**，路径维持 `/api/v1`：唯一消费方 `blog-portal` 同变更升级、系统未对外发布，先例同 `article-content-markdown-only` 去 `contentFormat`；若后续对外暴露需再裁决 `/api/v2` 或双字段过渡）；OpenAPI 注解同步 |
| **后端** | `blog-framework` 新增渲染器封装（Parser/HtmlRenderer 单例 + escapeHtml/sanitizeUrls 配置单点）；`ArticleService.detailPublic()` 接线转换；`blog-common` 无改动 |
| **依赖** | `com.vladsch.flexmark:flexmark` + `flexmark-ext-tables` **0.64.8**（Java 11+，JDK 21 兼容；已登记 `docs/version-baseline.md` §1，父 pom `dependencyManagement` 按其落地） |
| **前端** | `blog-portal`：`utils/markdown.ts` 移除、`ArticleDetailView.vue` 注入 HTML；`blog-admin` **零改动**（编辑仍 Markdown 源文） |
| **存储/DB** | **零改动**（不落 HTML 对象、无 DDL） |
| **跨系统** | 无（仅 blog 系统内部；其他系统不消费文章正文接口） |
