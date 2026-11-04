---
name: jenkins-pipeline-onboarding
description: 为指定项目/服务创建或修改 Jenkins 流水线（Job）时的执行流程与硬门禁。当用户说「给 X 建 Jenkins 流水线」「新建 Jenkins Job」「接入 CI/CD」「A 服务怎么发布」「补一条 cd 流水线」「流水线跑失败了」时使用。包含输入契约、预检门禁、离线渲染、幂等应用、验收清单与失败排查。
metadata:
  author: project_ai
  version: "1.0"
  updated: "2026-11-04"
---

# 为指定项目创建 Jenkins 流水线

面向「多系统单仓 + Docker 发布」项目的流水线接入流程。**本 skill 是流程与门禁；事实数据在 `deploy/jenkins/jobs.yaml`；机器在 `deploy/jenkins/*.py`（技能自带一份快照，可安装到新项目）。三者不要互相抄。**

## 0. 先认清四份东西各管什么

| 东西 | 角色 | 谁改 |
|------|------|------|
| `deploy/jenkins/jobs.yaml` | **Job 清单唯一事实源**（名字/系统/模块/端口/健康检查/归属） | 改 Job 集合只改这里 |
| `deploy/jenkins/templates/*.tmpl` | Pipeline 脚本与 Job XML 模板（占位符 `{{KEY}}`） | 阶段结构变化才改 |
| `deploy/jenkins/{preflight,render,create-jobs,jenkins_api}.py` | 预检 / 渲染 / 应用 / REST | 逻辑缺陷才改 |
| `docs/jenkins-pipeline-guide.md` | 人读手册 + 边界说明（不再手抄 Job 表） | 约定变化时改 |
| `assets/deploy-jenkins/` | 上述机器的**可搬迁快照** + `jobs.example.yaml` | 用 `bootstrap.ps1 -Mode Export` 回收 |

铁律：**`jobs.yaml` 之外不得出现第二份 Job 清单；任何 `*.py`/`*.md` 都不得写入密码或 Token。**

## 步骤 0 — 目标项目里还没有这套机器时，先装

```powershell
# 判断：deploy/jenkins/preflight.py 存在吗？不存在就走这里
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1 -DryRun
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1
```

它会装出 `deploy/jenkins/`（4 个 py + 3 个模板 + `jobs.yaml` 模板）和 `docs/jenkins-pipeline-guide.md` 骨架，
**不会**覆盖已有 `jobs.yaml`，也**不会**生成 Dockerfile。装完按 `references/bootstrap.md` §3 改 7 项。
若目标项目目录布局不是 `frontend/<system>` / `backend/<system>/<module>`，见 `bootstrap.md` §4（`unit_path`）。

## 1. 判定走哪条路

| 用户诉求 | 走法 |
|---|---|
| 新项目第一次接入 | 步骤 0（bootstrap）→ 本表下一行 |
| 新系统/新服务要发布（本仓库内） | §2 全流程（新增一条 job 条目） |
| 已有服务改构建/发布方式 | §2 全流程，但只动该条 job + 必要时模板 |
| 「流水线跑失败了」 | 直接跳 `references/failure-modes.md`，**先取证**再改 |
| 只改 `deploy/nginx`、`docs/`、`openspec/` | 不需要流水线，告知即可 |
| 前/后端还没 Dockerfile | 抄 `assets/Dockerfile.*.tmpl` 补上（见 `references/failure-modes.md`「缺 Dockerfile」），**不得**跳过 |

本仓库固定形态（不要发明第二种）：

- Agent：`agent any` + **全 Docker 构建**（宿主机只需 Docker，无 JDK/Maven/Node）
- 触发参数：**只有 `BRANCH`**（不提供 `DEPLOY_ENV`）
- Pipeline 脚本**内嵌在 Job** 里（`CpsFlowDefinition`），**Jenkinsfile 不入仓**
- 后端 profile 由代码 `application.yml` 的 `spring.profiles.active` 决定，流水线**不注入**
- 前端环境走构建期 `VITE_*`，API 同源 `/api`
- 部署形态：docker（镜像 `{registry}/project_ai/{target}:{git-short-sha}` + `:latest`）

## 2. 执行流程（六步，每步有产物）

### 步骤 1 — 收齐输入（缺就停下问，禁止编造）

打开 `references/input-contract.md`，按表逐项从**仓库真实文件**取证：

- `system` / `target` / `module` → 读目录与 `pom.xml` 的 `finalName`
- `port` → 必须已在 `docs/port-registry.md` 登记（没登记就先登记，再建流水线）
- `health_path` → 前端 `/healthz`，后端 `/actuator/health`
- 凭据 → **只写 ID**，值去 `docs/test-env.md` §6.1，禁止写进任何文件

拿不到就列「待补清单」交回给用户，**不要猜主机、端口、凭据 ID、镜像仓库**。

### 步骤 2 — 写进唯一事实源

在 `jobs.yaml` 的 `jobs:` 下追加一条（字段含义见表内注释）。同系统同构的服务只差 4 个字段：`name / target / port / health_path`。

### 步骤 3 — 预检（硬门禁，ERROR 必须清零）

