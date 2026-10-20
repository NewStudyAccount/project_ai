# Design · restyle-admin-aurora

## Context

- 三管理端（auth/user/blog-admin）现行「云白」token 体系：`main.css` `:root` + `AppLayout.vue` 壳样式 + `components/common` 骨架（CrudPage/CrudForm/DetailDialog，三系统逐字节同构）。
- 新视觉基线「极光 Aurora」已定稿：`docs/template/admin-ui-design.md`（2026-10-20 重写）+ `docs/template/tokens-aurora.css` + 样张 `docs/template/admin-ui-style-aurora.html`。
- 关键现状：业务视图与骨架**全部走 CSS 变量**（QA 此前核验「整份 diff 新增/删除 hex 色值为 0」），换装主战场就是 token 表 + 壳样式两处。
- 技术栈 Vue3 + TS + Element Plus（`CLAUDE.md` §2）；Element Plus 主题经 `--el-color-primary` 系列覆盖。

## Goals / Non-Goals

**Goals:**

- 三端 token 一次性换装到位（16 改值 + 8 新增 + 胶囊 5 组配对）
- 壳换装：渐变侧栏 + 光晕 + 玻璃激活 + 渐变竖条 + 顶栏
- 组件视觉对齐：渐变主按钮/光晕、卡片 hover 抬升、软胶囊配对、统计卡渐变
- 旧云白色值零残留（硬编码清底）
- 三端视觉一致、骨架零改动

**Non-Goals:**

- 不改布局/交互/请求/权限行为
- 不动门户（auth-portal / blog-portal）
- 不改后端 / 制品 / 骨架组件代码
- 不做暗色内容主题 / 移动端 / 图表库

## Decisions

**D1 · Token 换装一次到位（同名变量换值 + 新增）**
三端 `main.css` `:root` 直接切换为 `tokens-aurora.css` 全量取值（含 `--el-color-primary` 系列）；不搞新旧 token 并存的过渡层。备选：引入 `data-theme` 双主题——否决：无暗色需求，徒增复杂度。

**D2 · 壳样式落点 `AppLayout.vue`，骨架零改动**
渐变侧栏/光晕/玻璃激活/渐变竖条全部在 `AppLayout.vue` 样式块与 `main.css` 壳类实现；`CrudPage/CrudForm/DetailDialog` 三系统逐字节不动（走 token 自动换肤）。若骨架个别样式硬编码了旧色值，按 D4 清底（属换装必要修改，不算骨架改动）。

**D3 · 渐变纪律：单一强调渐变来源**
`--accent-gradient` 是唯一强调渐变（主按钮/激活竖条/分页当前页/Logo）；统计卡渐变仅 `--stat-grad-1…4` 且仅 Dashboard；内容区保持明亮浅底；玻璃拟态仅侧栏激活项。禁止页面级大面积渐变与多层发光（`admin-ui-design.md` §1.1/§5）。

**D4 · 旧色值清底（grep 驱动）**
云白残值清单（`#0284c7` `#0369a1` `#38a2d8` `#6bb8e2` `#9fd0ec` `#b9def2` `#e6f3fa` `#026ea3` `#f0f9ff` `#0c4a6e` `#f6f7f9` `#e4e4e7` `#18181b` `#3f3f46` `#71717a` 等）在三端 `src/` 内 grep 零命中（注释/历史比选样张除外）；命中处一律替换为对应 token 引用。

**D5 · 视觉细节参照样张、取值以 token 文件为准**
样张 `admin-ui-style-aurora.html` 给形态（光晕半径、渐变角度、胶囊配对）；色值/变量名以 `tokens-aurora.css` 与 `admin-ui-design.md` §1 为唯一事实来源，三者冲突以后者为准。

**D6 · 三端同构、每端独立提交**
每端一个独立提交（`feat(*-admin): 切换极光 Aurora 视觉基线`），可单端 revert；三端 token 表与壳样式逐字一致（系统名/菜单数据除外）。

## Risks / Trade-offs

- [三端换装后相互漂移] → token 表从同一来源逐字复制；QA 三端 diff 对照（D6）
- [旧色值残留导致半套混用] → D4 grep 清底 + 自检任务硬门禁
- [侧栏深底文字对比度] → 侧栏文字三件套为样张固定值（`#edeaff`/`#a9a2d8`/`#ffffff`），不许微调
- [Element Plus 组件内部默认色未覆盖]（如 checkbox 边框、slider 等）→ 落地时目测走查组件覆盖 `--el-color-primary` 系列即可，个别默认灰不强求；如发现刺眼不协调，登记不顺手改
- [统计卡无数据源] → 维持既有降级语义（align-*-proto D6），统计卡 token 只预置不强用

## Migration Plan

纯前端视觉换装，无数据/接口迁移：

1. token 落地：三端 `main.css` `:root` 切换 `tokens-aurora.css` 全量取值
2. 壳换装：`AppLayout.vue`（侧栏渐变/光晕/玻璃激活/渐变竖条/顶栏）
3. 组件视觉对齐：渐变主按钮 + 光晕、软胶囊配对、hover 抬升、分页强调
4. 旧色值 grep 清底
5. 每端独立提交 + lint / type-check 通过
6. 回滚：按端提交单页 revert；无后端兼容性问题

## Open Questions

- Element Plus 深色 `el-menu` 在渐变底上的 hover 态是否需额外覆盖——落地走查后定，若仅用 token 可解则不新增样式
- 门户（auth-portal / blog-portal）换装时机——本变更不覆盖，待用户另行排期
