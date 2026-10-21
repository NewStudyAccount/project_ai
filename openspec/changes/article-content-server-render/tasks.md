# Tasks: article-content-server-render

> 设计文档（business-design §E.5 / api-design §0·§6.4 / version-baseline）已先行更新；本清单只含代码落地与验证。完成后逐项勾选状态位（`CLAUDE.md` §8）。

## 1. 依赖与版本基线落地

- [x] 1.1 blog 父 `pom.xml` `dependencyManagement` 按 `docs/version-baseline.md` 锁定 **flexmark-java 0.64.8**（`com.vladsch.flexmark:flexmark` + `flexmark-ext-tables`）；`blog-framework` 引入依赖（模块不写版本号，`CLAUDE.md` §2.1）
- [x] 1.2 `cd backend/blog && mvn -q compile` 通过

## 2. 服务端渲染器（blog-framework）

- [x] 2.1 新增 Markdown→HTML 渲染器封装（Parser/HtmlRenderer 实例复用；`escapeHtml(true)` + `sanitizeUrls(true)` 固化为默认配置、业务不可覆盖），包结构对齐 `CLAUDE.md` §5.6；落点对齐 business-design §8
- [x] 2.2 语法覆盖自检（business-design §E.2 全集）：图片→`<img>`、链接→`<a>`、表格→`<table>`、有序列表→`<ol>`，及标题/段落/无序列表/引用/围栏代码/行内代码/粗斜体/分隔线
- [x] 2.3 安全样例自检：`<script>`/`<div>` 等原生 HTML 转义为文本输出；`javascript:` 链接与图片 URL 不可达（specs `article-content-server-render` 场景）

## 3. 服务接线（blog-service）

- [x] 3.1 `ArticleService.detailPublic()`：读 OSS Markdown 源文后经渲染器转 HTML 再填 `ArticleDetailVO.content`；管理端详情（`GET /api/v1/admin/articles/{id}`）保持返回 Markdown 源文不动；`content-url` 仍指源文对象
- [x] 3.2 `ArticleDetailVO.content` 的 `@Schema` 注解标注分端语义（公开=渲染 HTML / 管理端=Markdown 源文），OpenAPI 注解同步（`CLAUDE.md` §6.8）
- [x] 3.3 `cd backend/blog && mvn -q compile` 通过；确认无新端点、无 DDL、无配置文件新增（渲染参数不进 `application-*.yml`，固化代码内）

## 4. 前端改造（blog-portal）

- [x] 4.1 `ArticleDetailView.vue` 改为注入服务端 HTML（`v-html`）；删除 `src/utils/markdown.ts` 自研渲染器及全部引用（specs `blog-portal` 场景「正文直接注入服务端 HTML」）
- [x] 4.2 `src/api/public.ts` `ArticleDetailVO.content` 字段语义注释改为「服务端渲染 HTML」（字段名与类型不变）；`blog-admin` **零改动**
- [x] 4.3 `cd frontend/blog-portal && npm run lint && npm run type-check` 通过

## 5. 验证与回写

- [x] 5.1 冒烟（环境就绪后）：`GET /api/v1/public/articles/{idOrSlug}` 断言 `content` 含 `<h1>`/`<img>` 等结构、无字面 `![…](…)` 语法文本；管理端详情 `content` 仍可回编辑器（源文）
- [x] 5.2 安全冒烟：对含 `<script>`、`[x](javascript:…)` 的正文，公开详情输出为转义文本、可执行 URL 不可达
- [x] 5.3 回写实现偏差到 `docs/blog-system/*`（如有），并核对渲染器落点/配置与 business-design §E.5 一致；提交说明按 `CLAUDE.md` §6.3（如 `feat(blog): 正文改为服务端渲染返回 HTML`）
- [ ] 5.4 （可选）渲染器单测：仅在引入测试框架时执行，且须按 `CLAUDE.md` §2.2/§6.12 回填口径；测试命令**不作**提交门禁
