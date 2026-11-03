---
name: doc-driven-dev
description: 文档驱动的 AI 协作开发方法论（Doc-Driven AI Development）。当用户要为新系统/新业务域编写设计文档、建立 AI 编码规范、组织 CLAUDE.md/AGENTS.md 等契约层文件、走 SDD（规范驱动开发）流程落码、或评审/重构现有开发文档体系时使用。触发词：设计文档、文档体系、三件套、CLAUDE.md、AGENTS.md、SDD、openspec、契约层、领域设计、文档分工、防漂移、AI 开发规范、协作红线。
agent_created: true
---

# 文档驱动开发（Doc-Driven AI Development）

## 概述

本 skill 将「如何为 AI 编码工具组织开发文档」的一套经过实战验证的方法论落地执行。核心思想：**分层契约驱动**——把「稳定契约 / 领域设计 / 单次变更 / 代码真相」四层分离，每层只写自己的事并显式声明"不写什么"，用效力顺序与唯一清单消灭双源漂移，用红线与反模式表约束 AI 行为。

核心认知：AI 把文档当**规格说明书 + 护栏**用。文档不是附属品，而是控制 AI 行为的主界面。

## 六条元规则（所有场景必须遵守）

1. **单一事实源**：一件事只在一处定义，其余位置用指针引用（"唯一清单""唯一登记处""唯一裁剪源"是高频词）。
2. **显式分工表**：每个文档头部声明「本文放什么 / 不放什么」——"不放什么"里写清"该去哪查"。
3. **效力顺序**：稳定契约（L0）> 设计正文（L1）> 可视化原型（HTML/ER 图）。冲突时按序裁决，杜绝"原型即契约"。
4. **稳定 / 易变分离**：版本号、端口、账密、任务清单、完整实现代码永远不进契约层。
5. **决策留痕**：关键取舍写「决策 / 取舍 / 原因」三元组，防止后人或 AI 把刻意设计当缺陷"修掉"。
6. **开放问题收口**：未决事项收敛到唯一清单，其他文档只指向它；AI 遇到清单内事项**必须停下来征询**，不得替业务拍板。

## 场景路由（根据用户意图选择流程）

| 用户意图 | 执行流程 | 加载资源 |
|---|---|---|
| 为新项目建立 AI 开发规范 / 组织 CLAUDE.md | Step A：搭 L0 契约层 | 读 `references/l0-contract.md`，复制 `assets/claude-md-skeleton.md` 起步 |
| 为新系统/新业务域写设计文档 | Step B：写 L1 领域设计三件套 | 读 `references/l1-domain-design.md`，复制 `assets/l1-starter/` 起步 |
| 按 SDD 流程落码 | Step C：SDD 闭环 | 读 `references/sdd-workflow.md` |
| 评审 / 重构现有文档体系 | Step D：质量门验收 | 读 `references/quality-gate.md`，逐项核对输出差距报告 |
| 不确定归属的机制类问题（裁剪源、唯一清单、裁决说明等） | 直接查阅 | 读 `references/key-mechanisms.md` |

多个场景可串联：新项目 = A → B；新业务域落码 = B → C；文档改造 = D →（按差距）A 或 B。

## Step A：搭 L0 契约层

1. 读 `references/l0-contract.md` 获取完整骨架与写作要领。
2. 将 `assets/claude-md-skeleton.md` 复制到项目根目录 `CLAUDE.md`，逐节填充。
3. 入口文件（AGENTS.md 等）**只写指针指向 CLAUDE.md，不复制内容**——复制即漂移。
4. 自查 L0 质量门（见 quality-gate 契约层部分）：无版本号/端口/密钥/任务清单混入。

## Step B：写 L1 领域设计三件套

0. **设计前置（必做，不得跳过）**：三件套是设计的**表达**，不是设计的**发生地**。先与用户完成设计裁决——AI 产出「设计要点提案」（领域模型草案、状态机草案、关键取舍三元组、待裁决清单含默认取值），**不写任何文档**；用户逐条裁决确认后，才进入模板填充。未裁决项登记进开放问题清单，不得替用户拍板。
1. 读 `references/l1-domain-design.md` 获取三件套（README / business-design / table-design / api-design）各章节的模板与要点。
2. 将 `assets/l1-starter/` 下的四个模板复制到 `docs/<domain>/`，按模板填充，**只写已裁决的内容**；填充中新出现的设计点回到前置裁决，不允许边填边拍板。
3. 通用能力用「裁剪取值」方式登记差异，**不复制通用条文**（见 key-mechanisms §3）。
4. 正文定稿后**生成可视化**（详见 l1-domain-design §4）：`er-diagram.html` 必做（依据 table-design 关系一览，mermaid erDiagram）；`prototype.html` 有前端边界的域必做（依据路由树 + 接口总表，页面元素标注契约锚点）。可视化从正文派生，不得反向发明设计。
5. 完成后跑 L1 质量门自查（见 quality-gate 设计层部分）。

## Step C：SDD 落码闭环

按 `references/sdd-workflow.md` 执行：explore → propose（proposal/specs/design/tasks）→ apply（只按 tasks 清单落码，只勾状态位）→ archive（偏差回写设计文档）。

四条铁律：代码实施只按 tasks 清单进行；不擅自修改 proposal/specs/design 定义；未归档前 change 制品是唯一指令源；落码后与设计有偏差时以代码 + specs 为准回写设计文档。

注意：若项目已有 SDD 命令入口（如 `.claude/commands/opsx/`、openspec 目录），使用项目既有入口，本 skill 只提供方法论约束，不重复造机制。

## Step D：文档体系评审

1. 读 `references/quality-gate.md`，按「契约层 / 设计层 / 流程层 / AI 行为层」四个维度逐项核对。
2. 输出差距报告：已达标项 / 缺失项 / 修复建议（按优先级排序）。
3. 改造已有文档体系时优先补四个核心机制（分工表、Non-goals + 反模式表、开放问题唯一清单、决策三元组），再逐步补齐骨架——这四项是收益最高的最小可行版本。

## 注意事项

- 契约与文档冲突：以代码为准改文档，或停下来向用户指出冲突。
- 不在契约层写服务清单、库表明细、完整实现。
- **目录结构以脚手架/既有框架的真实结构为准**（skill 不硬编码任何框架的目录样式）；L0 §4 必须给出具体目录树 + 模块职责表 + 目录纪律，AI 不得凭推导发明目录，不得新建未登记的顶层目录。
- 接口文档的接口逻辑必须含**否定性约束**（"不改 X""不碰 Y"）——对 AI 极其重要。
- 待裁决事项不得替业务拍板，必须停下征询。
- 边界（Non-goals）与正向需求同等重要——AI 最容易在"没说不做的地方"发挥过度。

## 资源清单

- `references/l0-contract.md` — L0 契约层（CLAUDE.md）骨架模板与写作要领
- `references/l1-domain-design.md` — L1 领域设计三件套模板详解（README / business / table / api + 可视化）
- `references/key-mechanisms.md` — 六大关键机制详解（分工表、唯一清单、裁剪源、裁决说明、红线反模式、接口契约技巧）
- `references/sdd-workflow.md` — SDD 闭环流程与铁律
- `references/quality-gate.md` — 质量门验收清单（四维度）
- `assets/claude-md-skeleton.md` — L0 骨架文件，复制后填充
- `assets/l1-starter/` — 三件套初始模板（README / business-design / table-design / api-design），复制到 docs/<domain>/ 后填充
