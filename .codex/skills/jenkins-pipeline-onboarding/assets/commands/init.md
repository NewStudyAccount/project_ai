---
name: "Jenkins: Init"
description: 为新项目接通 Jenkins 流水线（bootstrap → 填 jobs.yaml → 预检 → dry-run）
category: CI/CD
tags: [jenkins, bootstrap, ci]
---

> **路径约定**：<技能根> = 含 `SKILL.md` 的目录；本文件位于 `<技能根>/assets/commands/`。下文不写死某个 AI 工具的目录名（`.claude` / `.trae` / `.codex`），因此同一份命令在任何工具下都成立。

为新项目初始化 Jenkins 流水线机器。**严格执行 `<技能根>/SKILL.md` 的「步骤 0」+「§2 步骤 1~4」**，不要自行发明流程。

**输入**：无。
**停止点**：本命令**只到 dry-run**，不创建/更新任何 Jenkins Job。要真正下发，让用户执行 `/jenkins:apply`。

**步骤**

1. **判断机器是否已装**

   - `deploy/jenkins/preflight.py` 存在 → 报「机器已就绪」，跳到第 2 步。
   - 不存在 → 执行：
     ```powershell
     powershell -NoProfile -File <技能根>/scripts/bootstrap.ps1
     ```
     报出它生成了哪些文件（`deploy/jenkins/*`、`docs/jenkins-pipeline-guide.md` 骨架）。

2. **一次问齐 5 项（禁止猜，禁止分批骚扰）**

   用 AskUserQuestion 一次问完：
   - Git 仓库地址
   - 默认分支
   - 镜像仓库地址（含端口）
   - 部署机 IP + SSH 用户
   - 3 个凭据 ID（Git / 镜像仓库推送 / SSH）

   若用户已经在对话里给过，直接采用，不要重复问。

3. **写 `deploy/jenkins/jobs.yaml`**

   - 只填 `defaults` 和用户明确给出的服务；
   - 未给服务 → 保留 `jobs:` 里的一条示例，并在汇报里标注「待替换」；
   - 每个可部署单元还要：端口（必须已在端口登记文件里）+ 健康检查路径 + `module`（后端）。

4. **预检**

   ```bash
   python deploy/jenkins/preflight.py
   ```

   ERROR 逐条修（缺 Dockerfile 就抄 `assets/Dockerfile.*.tmpl`，缺 `repackage` 就补 pom），
   **不许**为了让检查通过而修改 `preflight.py` 或加 `--skip-preflight`。

5. **dry-run**

   ```bash
   python deploy/jenkins/create-jobs.py --dry-run
   ```

6. **按 SKILL §3 的固定格式汇报**：预检 ERROR/WARN 数、dry-run n/n、产物路径、未验证项，
   最后提示：「下一步 `/jenkins:add <服务>` 或 `/jenkins:apply`」。

**禁止**

- 编造仓库地址/端口/镜像仓库/凭据 ID；
- 生成 Dockerfile 之外的猜测性配置（镜像名、端口映射都由 `jobs.yaml` 推导）；
- 自动执行 `git commit` / `git push`；
- 跳过预检直接渲染。
