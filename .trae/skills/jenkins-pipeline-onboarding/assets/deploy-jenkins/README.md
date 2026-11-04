# Jenkins 流水线工具链（技能自带）

> 由 `jenkins-pipeline-onboarding` 技能安装到目标项目的 `deploy/jenkins/`。
> 本目录**不保存任何密码/Token/私钥**；凭据只以 ID 形式出现，值在 Jenkins Credentials。

## 1. 目录结构

```
deploy/jenkins/
├── jobs.yaml            # ★ 唯一事实源：Job 名字/系统/模块/端口/健康检查/归属
├── templates/
│   ├── frontend.groovy.tmpl   # 前端 Pipeline（占位符 {{KEY}}）
│   ├── backend.groovy.tmpl    # 后端 Pipeline
│   └── job.xml.tmpl           # Job 配置（CpsFlowDefinition，脚本内嵌）
├── render.py            # 纯渲染：jobs.yaml + 模板 → config.xml（不联网）
├── preflight.py         # 只读预检：目录/Dockerfile/脚本/端口/密钥/可部署性
├── create-jobs.py       # 入口：预检 → 渲染 → 创建/更新 → 回读校验
├── jenkins_api.py       # Jenkins REST（crumb + Cookie 会话 + Basic Auth）
└── .jenkins.env         # 本地凭据（gitignore，不存在也正常）
```

**Jenkinsfile 不入仓**：Pipeline 脚本内嵌在 Job（`CpsFlowDefinition`）。

## 2. 命令

```bash
python deploy/jenkins/preflight.py                  # 预检（不联网）
python deploy/jenkins/preflight.py --json            # 机器可读
python deploy/jenkins/preflight.py --online          # 额外：可达性 + 凭据 ID

python deploy/jenkins/create-jobs.py --dry-run       # 离线渲染，产物落临时目录
python deploy/jenkins/create-jobs.py --only <name>   # 只同步一条
python deploy/jenkins/create-jobs.py                 # 全部同步
```

退出码：`0` 成功；`1` 预检/应用/校验失败；`2` 清单不可读。

## 3. 凭据（本地执行时）

```powershell
$env:JENKINS_URL   = 'http://<jenkins>:8080'
$env:JENKINS_USER  = '<user>'
$env:JENKINS_TOKEN = '<API Token>'
```

或写 `deploy/jenkins/.jenkins.env`（每行 `KEY=VALUE`，需自行加入 `.gitignore`）。
缺凭据时脚本报 `CredentialsMissing` 并给出指引，不会回退到内置密码。

## 4. 改 Job 清单

1. 编辑 `jobs.yaml`（字段见技能的 `references/input-contract.md`）；
2. `python deploy/jenkins/preflight.py` → ERROR 必须为 0；
3. `--dry-run` 看产物；
4. `--only <name>` 应用。

不要手改 Jenkins 上的脚本——下次同步会被覆盖。

## 5. 回滚

每次覆盖前旧 `config.xml` 存到 `%TEMP%\jenkins-job-backup\<时间戳>\<job>.config.xml`。
恢复：把备份 POST 回 `/job/<name>/config.xml`；新建的 Job 回滚 = 删除该 Job。

## 6. 仍须人工完成

1. **凭据**：Git、镜像仓库推送、部署机 SSH（只建 ID，不写值）；
2. **节点能力**：`agent any` 的节点只需 Docker，能拉 `node:24-alpine` / `maven:3.9-eclipse-temurin-21`；
3. **插件**：Pipeline、Credentials Binding、Git、SSH Credentials；
4. **首次冒烟**：任选一条，`BRANCH` 选真实分支跑通五阶段。

## 7. 与技能快照保持一致

本目录的 `*.py` 与 `templates/*.tmpl` 由技能安装。检查是否漂移：

```powershell
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1 -Mode Check
```
