# doc-driven-dev Skill 使用规范（新项目从零起步操作手册）

> 适用场景：在一个**全新的项目**中，用 `doc-driven-dev` skill 建立四层文档体系（L0 契约 → L1 领域设计 → L2 变更制品 → L3 代码真相），并跑通首个 SDD 闭环。
> 方法论出处：`docs/ai-doc-methodology.md`；skill 本体：`.workbuddy/skills/doc-driven-dev/`

---

## 阶段 0：把 Skill 带进新项目（一次性，5 分钟）

skill 是**项目级安装**的，新项目里它不存在——这是事实源纪律的代价，先把它请过去。

| 方式 | 操作 | 适用 |
|---|---|---|
| 项目级（默认） | 把模板项目的 `.workbuddy/skills/doc-driven-dev/` 整个目录复制到新项目同路径 | 想随 git 共享给团队 |
| 用户级 | 复制到 `~/.workbuddy/skills/doc-driven-dev/` | 多个私人项目都要用，不想每个仓库放一份 |

**验证**：新会话中说「用 doc-driven-dev 帮我搭项目规范」，能触发即成功。

**分支决策（开始前先定，后面不再摇摆）**：

| 你的项目形态 | 裁剪程度 |
|---|---|
| 多系统单仓库 | 全套四层，每个系统一个 `docs/<system>/` 三件套 |
| 单体小项目 | L0 保留骨架；L1 三件套**可合并为一份设计文档**，但分工表/唯一清单/裁决说明不可省 |
| 纯前端 / 纯脚本 | L0 的红线 + 反模式 + Non-goals 是核心；L1 减为接口/数据两节 |
| 赶时间 | 最小可行版本：只做 4 件事（见附录 A） |

---

## 阶段 1：搭 L0 契约层（项目立项当天，半天内）

> 对应 skill 路由 **Step A**。产物：`CLAUDE.md`（唯一事实源）+ `AGENTS.md`（纯指针）。

1. **复制骨架**：把 skill 的 `assets/claude-md-skeleton.md` 复制为新项目根目录的 `CLAUDE.md`。
2. **逐节填充**（只填「必须长成什么样」的稳定契约）：
   - 文档分工表 → 项目概述（业务目标 + **明确不做清单**）→ 技术栈 +「明确不引入」清单 → 环境与启动 → **目录结构（⚠️ 重点节：必须照抄脚手架/既有框架的真实结构，给出具体目录树 + 模块职责表 + 目录纪律，禁止"标准分层"式模糊表述——这一节含糊，AI 后面每一步都会发明自己的目录）** → 架构设计 → 开发规范 → 协作红线 → 反模式表 → SDD 工作流入口 → 待裁决事项清单
3. **同步建三个登记处**（骨架里引用了它们，现在就得存在，否则指针悬空）：
   - `docs/test-env.md`（端口、IP、测试账密——**永远不进 CLAUDE.md**）
   - `docs/error-code-ranges.md`（错误码号段唯一登记处）
   - `docs/version-baseline.md`（组件版本唯一取用处）
4. **建入口指针**：`AGENTS.md` 只写一段话——"规范唯一事实源是 CLAUDE.md，修改规范只改它"。**一个字都不要复制**，复制即漂移。
5. **过质量门**：让 AI 按 skill 的 `references/quality-gate.md` 契约层五项自查（分工表 / Non-goals / 不引入+反模式 / 待裁决清单 / 无版本号端口密钥混入）。

**给 AI 下达的推荐口令**：
> 「用 doc-driven-dev 的 Step A，以 assets/claude-md-skeleton.md 为骨架，根据我下面的描述生成 CLAUDE.md：<项目描述、技术选型、明确不做的清单>」

---

## 阶段 2：写首个业务域的 L1 三件套（第一个功能开发前）

> 对应 skill 路由 **Step B**。产物：`docs/<domain>/` 下四个文件。

1. **复制模板**：把 skill 的 `assets/l1-starter/`（README / business-design / table-design / api-design）复制到 `docs/<domain>/`。
2. **按阅读顺序填充**：business-design → table-design → api-design，最后回填 README 索引页。
3. **填充时的四个必做动作**（缺一个，后面 AI 必漂移）：
   - business-design 里把 **Non-goals** 对齐 L0，状态机和业务规则**逐条编号**（R1、R2…，供任务引用）
   - table-design **结论清单放最前**；每个刻意取舍（拆表、冗余、预留字段）写进**字段裁决说明**（决策/取舍/原因）
   - api-design 每个接口写满**八元组**，接口逻辑里显式写「**不碰** X」的否定性约束
   - 所有未决事项收口到 table-design 的**开放问题唯一清单**，其他文档只放指针