```bash
python deploy/jenkins/preflight.py            # 人读
python deploy/jenkins/preflight.py --json     # 机器读（AI 用它判断）
python deploy/jenkins/preflight.py --online    # 额外查 Jenkins 可达性 + 凭据 ID 是否存在
```

预检会检查：目录存在 / Dockerfile 存在 / 前端必需 npm 脚本 / 后端 pom / 端口已登记且不重复 / 镜像名不冲突 / 无明文密钥，以及从仓库事实推导的可部署性缺口（有 Dockerfile 未登记、启动模块缺 `repackage` 插件、前端缺 Dockerfile）。

**任何 ERROR 都不得进入下一步。** 逐条修，不要用 `--skip-preflight` 绕过（只有用户明确要求时才用，且要在交付说明里写明绕过了什么）。

### 步骤 4 — 离线渲染并自查

```bash
python deploy/jenkins/create-jobs.py --dry-run
```

产物落在临时目录（`<tmp>/jenkins-render/<时间戳>/`），每条 job 一份 `.groovy` + `.config.xml`。逐条确认：

- 有 `BRANCH` 参数、没有 `DEPLOY_ENV`、没有 `spring.profiles`
- 前端有 `npm ci → lint → type-check → build`；后端有 `mvn -pl <module> -am test → package`
- 发布段是 `docker build → login → push → 目标机 docker run → curl 健康检查`
- 没有明文密钥

### 步骤 5 — 应用到 Jenkins

先按 `references/credentials.md` 设置环境变量（仓库内不存密码），再执行：

```bash
python deploy/jenkins/create-jobs.py                    # 全部
python deploy/jenkins/create-jobs.py --only <job-name>  # 只一条
```

脚本行为：预检 → 存在则**先备份旧 config.xml** 再覆盖 → 不存在则新建 → **回读校验**（参数/密钥/质量门/发布段）→ 打印现有 Job 列表。任一条失败会列出已处理清单与备份目录，按提示回滚。

### 步骤 6 — 验收与登记

按 `assets/acceptance-checklist.md` 过一遍。登记**不需要**改文档表格：`jobs.yaml` 就是登记处；若用户要求人读表格，从 `jobs.yaml` 生成，**不要手打第二份**。

## 3. 交付回话格式（照此汇报，别只贴命令输出）

```
流水线：<job-name>（<system>/<target>，kind=frontend|backend，port=<port>）
事实源：deploy/jenkins/jobs.yaml  （已登记 / 未改动）
预检：  ERROR 0 / WARN n    （--online：跳过/通过，原因）
渲染：  dry-run n/n 通过    产物：<路径>
应用：  created|updated|未执行（原因）
验收：  <逐项 通过/未验/失败>
未验证项：<例如 Jenkins 不可达、凭据待人工创建>
回滚：  <一句话命令或备份路径>
```

## 4. 禁止清单（踩了就是错）

- 猜主机/端口/凭据 ID/镜像仓库，或把密码、私钥、Token 写进任何将提交的文件。
- 在仓库里新增 Jenkinsfile（本仓库形态是 Job 内嵌）；照抄「Multibranch Pipeline」配置（那是别的形态，见 `references/jenkins-profiles.md`）。
- 手抄第二份 Job 清单到 md 里（`jobs.yaml` 是唯一源）；包括「当前覆盖情况」这类**带日期的状态快照**——要看就跑 `preflight.py`，不要抄进文档。
- 用 `|| true`、注释掉 stage、`SKIP_TESTS=true` 让质量门变绿。
- 无视预检 ERROR 强行创建；或为了「让预检通过」把检查逻辑改松。
- 顺手重构流水线结构、改别的 Job、给未要求的服务加发布能力（CLAUDE.md §7.8）。
- 流水线里注入 `--spring.profiles.active`（本项目由 `application.yml` 决定）。

## 5. 参考文件

| 文件 | 什么时候读 |
|---|---|
| `references/bootstrap.md` | 步骤 0，把机器装到新项目 / 换目录布局 / 与快照对账 |
| `references/input-contract.md` | 步骤 1，字段取值与校验规则 |
| `references/failure-modes.md` | 流水线失败、预检报错、初次接入踩坑 |
| `references/jenkins-profiles.md` | 怀疑形态选错了（入仓 Jenkinsfile / Multibranch / K8s） |
| `references/credentials.md` | 步骤 5 前，凭据怎么给、谁能建 |
| `references/job-registry.md` | 登记与覆盖缺口、文档映射 |
| `assets/acceptance-checklist.md` | 步骤 6 |
| `assets/Dockerfile.{frontend,backend}.tmpl`、`assets/nginx.conf.tmpl` | 目标项目缺 Dockerfile / 缺 `/healthz` 时抄 |

## 6. 技能自己的维护（改完必须做）

```powershell
# 1) 项目里跑通后，把实现回收成技能快照
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1 -Mode Export
# 2) 校验快照与项目实现是否一致（防止两边漂移）
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1 -Mode Check
# 3) 同步到其它 AI 工具的镜像目录
powershell -NoProfile -File .agents/sync-skills.ps1
```

规则：**谁跑通了以谁为准**，再 Export → Check → sync。
