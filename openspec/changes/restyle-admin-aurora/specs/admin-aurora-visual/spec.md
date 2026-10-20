# admin-aurora-visual · 三管理端极光 Aurora 视觉基线

## Purpose

约束 auth-admin / user-admin / blog-admin 三管理端的视觉基线：「极光 Aurora」token 体系（色板/圆角/阴影/间距）、应用壳（深靛紫渐变侧栏/光晕/玻璃激活/顶栏）、组件视觉约定（渐变主按钮/状态软胶囊/统计卡渐变/弹窗阴影）。**只约束视觉层**；页面形态归 `admin-*-crud-ui` 系能力、业务语义归各业务域能力，本 spec MUST NOT 被解读为改变其需求。

## ADDED Requirements

### Requirement: 统一的极光 Aurora token 体系
三管理端 SHALL 以 `docs/template/admin-ui-design.md` §1 / `docs/template/tokens-aurora.css` 为唯一 token 来源：主色 `#6d5df6`（靛紫）、内容区冷紫灰亮底、紫调分层阴影、圆角 16/10/999、间距 20/10；Element Plus 主题 SHALL 经 `--el-color-primary` 系列覆盖对齐。旧「云白」色值（`#0284c7` 系等）MUST NOT 在三端 `src/` 残留；各系统 MUST NOT 私自换主色或新增 token 体系外色值。

#### Scenario: token 换装来源唯一
- **WHEN** 任一管理端渲染页面
- **THEN** 全部色/圆角/阴影/间距取自 token 变量，`src/` 内 grep 旧云白色值零命中

#### Scenario: Element Plus 主题对齐
- **WHEN** 渲染 Element Plus 组件（按钮/输入/分页/弹窗）
- **THEN** 主色系列为靛紫 `#6d5df6` 系（light-3/5/7/8/9、dark-2 按 token 表）

### Requirement: 极光渐变应用壳
三管理端应用壳 SHALL 一致：侧栏固定 240px、底为深靛紫极光渐变（`--bg-sidebar-gradient`）+ 顶部光晕（`--sidebar-glow`），激活项玻璃底（`--bg-sidebar-active`）+ 渐变竖条 + 白字；顶栏 56px 白面下缘细线；主内容冷紫灰亮底 + page-card。`el-menu` SHALL 用深色模式，侧栏图标单色跟文字色，MUST NOT 使用彩色 emoji/多色图标。

#### Scenario: 侧栏激活态
- **WHEN** 用户选中任一菜单项
- **THEN** 显示玻璃底 + 渐变竖条 + 白字，与样张 `admin-ui-style-aurora.html` 形态一致

#### Scenario: 三端壳一致
- **WHEN** 并排查看 auth-admin / user-admin / blog-admin
- **THEN** 壳布局与视觉除系统名/菜单数据外逐像素级一致（同构复制）

### Requirement: 组件视觉约定
主操作按钮 SHALL 为胶囊圆角 + 极光渐变（`--accent-gradient`）+ 光晕（`--primary-glow`）；状态 SHALL 用软胶囊且配对固定（成功 `#d1fae5/#047857`、警告 `#fef3c7/#b45309`、危险 `#ffe4e6/#be123c`、信息 `#e0e7ff/#4338ca`、进行 `#cffafe/#0e7490`）；卡片 hover SHALL 可抬升（`--shadow-card-hover`）；弹窗圆角 16 + 紫调阴影。渐变 MUST 仅出自 `--accent-gradient` 与 `--stat-grad-*`，MUST NOT 出现页面级大面积渐变背景、毛玻璃滥用（仅侧栏激活项）、过重投影、第三方图标包。

#### Scenario: 主按钮视觉
- **WHEN** 渲染任一主操作按钮（默认/hover/active/禁用）
- **THEN** 呈现渐变胶囊 + 光晕，禁用态降饱和不发光

#### Scenario: 状态软胶囊
- **WHEN** 表格状态列渲染启用/停用/失败/草稿/审核中等状态
- **THEN** 按固定配对出软胶囊，不使用随意 `el-tag` 配色或大面积色块行

### Requirement: 视觉换装零行为变化
本基线 SHALL NOT 改变任何页面的布局结构、交互流程、请求参数/结果处理与权限判定（`hasPermission` 整钮不渲染等语义不变）；统计卡片 SHALL 仅在现有接口可提供数据时渲染（渐变 `--stat-grad-*`），无接口降级为说明卡 + 快捷入口，MUST NOT 虚构数据或新增统计接口。骨架组件（`CrudPage.vue` / `CrudForm.vue` / `DetailDialog.vue`）MUST NOT 因换装产生结构性改动（色值 token 化清底除外）。

#### Scenario: 换装前后行为一致
- **WHEN** 完成任一端视觉换装
- **THEN** 页面交互与请求行为与换装前逐项一致，仅视觉呈现变化

#### Scenario: 首页统计降级保持
- **WHEN** 无可用统计接口
- **THEN** 首页仍为说明卡 + 快捷入口，不渲染统计卡
