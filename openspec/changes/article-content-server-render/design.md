# Design: article-content-server-render

> 需求见 `specs/`；动机见 `proposal.md`。契约口径以 `CLAUDE.md` 为准，本文不复述返回体/错误码/发号等通用口径。

## Context

当前正文渲染链路：OSS 存 Markdown 源文 → `GET /api/v1/public/articles/{idOrSlug}` 把**源文**塞进 `ArticleDetailVO.content` → 浏览器里 `blog-portal/src/utils/markdown.ts`（自研 60 行极简解析器）转 HTML → `v-html`。该解析器缺图片/链接/表格/引用/有序列表/分隔线语法（图片 bug 已复现），且与 `blog-admin` 的 md-editor-v3 预览是两套渲染器（所见非所得）。上游规格：`blog-content-domain`（`blog-public-read` / `blog-portal`，未归档）、`article-content-markdown-only`（正文唯一 Markdown，未归档）。设计文档 `docs/blog-system/business-design.md` §E.5 已改写为服务端渲染口径，`api-design.md` §0/§6.4 已同步。

约束：正文 ≤500KB、OSS 只存 `text/markdown` 源文（business-design §3.3）、源文「HTML 一律视为文本」（§E.2）、依赖版本唯一取用处 `docs/version-baseline.md`（flexmark-java 0.64.8 已登记）、纯网关鉴权（本变更无新端点）。

## Goals / Non-Goals

**Goals:**

- Markdown→HTML 转换收口到服务端单点（`blog-framework` 封装），公开详情 `content` 直接返回渲染 HTML
- 语法覆盖补齐到 §E.2 允许语法全集（图片/链接/表格等），消除 admin 预览与 portal 展示的渲染器分裂
- 保持安全契约：原生 HTML 不执行、可执行协议不可达（转义 + URL 消毒固化在渲染器配置单点）

**Non-Goals:**

- 不做发布时预转存 `.html` 对象、SSR/预渲染、SEO 优化
- 不改存储契约（OSS 仍只存 Markdown 源文；无 DDL）
- 不改管理端编辑链路（读写仍 Markdown 源文；md-editor-v3 内建预览保留）
- 不引入 `flexmark-all` 全家桶、OWASP sanitizer 等额外依赖
- 不做渲染结果缓存（本期不做，见 Open Questions）

## Decisions

1. **渲染时机 = 读取时转换**（`detailPublic` 组装路径内）。
   - 备选：发布时转换并落 `.html` 对象（读取直出）——需扩展对象生命周期/清理（`ops-object-cleanup`）、双对象一致性，本期正文 ≤500KB、详情低频，收益不抵复杂度；留作后续变更。
   - 备选：前端换标准库渲染（markdown-it）——渲染器仍是第二份真相，admin/portal 分裂无解，且图片 URL 白名单/消毒口径前端各自实现，安全面散落。
2. **渲染库 = flexmark-java 0.64.8（`flexmark` 核心 + `flexmark-ext-tables`）**，版本已登记 `docs/version-baseline.md` §1，blog 父 pom `dependencyManagement` 按其落地。
   - 备选：commonmark-java——无表格扩展，§E.2 语法覆盖不全。
   - 备选：flexmark-all——全家桶 30+ 模块，违反最小依赖。
   - 备选：补全自研解析器——维护成本与 XSS 面自负，无收益。
3. **安全配置 = `escapeHtml(true)` + `sanitizeUrls(true)`，固化在 framework 渲染器单例配置**：原生 HTML 转义为文本（对齐 §E.2「一律视为文本」，不是「清洗后执行」）、`javascript:` 等协议丢弃。
   - 备选：OWASP HTML Sanitizer（白名单放行部分 HTML）——与「HTML 一律视为文本」口径相悖，且多一个依赖。
4. **落点 = `blog-framework` 渲染器封装**（Parser/HtmlRenderer 复用 + 配置单点）。
   - 备选：`blog-service/util`——业务可绕过配置另转，安全口径散落（违反「禁止业务自建横切实现」）。
   - 备选：`blog-common`——`common` 只放零配置纯基础，渲染器属需配置封装（CLAUDE.md §5.4）。
5. **契约 = 公开 `content` 语义直接改为 HTML（不新增 `contentHtml` 双字段）**，路径维持 `/api/v1`。
   - 备选：`content`（源文）+ `contentHtml` 双字段（路线 C）——过渡期双源、需二期清理；业务已裁决路线 B。
   - 备选：升 `/api/v2`——系统未对外发布、唯一消费方 `blog-portal` 同变更升级（先例：`article-content-markdown-only` 在 v1 内去 `contentFormat`）；对外发布前若需兼容再裁决 v2/双字段。
6. **前端 = 直接注入**：`blog-portal` 详情页 `v-html` 注入服务端 HTML，删除自研渲染器；不为 portal 增加任何渲染依赖。

## Risks / Trade-offs

- [XSS 责任移到服务端，配置错误即漏洞] → escapeHtml/sanitizeUrls 固化在 framework 单点默认配置，业务不可覆盖；验收含原生 HTML 转义、`javascript:` 丢弃样例（specs 场景可测）
- [`content` 字段分端语义易混（admin=源文 / public=HTML）] → `api-design.md` §0/§3.3/§6.4 明文分端；VO `@Schema` 注解标注语义
- [读取时转换增加详情接口 CPU] → 正文 ≤500KB、公开详情低频 + `@RateLimit`；Parser/Renderer 单例复用；若成热点再开「预转存」变更
- [flexmark 与 md-editor-v3 内建预览存在渲染细节差异] → 前端预览仅辅助（§E.1），对外唯一真相 = 服务端渲染；差异可接受
- [语义变更对旧消费方是 BREAKING] → 影响面已登记（proposal Impact）：唯一消费方 blog-portal 同变更升级、同批次发布；对外暴露前再裁决版本策略

## Migration Plan

1. 落地顺序：`blog-framework` 渲染器 + `blog-service` 接线 + `blog-portal` 注入改造（同一变更、同一批次发布，**禁止**只发一侧——否则一侧把 HTML 当文本或把源文裸显）
2. 验证：`cd backend/blog && mvn -q compile`；`cd frontend/blog-portal && npm run lint && npm run type-check`；冒烟 `GET /api/v1/public/articles/{idOrSlug}` 断言 `content` 含 `<img>`/`<h1>` 结构、源文字面语法消失
3. 回滚：回退部署版本即可（无 DDL、无数据迁移、OSS 对象未变）

## Open Questions

- 对外发布前是否需要 `/api/v2` 或双字段过渡（当前维持 v1，唯一消费方同步升级）
- 管理端编辑器预览是否后续统一走服务端渲染（本期保留 md-editor-v3 内建预览）
- 渲染结果是否需要缓存（Redis，key 规约 `CLAUDE.md` §6.9）；本期读取时直转
