# Design: adopt-markdown-editor · 技术选型

> 约束：Vue3.5 + TS + Element Plus；`CLAUDE.md` §2 不叠平行 UI 库；版本先入 `docs/version-baseline.md`。

---

## Context

写正文页需成熟 Markdown 编辑器。当前为自研 textarea + 插入按钮 + `renderMarkdown` 预览（`article-content-markdown-only`）。项目中文向、Element Plus 管理端、保存 **Markdown 源文**（非 HTML）。

---

## Goals / Non-Goals

**Goals：** 选定唯一编辑器；固定接入方式；保持源文/双轨/500KB 契约；可替换自研编辑区。

**Non-Goals：** 协同编辑；多编辑器并存；portal 写作；后端格式字段；第二套 UI 框架。

---

## 选型对比（2026-10）

| 维度 | **md-editor-v3** | vditor | @kangc/v-md-editor | ByteMD | Toast UI Editor |
|------|------------------|--------|--------------------|--------|-----------------|
| Vue3 原生 | **是** | 中（有 Vue 包装/直接用） | Vue2 史为主，Vue3 需确认 | 偏框架无关/React 史 | 需自封装 Vue |
| TS 类型 | 较好 | 一般 | 一般 | 好 | 一般 |
| 工具栏/预览/目录 | **完整** | 完整（WYSIWYG/IR/分屏） | 完整 | 完整 | 完整 |
| 与 Element Plus | **不冲突（内容组件）** | 不冲突 | 可能带主题样式 | 不冲突 | 需统一主题 |
| 包体积 | 中 | 偏大（多编辑模式） | 中 | 中 | 中 |
| 中文文档/社区 | **好** | 好 | 中 | 中 | 英文为主 |
| 源文可控 | 可取 Markdown 源 | 可 | 可 | 可 | 可 |
| 维护活跃 | 活跃 | 活跃 | 一般 | 一般 | 一般 |
| 与「源码+预览、存源文」 | **贴合（可关所见即所得）** | 多模式需收窄配置 | 贴合 | 贴合 | 需配置 |

### 裁决

| 项 | 结论 |
|----|------|
| **选定** | **`md-editor-v3`**（Vue3 专用 Markdown 编辑器，MIT） |
| 理由 | 与 Vue3/TS/组合式 API 契合；工具栏与预览完整；中文文档；**单内容组件**、不构成第二套 UI 库；易「只编辑/预览、保存源文」 |
| 备选 | **vditor**（若强需求所见即所得再评估）；**ByteMD**（包体/React 史优先级低） |
| 否决 | `@kangc/v-md-editor`（Vue3 支持与维护不确定）；Toast UI（Vue 集成成本高）；继续自研（不满足「成熟编辑器」） |

**模式配置（产品默认）：** **分屏 / 编辑+预览**；**关闭**所见即所得为默认提交形态风险面；保存 `modelValue` 为 **Markdown 字符串**。

**能力面收紧（接入封装必须做）：**

1. 关闭编辑器内「上传到服务器」或改为走既有 `/api/v1/files` 后回填 URL（**默认关闭**，图片用手写 `![](url)` 或后续变更）
2. 粘贴 HTML → 降为纯文本 / Markdown（禁结构 HTML 入源文）
3. 预览 XSS：编辑器预览用其安全渲染；**禁止**把预览 HTML 存库
4. 外链 `javascript:` 过滤（与 `article-markdown-only` 一致）
5. 字数/500KB：外层页面继续显示并门禁保存

**落点：**

```
frontend/blog-admin
  package.json          + md-editor-v3（版本见 version-baseline）
  src/components/markdown/MdEditor.vue   # 薄封装：v-model 源文、theme、禁用上传
  src/views/content/ArticleContentEditor.vue  # 用 MdEditor 替换自研工具栏
docs/version-baseline.md   # 前端依赖表登记 md-editor-v3
docs/blog-system/business-design.md §E  # 编辑器实现改为「md-editor-v3（封装）」
```

---

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| [包体积] | 仅 blog-admin 写正文路由引入；可动态 `import()` |
| [样式与 EP 冲突] | 薄封装隔离 class；必要时 `md-editor` 主题变量 |
| [能力面过宽] | 封装关闭上传/不暴露危险工具条 |
| [预览与 portal 不一致] | 可接受（编辑器预览仅辅助）；保存源文后 portal 仍 `renderMarkdown` |
| [版本漂移] | 版本只登记 `version-baseline.md`，lock 跟随 |

---

## Migration Plan

1. 改 `version-baseline.md`（先）→ `blog-admin` 装包  
2. 实现 `MdEditor.vue` 薄封装  
3. 替换写正文页编辑区；回归保存草稿/发布  
4. 回滚：删依赖与封装，恢复 textarea 实现（git revert）

---

## Open Questions

- 图片工具条是否第一期开放「填 URL 插入」（默认开）或整段关闭（更严）。
- 是否同步给 blog-portal **不**引入（已定：不引入）。
