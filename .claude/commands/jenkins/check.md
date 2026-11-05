---
name: "Jenkins: Check"
description: 流水线体检（预检 + 机器快照一致性 + 技能镜像一致性），只读不改
category: CI/CD
tags: [jenkins, lint, health-check]
---

> **路径约定**：<技能根> = 含 `SKILL.md` 的目录；本文件位于 `<技能根>/assets/commands/`。下文不写死某个 AI 工具的目录名（`.claude` / `.trae` / `.codex`），因此同一份命令在任何工具下都成立。

只做体检，**不修改任何文件、不连 Jenkins（除非显式要求 `--online`）**。

**输入**：可选 `--online`（额外核 Jenkins 可达性与凭据 ID）。

**步骤**

1. **预检**

   ```bash
   python deploy/jenkins/preflight.py
   ```
   （用户要在线核对时加 `--online`）

2. **机器与技能快照是否一致**

   ```powershell
   powershell -NoProfile -File <技能根>/scripts/bootstrap.ps1 -Mode Check
   ```
   不一致时说明：项目里改了机器 → 用 `-Mode Export` 回收；否则用 `bootstrap.ps1 -Force` 重装。

3. **技能镜像是否一致**（`.agents/sync-skills.ps1` 存在时才做）

   ```powershell
   powershell -NoProfile -File .agents/sync-skills.ps1 -Check
   ```

4. **汇总成一屏**

   | 检查 | 结果 |
   |---|---|
   | 预检 ERROR / WARN | n / n |
   | dry-run 静态自查（可选） | n/n |
   | 机器 vs 快照 | 一致 / 有漂移（列出） |
   | 技能镜像 | 一致 / 有漂移（列出） |

   再单独列「需要人决策的项」：没有流水线的可部署单元、缺 Dockerfile、缺 `repackage`、端口未登记。

**禁止**

- 在体检里顺手修改 `preflight.py`／pom／Dockerfile；
- 用 `--skip-preflight`；
- 把体检结果抄进 md 文档（要看就重跑命令）。