4. **通用能力用裁剪取值**：跨系统通用的设计（对象存储、消息、鉴权…）单独建通用文档作为唯一裁剪源，系统文档只登记「本系统取值 + 差异」，不复制条文。
5. **过质量门**：按 quality-gate 设计层七项自查。

**推荐口令**：
> 「用 doc-driven-dev 的 Step B，以 assets/l1-starter 为模板，为 <业务域> 写三件套。已知信息如下：<需求、约束、边界>」

**注意**：AI 生成后，**人必须过一遍**——特别是 Non-goals、裁决说明、开放问题三处。这三处是「AI 替业务拍板」的高发区，遇到必须打回让它停下来问你。

---

## 阶段 3：跑通首个 SDD 闭环（第一个变更开始）

> 对应 skill 路由 **Step C**。产物：`openspec/changes/<change-name>/` 四件制品。

1. **初始化 SDD 入口**：项目如有 openspec 机制（`.claude/commands/opsx/`）就用它；没有则按所选工具初始化，skill 只提供纪律约束，不重复造机制。
2. **四阶段各干各的**：

   | 阶段 | 做什么 | 禁止 |
   |---|---|---|
   | explore | 读 L0 → 读相关 L1 → 看代码现状，只调研 | 写任何代码 |
   | propose | 产出 proposal / specs / design / tasks；design 从 L1 **裁剪**，不发明新口径 | 反向污染设计文档 |
   | apply | **只按 tasks 清单**落码，完成一项勾一项 | 擅自改 proposal/specs/design；顺手重构；扩大改动 |
   | archive | 归档封存；与代码有偏差处以代码 + specs 为准**回写 L1 文档** | 归档后还把 change 目录当指令源 |

3. **AI 行为三条硬验收**（阶段 3 结束时观察到的）：
   - 修 bug 前先要报错/日志/traceId
   - 遇到待裁决清单里的事会停下来问
   - 没有一次「顺手优化」

---

## 阶段 4：持续维护节奏（进入日常迭代后）

| 时机 | 动作 |
|---|---|
| 每次变更归档时 | 契约偏差回写 L1；检查 L0 待裁决清单是否需更新 |
| 每新增一个业务域 | 走阶段 2 流程（三件套） |
| 修订任何规范 | **只改唯一事实源**，其他位置永远是指针 |
| 每月 / 每里程碑 | 跑一次 quality-gate 全量清单，输出差距报告，按 P0（四核心机制）→ P1（骨架）→ P2（流程与行为层）排序修复 |
| 方法论本身演进 | 更新 `docs/ai-doc-methodology.md`，再同步 skill 的 references——skill 与方法论是**指针关系**，不要让两处各自生长 |

---

## 附：常用口令速查表

| 你说 | AI 走 |
|---|---|
| 「用 doc-driven-dev 帮新项目搭 AI 开发规范」 | Step A → L0 契约层 |
| 「用 doc-driven-dev 给 XX 业务域写设计文档」 | Step B → L1 三件套 |
| 「用 doc-driven-dev 按 SDD 流程实现 XX」 | Step C → explore→propose→apply→archive |
| 「用 doc-driven-dev 评审一下现有文档体系」 | Step D → 质量门差距报告 |

## 附录 A：最小可行版本（赶时间时的保底四件事）

做到这四点，AI 输出一致性即可显著提升，其余骨架可以后补：

1. **分工表**——每个文档声明「放什么 / 不放什么」
2. **Non-goals + 反模式表**——写死"不做的事"和"AI 常犯的错"
3. **开放问题唯一清单**——未决事项一处收口，AI 遇到必须停下问
4. **决策三元组**——关键取舍写「决策 / 取舍 / 原因」，防"优化式破坏"

## 附录 B：新项目验收清单（阶段 3 完成时打勾）

- [ ] `CLAUDE.md` 存在且过契约层质量门；`AGENTS.md` 纯指针
- [ ] 三个登记处（test-env / error-code-ranges / version-baseline）已建
- [ ] 首个业务域 `docs/<domain>/` 三件套齐备，过设计层质量门
- [ ] 首个 change 已走完 propose→apply→archive，偏差已回写 L1
- [ ] AI 行为三条硬验收全部通过
