# 把技能安装到新项目（bootstrap）

技能自带一套完整、可运行的机器：`assets/deploy-jenkins/`（4 个 py + 3 个模板 + jobs 模板 + README）。
目标项目里**不需要**已有任何 Jenkins 相关文件 —— 这一步就是把它装出来。

## 1. 先搬技能目录

把整个技能目录复制到新项目（三种 AI 工具任选其一位置，其余靠镜像脚本同步）：

```powershell
# 在新项目根目录执行；<old> 是技能来源仓库
Copy-Item -Recurse <old>\.claude\skills\jenkins-pipeline-onboarding  .\.claude\skills\
```

复制后**只编辑** `.claude/skills/jenkins-pipeline-onboarding/`，其余镜像目录由脚本生成：

```powershell
powershell -NoProfile -File .agents/sync-skills.ps1        # 需要先有 .agents/sync-skills.ps1
```

> 若新项目没有 `.agents/sync-skills.ps1`，先把它一起复制过去；没有该脚本也不影响本 skill 在 `.claude` 下工作。

## 2. 安装机器

```powershell
# 默认目标 = 技能所在仓库根目录
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1 -DryRun
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1
```

会生成：

```
deploy/jenkins/{preflight,render,create-jobs,jenkins_api}.py
deploy/jenkins/templates/{frontend.groovy,backend.groovy,job.xml}.tmpl
deploy/jenkins/jobs.yaml        # 从 jobs.example.yaml 生成，含 <TODO: ...> 占位
deploy/jenkins/README.md
docs/jenkins-pipeline-guide.md  # 若不存在则生成骨架
```

**不会**做的事：覆盖已存在的 `jobs.yaml`、写任何凭据、连 Jenkins、生成 Dockerfile。

## 3. 安装后必须改的 7 项

| # | 改什么 | 位置 | 判定标准 |
|---|---|---|---|
| 1 | `defaults.*` 全部 `<TODO: ...>` | `deploy/jenkins/jobs.yaml` | 仓库地址、分支、部署主机、镜像仓库、3 个凭据 ID、网络名 |
| 2 | `jobs:` 里的示例换成真实服务 | 同上 | 每条对应一个**可部署单元** |
| 3 | 端口登记 | `docs/port-registry.md` | 每个 `port` 都能在该文件里搜到 |
| 4 | Dockerfile | 每个可部署单元目录 | 抄 `assets/Dockerfile.frontend.tmpl` / `Dockerfile.backend.tmpl` |
| 5 | 前端 nginx 的 `/healthz` | `frontend/<system>/nginx.conf` | 抄 `assets/nginx.conf.tmpl`；缺它 Smoke 必失败 |
| 6 | 后端 `repackage` | 启动模块 `pom.xml` | 有 `spring-boot-maven-plugin` + `repackage`，否则瘦 jar 启动失败 |
| 7 | 本地凭据 | 环境变量或 `deploy/jenkins/.jenkins.env` | 后者要进 `.gitignore` |

改完先跑：

```bash
python deploy/jenkins/preflight.py            # ERROR 必须为 0
python deploy/jenkins/create-jobs.py --dry-run
```

## 4. 目录布局不是 `frontend/<system>` / `backend/<system>/<module>` 怎么办

两种办法：

1. **逐条指定**（推荐）：在 job 里写 `unit_path:`，显式给出可部署单元相对仓库根的路径。
   ```yaml
   - name: web-cd
     kind: frontend
     system: web
     unit_path: apps/web          # 覆盖默认 frontend/web
     target: web
     port: 5100
     health_path: /healthz
   ```
2. **改默认推导**：`deploy/jenkins/render.py` 的 `resolve()`（改 `Path("frontend") / system` 那两行）。

磁盘扫描类检查（覆盖缺口、前端缺 Dockerfile）仍会去看 `frontend/` 与 `backend/`；
布局不同时这两类 WARN 会失效，其余检查（目录/Dockerfile/端口/密钥/回读校验）照常工作。

## 5. 与技能快照保持一致

`deploy/jenkins/*.py` 与 `templates/*.tmpl` 是技能快照的副本。两边都可能被改，所以：

```powershell
# 只比对，不写
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1 -Mode Check

# 项目里改好了、要回收成新快照（升级技能包）
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1 -Mode Export
```

规则：**谁跑通了就以谁为准**，然后 Export 回快照，再 `sync-skills.ps1` 同步到其它工具镜像。

## 6. 已知限制（照实说）

| 限制 | 影响 | 规避 |
|---|---|---|
| 端口登记文件固定为 `docs/port-registry.md` | 没有该文件 → 端口检查跳过（WARN） | 建同名文件，或改 `preflight.py` 顶部 `PORT_REGISTRY` |
| 前端质量门固定 `lint` / `type-check` / `build` | 缺脚本会 ERROR | 补 package.json 脚本；不要删检查 |
| 健康检查约定 `/healthz`、`/actuator/health` | 不符合会 WARN | 按实际改，但要同步改 job 的 `health_path` |
| 部署形态固定 docker + `docker run` | 用 K8s/其他形态要改模板与发布段 | 见 `references/jenkins-profiles.md` §D |
| Agent 固定 `agent any` + 全 Docker 构建 | 构建机必须能跑 docker | 若要用固定 label，改 `defaults.agent_label` **并**改模板（当前模板写死 `agent any`） |

## 7. 新项目验收顺序

```
bootstrap  → 改 jobs.yaml TODO  →  preflight（ERROR 0）  →  dry-run
          →  建凭据（人工）  →  create-jobs.py --only <name>  →  首次构建五阶段
```
