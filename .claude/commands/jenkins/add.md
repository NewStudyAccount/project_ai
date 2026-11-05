---
name: "Jenkins: Add"
description: 给某个服务加一条 Jenkins 流水线（取证 → 写 jobs.yaml → 预检 → dry-run → 可选应用）
category: CI/CD
tags: [jenkins, pipeline, ci]
---

> **路径约定**：<技能根> = 含 `SKILL.md` 的目录；本文件位于 `<技能根>/assets/commands/`。下文不写死某个 AI 工具的目录名（`.claude` / `.trae` / `.codex`），因此同一份命令在任何工具下都成立。

为一个可部署单元新增流水线。**按 `<技能根>/SKILL.md` §2 执行**。

**输入**：服务名或目录名，例如 `/jenkins:add blog-portal`、`/jenkins:add backend/blog/blog-gateway`。
未给输入 → 用 AskUserQuestion 问是哪个服务，并列出 `frontend/*` 与 `backend/*/*` 中**有 Dockerfile** 的目录。

**步骤**

1. **取证（读真实文件，不靠命名推断）**

   | 字段 | 从哪取 |
   |---|---|
   | `system` | 目录名（`frontend/<system>` 或 `backend/<system>`） |
   | `unit_path` | 可部署单元相对仓库根的路径 |
   | `module` | 后端：`pom.xml` 有 `spring-boot-maven-plugin` **且**同目录有 `Dockerfile` 的那个模块 |
   | `port` | 端口登记文件里该组件的登记值 |
   | `health_path` | 前端 `/healthz`；后端 `/actuator/health` |
   | `target` | 镜像名/容器名/Job 名主干，默认取服务短名 |

2. **缺口处理**

   - 目录没有 Dockerfile → 说明「无法构建发布镜像」，提供 `assets/Dockerfile.frontend.tmpl` / `Dockerfile.backend.tmpl` 的要点，**等用户确认**再补；
   - 端口未登记 → 先让用户登记（或在登记文件里补），**不要**直接建 Job；
   - 后端缺 `repackage` → 明确指出「产出瘦 jar，容器 `java -jar` 会失败」，等用户点头再改 pom。

3. **写进 `deploy/jenkins/jobs.yaml`**：只追加这一条，不动其它条目。

4. **预检 + 渲染自查**

   ```bash
   python deploy/jenkins/preflight.py
   python deploy/jenkins/create-jobs.py --dry-run --only <name>
   ```

5. **确认是否下发**：用 AskUserQuestion 问「现在就同步到 Jenkins 吗？」。
   - 用户同意 → 执行 `python deploy/jenkins/create-jobs.py --only <name>`（需要 `JENKINS_URL/USER/TOKEN`；缺则提示，不要代填密码）；
   - 用户拒绝 → 停在这里，汇报 dry-run 结果。

6. **按 SKILL §3 汇报**，必须写清「未验证项」（例如 Jenkins 不可达、首次构建未跑）。

**禁止**：猜端口/镜像仓库/凭据 ID；手改 Jenkins 界面上的脚本；改其它 Job。
