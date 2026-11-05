---
name: "Jenkins: Apply"
description: 把 jobs.yaml 同步到 Jenkins（预检通过 → 备份 → 创建/更新 → 回读校验）
category: CI/CD
tags: [jenkins, deploy, ci]
---

> **路径约定**：<技能根> = 含 `SKILL.md` 的目录；本文件位于 `<技能根>/assets/commands/`。下文不写死某个 AI 工具的目录名（`.claude` / `.trae` / `.codex`），因此同一份命令在任何工具下都成立。

把事实源同步到 Jenkins。**这是唯一会写 Jenkins 的命令**，执行前必须预检通过。

**输入**：可选 Job 名，例如 `/jenkins:apply blog-service-cd`；省略 = 全部。

**步骤**

1. **预检（必经）**

   ```bash
   python deploy/jenkins/preflight.py
   ```
   有 ERROR → **立即停止**，报告 ERROR 并说明修法。**不要**用 `--skip-preflight` 绕过。

2. **凭据就绪检查**

   需要 `JENKINS_URL`、`JENKINS_USER`、`JENKINS_TOKEN`（或 `JENKINS_PASS`），或 `deploy/jenkins/.jenkins.env`。
   缺失时：报「缺少 Jenkins 凭据」并给出设置示例（值来源指向项目的环境账密登记处），
   **不要**替用户填写密码，也不要把密码写进任何文件。

3. **应用（幂等）**

   ```bash
   python deploy/jenkins/create-jobs.py --only <name>   # 单条
   python deploy/jenkins/create-jobs.py                 # 全部
   ```

   脚本行为：存在则先备份旧 `config.xml` → 覆盖 → 回读校验；不存在则新建。
   任一失败会打印「已处理清单 + 备份目录」。

4. **汇报**

   用 SKILL §3 的固定格式，至少包含：

   ```
   应用：created n / updated n / failed n
   备份：<目录>
   回滚：<单条命令>
   下一步：去 Jenkins 触发一次构建，确认五阶段（AI 不等构建结果，除非用户要求）
   ```

**禁止**

- 修改流水线语义来让应用成功（那是改 `jobs.yaml` 或模板的事，要单独确认）；
- 把 Jenkins 密码/Token 写进命令、文件或提交；
- 失败后反复重试碰运气——先看错误信息。
