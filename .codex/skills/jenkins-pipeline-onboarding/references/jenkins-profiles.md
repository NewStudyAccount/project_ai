# 流水线形态（选错形态是本类任务最大的返工源）

> 本仓库**已裁决**为形态 A。另外两种是「别的项目怎么接」的参考，**不要**把 B/C 的配置照搬进本仓库。

## A. Job 内嵌 Pipeline（本项目，默认）

| 项 | 值 |
|---|---|
| Jenkins Item 类型 | **Pipeline**（`flow-definition`），**不是** Multibranch |
| Pipeline 脚本位置 | **Job 里内嵌**（`CpsFlowDefinition`），仓库内**没有** Jenkinsfile |
| 唯一事实源 | `deploy/jenkins/jobs.yaml` + `templates/*.tmpl` |
| 同步方式 | `python deploy/jenkins/create-jobs.py`（幂等 create/update） |
| 触发 | 手动 + 参数 `BRANCH`（当前未配 Webhook） |
| Agent | `agent any` + 全 Docker 构建 |

**为什么这样选：** 单仓库多系统、Job 数量少、参数只有分支；内嵌脚本让「事实源」集中在 `jobs.yaml`，避免「仓库里 N 个 Jenkinsfile 各自漂移」。代价是必须用脚本同步，手改 Jenkins 会被下次同步覆盖。

## B. Jenkinsfile 入仓（常见默认做法，本项目不用）

| 项 | 值 |
|---|---|
| Item 类型 | Pipeline + `Pipeline script from SCM`，或 Multibranch |
| 脚本位置 | `frontend/<system>/Jenkinsfile`、`backend/<system>/<module>/Jenkinsfile` |
| 共享逻辑 | `deploy/jenkins/shared/`（`vars/` 或 `@Library`） |

适合：脚本需要跟随代码评审、需要按分支差异化、团队多人改流水线。
**本项目若要用 B：** 先改 `docs/jenkins-pipeline-guide.md` §0 的裁决和 `jobs.yaml` 模型，属于**变更**，不能顺手切。

## C. Multibranch Pipeline

| 项 | 值 |
|---|---|
| 用途 | 每个分支/PR 一条流水线，自动发现 |
| 前提 | 仓库内有 Jenkinsfile + 路径过滤策略（`frontend/<sys>/**` 等） |
| 风险 | 分支爆炸、无效分支扫描拖慢 Jenkins、生产发布难控 |

`docs/jenkins-pipeline-guide.md` §3.B 里出现的「Multibranch」段落属于**通用参考**，与本项目裁决存在冲突——以本文件 A 为准，读到那段不要照做。

## D. K8s 发布（本项目明确不做）

当前只有 docker（`docker run` / 网络 `project-ai`）。上 K8s 需另开变更重写发布阶段（镜像 + 清单 + 回滚），**不得**在未归档前改契约（CLAUDE.md §7）。

## 判别口诀

- 仓库里有没有 Jenkinsfile？没有 → A。有 → B/C。
- Job 是 `flow-definition` 还是 `multibranchPipelineJob`？前者 A，后者 C。
- 拿不准就看现有 Jenkins 上的 Job 类型（`/job/<name>/config.xml` 根标签），**以现状为准**，不要凭记忆。

## 从 A 迁到 B 时要动什么（仅备查，非本次任务）

1. 新增 Jenkinsfile（每可部署单元一份），把模板里的 `environment` 段搬到 `Jenkinsfile`；
2. `jobs.yaml` 增加 `jenkinsfile_path`，`create-jobs.py` 改为生成 SCM Pipeline（`CpsScmFlowDefinition`）；
3. 更新 `docs/jenkins-pipeline-guide.md` §0/§4 裁决与登记方式；
4. 处理既有 Job 的迁移与回滚（保留旧 config.xml 备份）。
