# portal-aurora-visual Specification

## Purpose
约束 auth-portal（登录门面）与 blog-portal（公开博客）的「极光 Aurora」品牌视觉对齐：auth-portal 按 `admin-ui-design.md` §4.5 全量换装，blog-portal 按品牌色轻量口径对齐（不套管理端壳）。**只约束视觉层**；布局/阅读体验归内容域能力、管理端视觉归 `admin-aurora-visual`，本 spec MUST NOT 被解读为改变其需求。
## Requirements
### Requirement: auth-portal 登录门面全量换装
auth-portal SHALL 采用「极光 Aurora」token 子集（取值以 `docs/template/tokens-aurora.css` 为准）：页面底 `--bg-page`（冷紫灰）、主色 `--primary`（靛紫 `#6d5df6`）、白卡片圆角 16 + 紫调阴影、主按钮 `--accent-gradient` 渐变胶囊 + `--primary-glow` 光晕；Element Plus 主色系列 SHALL 映射 token。旧云白色值（`#0284c7` 系等）MUST NOT 残留。布局 SHALL 保持 §4.5 定稿形态（居中白卡片 380px、`label-position="top"`、全系统登录页同一做法）。

#### Scenario: 登录页视觉
- **WHEN** 打开 auth-portal 登录页
- **THEN** 冷紫灰页底 + 白卡片（圆角 16、紫调阴影）+ 渐变胶囊主按钮，与管理端同一品牌语言

#### Scenario: 旧色值清底
- **WHEN** 在 auth-portal `src/` 内检索旧云白色值
- **THEN** 零命中（注释引述除外）

### Requirement: blog-portal 品牌色轻量对齐
blog-portal SHALL 完成品牌视觉对齐：主色/链接色（`#409eff` → `--primary` 靛紫）、页面底（`#f7f8fa` → `--bg-page`）、文字灰阶（`#303133`/`#606266`/`#909399` → 深靛墨系三级）、卡片圆角 16 + 紫调阴影、标签浅底（`#ecf5ff` → `--primary-light`）；全部硬编码色值 SHALL token 化（token 子集取值出自 `tokens-aurora.css`，`--bg-code` 为唯一子集扩展）。blog-portal MUST NOT 被套用管理端壳（侧栏/顶栏/胶囊按钮语言），布局与组件结构 MUST NOT 改变。

#### Scenario: 博客站品牌色切换
- **WHEN** 打开 blog-portal 任意页面（首页/列表/详情/分类/标签/关于）
- **THEN** 链接与强调色为靛紫、文字为深靛墨系、卡片圆角 16，布局与阅读体验不变

#### Scenario: 硬编码色值清底
- **WHEN** 在 blog-portal `src/` 内检索 `#409eff`/`#ecf5ff`/`#303133`/`#606266`/`#909399`/`#ebeef5`/`#f2f2f2`/`#f5f7fa` 等旧值
- **THEN** 零命中（注释引述除外），全部经 token 引用

### Requirement: 门户换装零行为变化
本基线 SHALL NOT 改变两门户的模板结构、script、路由、请求与交互；换装范围仅限样式层（`<style>` 块与 `main.css`）。两门户 token 子集取值 MUST 与 `tokens-aurora.css` 一致，MUST NOT 本地微调色值。

#### Scenario: 换装前后行为一致
- **WHEN** 完成任一门户换装
- **THEN** 页面结构、导航、请求行为与换装前逐项一致，仅视觉呈现变化

