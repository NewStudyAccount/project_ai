# Jenkins 流水线工具链

> 用法与流程见 `.claude/skills/jenkins-pipeline-onboarding/SKILL.md`；人读约定见 `docs/jenkins-pipeline-guide.md`。
> **本目录不保存任何密码/Token/私钥**；凭据只以 ID 形式出现，值在 Jenkins Credentials。

## 1. 目录结构

```
deploy/jenkins/
├── jobs.yaml            # ★ 唯一事实源：Job 名字/系统/模块/端口/健康检查/归属
├── templates/
│   ├── frontend.groovy.tmpl   # 前端 Pipeline（占位符 {{KEY}}）
│   ├── backend.groovy.tmpl    # 后端 Pipeline
│   └── job.xml.tmpl           # Job 配置（CpsFlowDefinition，脚本内嵌）
├── render.py            # 纯渲染：jobs.yaml + 模板 → config.xml（不联网）
├── preflight.py         # 只读预检：目录/Dockerfile/脚本/端口/密钥/覆盖缺口
├── create-jobs.py       # 入口：预检 → 渲染 → 创建/更新 → 回读校验
├── jenkins_api.py       # Jenkins REST（crumb + Cookie 会话 + Basic Auth）
└── .jenkins.env         # 本地凭据（gitignore，不存在也正常）
```

**Jenkinsfile 不入仓**：Pipeline 脚本内嵌在 Job（`CpsFlowDefinition`），渲染源是本目录的模板 + `jobs.yaml`。

## 2. 命令

```bash
# 预检（不联网）
python deploy/jenkins/preflight.py
python deploy/jenkins/preflight.py --json          # 机器可读
python deploy/jenkins/preflight.py --online        # 额外：可达性 + 凭据 ID 是否存在

# 离线渲染（不连 Jenkins，产物落临时目录）
python deploy/jenkins/create-jobs.py --dry-run
python deploy/jenkins/create-jobs.py --dry-run --out ./out

# 应用（幂等：存在则备份后覆盖，不存在则新建）
python deploy/jenkins/create-jobs.py
python deploy/jenkins/create-jobs.py --only blog-service-cd
python deploy/jenkins/create-jobs.py --check       # 只跑预检
```

退出码：`0` 成功；`1` 预检/应用/校验失败；`2` 清单不可读。

## 3. 凭据（本地执行时）

```powershell
$env:JENKINS_URL   = 'http://192.168.99.100:8080'
$env:JENKINS_USER  = 'qjj'
$env:JENKINS_TOKEN = '<API Token>'
```

或写 `deploy/jenkins/.jenkins.env`（已 gitignore）：

```text
JENKINS_URL=http://192.168.99.100:8080
JENKINS_USER=qjj
JENKINS_TOKEN=<token>
```

测试账号见 `docs/test-env.md` §6.1（**不复制到本目录**）。缺凭据时脚本报 `CredentialsMissing` 并给出指引，不会回退到内置密码。

## 4. Job 清单怎么改

1. 编辑 `jobs.yaml`（字段说明见文件内注释与 skill 的 `input-contract.md`）；
2. `python deploy/jenkins/preflight.py` → ERROR 必须为 0；
3. `--dry-run` 看产物；
4. `--only <name>` 应用。

不要手改 Jenkins 上的脚本——下次同步会被覆盖。

## 5. 回滚

每次覆盖前会把旧 `config.xml` 存到：
`%TEMP%\jenkins-job-backup\<时间戳>\<job-name>.config.xml`

恢复单条（PowerShell）：

```powershell
$ts = '<时间戳>'
curl.exe -u "$env:JENKINS_USER`:$env:JENKINS_TOKEN" -X POST `
  --data-binary "@$env:TEMP\jenkins-job-backup\$ts\blog-service-cd.config.xml" `
  -H "Content-Type: application/xml" `
  "$env:JENKINS_URL/job/blog-service-cd/config.xml"
```

新建的 Job 回滚 = 删除该 Job（Jenkins → 该 Job → Delete Pipeline）。失败时 `create-jobs.py` 会打印「已处理清单 + 备份目录」。

## 6. 仍须人工在 Jenkins 完成

1. **创建凭据**（若尚无）：
   - `ssh-deploy-test`：SSH Username with private key（部署机私钥）
   - `registry-push-test`：Username/Password（镜像仓库推送账号）
2. **节点能力**：`agent any` 的节点只需 **Docker**（无需 JDK/Maven/Node），并能拉取 `node:24-alpine`、`maven:3.9-eclipse-temurin-21` 与目标镜像。
3. **插件**：Pipeline、Credentials Binding、Git、SSH Credentials（配合 `sshUserPrivateKey` + `ssh -i`，**无需** SSH Agent 插件）。
4. **首次冒烟**：任选一条 Job，`BRANCH` 选真实分支跑通五阶段。
5. （可选）Webhook / 分支触发；`prod` 发布必须人工确认。

## 7. 现状与已知缺口

- Job 集合、端口、覆盖缺口以 `jobs.yaml` + `references/job-registry.md` 为准，本文件不复制明细。
- `frontend/blog-portal` 缺 Dockerfile（端口 5177 已登记），补齐后才能建流水线 —— 预检会拦住。
- 测试环境 Jenkins 若不可达，`--online` 会降级为 WARN 并说明原因；此时「应用」需等环境恢复。
