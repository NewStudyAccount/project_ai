# Jenkins 流水线技能使用文档

> 技能本体：`.claude/skills/jenkins-pipeline-onboarding/`（AI 执行指令）
> 本文：给人看的**怎么用、什么时候用、出问题怎么办**
> 定位：把「为某个服务建 Jenkins 流水线」从一次性手工活，变成有门禁、可复制、可搬迁的标准动作

---

## 0. 它解决什么问题

| 环节 | 用之前 | 用之后 |
|---|---|---|
| 谁来建 | 人凭记忆点 Jenkins 界面，或复制上一条 Job 改 | 说一句话，AI 按固定六步走 |
| 参数从哪来 | 各处翻文档，容易抄错端口/主机 | 从 `jobs.yaml` + 仓库事实取，缺就停下问 |
| 错误什么时候发现 | 构建挂了才发现（缺 Dockerfile、瘦 jar） | 创建前预检拦截，ERROR 直接不许建 |
| 凭据 | 容易写进脚本或文档 | 只写 ID；值走环境变量 / Jenkins Credentials |
| 记录 | 多份表格各写一遍，很快过期 | 单一事实源，其余全是派生 |
| 换项目 | 全部重来 | 复制技能目录 + 一条 bootstrap 命令 |

---

## 1. 三种身份，三种入口

### 1.1 我是人 —— 说一句话就行

在 AI 工具里（Claude Code / Trae / Codex）直接说，skill 会按 `description` 自动命中：

| 你可以说 | 会发生什么 |
|---|---|
| 给 blog-portal 建一条 Jenkins 流水线 | 走完整六步 |
| 补一条 xxx-service 的 cd 流水线 | 同上 |
| xxx-cd 构建失败了 | 转排查分支，**先要报错**再改 |
| 先 dry-run，别碰 Jenkins | 只跑到渲染与自查，停在第 ④ 步 |
| 用 jenkins-pipeline-onboarding 帮我建流水线 | 显式点名，必定命中 |

不需要记命令。命令是给脚本和排查时用的，见 §5。

### 1.2 我是 AI —— 读 SKILL.md

`.claude/skills/jenkins-pipeline-onboarding/SKILL.md` 是**执行指令**：步骤、门禁、禁止清单、汇报格式。
它按需加载下钻文件，不要一次全读：

```
SKILL.md
├── references/bootstrap.md       新项目落地 / 换目录布局 / 快照对账
├── references/input-contract.md  字段取值与校验规则
├── references/failure-modes.md   失败排查（先取证）
├── references/credentials.md     凭据怎么给、谁能建
├── references/jenkins-profiles.md 形态判别（内嵌 / 入仓 / Multibranch / K8s）
├── references/job-registry.md    登记规则、覆盖缺口、命名回溯
└── assets/                       Dockerfile / nginx / 验收清单 / 可安装的机器
```

### 1.3 我是新项目 —— 跑 bootstrap

```powershell
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1
```

---

## 2. 快速上手（本仓库，机器已就绪）

```powershell
# 1) 预检：只读，不连 Jenkins
python deploy/jenkins/preflight.py

# 2) 离线渲染：产物落临时目录，逐条看
python deploy/jenkins/create-jobs.py --dry-run

# 3) 给凭据（只当前会话生效）
$env:JENKINS_URL   = 'http://192.168.99.100:8080'
$env:JENKINS_USER  = 'qjj'
$env:JENKINS_TOKEN = '<API Token>'

# 4) 应用：先备份旧 config.xml，再覆盖，最后回读校验
python deploy/jenkins/create-jobs.py --only user-service-cd
```

没有第 3 步也能跑到第 2 步；脚本报「缺少 Jenkins 凭据」时会把该设的变量列给你。

---

## 3. 新项目接入（三步）

```powershell
# ① 搬技能目录（整目录复制，不要只拷 SKILL.md）
Copy-Item -Recurse <old>\.claude\skills\jenkins-pipeline-onboarding  .\.claude\skills\

# ② 安装机器（不覆盖已有 jobs.yaml）
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1

# ③ 填 jobs.yaml 里的 <TODO: ...>，然后
python deploy/jenkins/preflight.py            # ERROR 必须为 0
python deploy/jenkins/create-jobs.py --dry-run
```

bootstrap 会生成：`deploy/jenkins/`（4 个 py + 3 个模板 + jobs.yaml 模板 + README）、`docs/jenkins-pipeline-guide.md` 骨架。

**新项目必须自己补的 7 项**（详见 `references/bootstrap.md` §3）：
仓库地址 / 分支 / 部署主机 / 镜像仓库 / 3 个凭据 ID / docker 网络名 → 都在 `jobs.yaml` 的 `defaults`。

**目录布局不是 `frontend/<system>` / `backend/<system>/<module>`？** 在 job 里写 `unit_path:` 显式覆盖，例如 `unit_path: apps/web`。

---

## 4. 典型场景

### 4.1 给已有服务补一条流水线

1. 在 `deploy/jenkins/jobs.yaml` 的 `jobs:` 追加一条（同构服务只差 4 个字段：`name / target / port / health_path`）
2. `preflight.py` → ERROR 清零
3. `create-jobs.py --dry-run` → 看产物
4. `create-jobs.py --only <name>`
5. 按 `assets/acceptance-checklist.md` 验收

### 4.2 改已有流水线（构建方式、模板、阶段）

改 `jobs.yaml`（参数）或 `templates/*.tmpl`（结构）→ 同样走 2~4 步。
**不要手改 Jenkins 界面上的脚本**，下次同步会覆盖。

### 4.3 流水线失败了

不要先改代码。按顺序取证：

```powershell
# 失败阶段、构建号、日志
python deploy/jenkins/preflight.py --online     # 先确认环境与凭据
```

然后让 AI 读 `references/failure-modes.md`（按现象 → 根因 → 定位 → 修法查表）。
铁律：**拿到完整报错再动手**，禁止凭猜测改、禁止用 `|| true` 或注释 stage 让构建变绿。

### 4.4 保持技能与项目一致

```powershell
# 项目里改通了 → 回收成技能快照
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1 -Mode Export
# 校验两边是否一致（漂移则退出码 1）
powershell -NoProfile -File .claude/skills/jenkins-pipeline-onboarding/scripts/bootstrap.ps1 -Mode Check
# 同步到 .trae / .codex / .agents 镜像
powershell -NoProfile -File .agents/sync-skills.ps1
```

规则：**谁跑通了以谁为准**，再 Export → Check → sync。

---

## 5. 命令速查

| 命令 | 作用 | 退出码 |
|---|---|---|
| `python deploy/jenkins/preflight.py` | 只读预检（目录/Dockerfile/脚本/端口/密钥/可部署性） | 0 通过；1 有 ERROR；2 清单读不了 |
| `... preflight.py --json` | 机器可读，AI 用它判断 | 同上 |
| `... preflight.py --online` | 额外查 Jenkins 可达性与凭据 ID 是否存在 | 同上 |
| `python deploy/jenkins/create-jobs.py --dry-run` | 离线渲染到临时目录并静态自查 | 0/1/2 |
| `... create-jobs.py --only <name>` | 只同步一条（可重复 `--only`） | 同上 |
| `... create-jobs.py` | 全量同步 | 同上 |
| `... create-jobs.py --check` | 只跑预检，不碰 Jenkins | 同上 |
| `bootstrap.ps1` | 安装机器到目标项目 | 0 成功；2 路径问题；3 已存在未加 `-Force` |
| `bootstrap.ps1 -Mode Check` | 比对快照与实现 | 0 一致；1 有漂移 |
| `bootstrap.ps1 -Mode Export` | 把项目实现回收成快照 | 0/2 |
| `sync-skills.ps1 [-Check]` | 技能镜像同步 / 漂移检测 | 0 一致；1 漂移 |

补充参数：`--spec <path>` 用别的清单、`--out <dir>` 指定 dry-run 产物目录、`--skip-preflight` 强行跳过预检（不推荐，且要在交付说明里写明）。

---

## 6. 为什么这样设计（三个关键取舍）

**① 只有一份 Job 清单**
`deploy/jenkins/jobs.yaml` 是唯一事实源。它**不会自动新增条目**——因为自动加就得猜端口、部署单元、负责人，而这三样必须有人裁决。写错比漏写危险：漏写会被预检 WARN 抓到，写错会一路带到镜像和线上。

**② 预检是门禁，不是提示**
它把「禁止编造主机/端口/凭据」从一句告示变成退出码。ERROR 不清零就不允许创建 Job；同时**不允许为了让预检通过而改松检查**。

**③ 技能自带机器 + 快照对账**
`assets/deploy-jenkins/` 是完整可运行的副本，新项目装了就能用；两边可能各改，所以有 `Check/Export` 保证不漂移。

---

## 7. FAQ

**Q：`jobs.yaml` 会自动添加吗？**
不会。没有任何代码往里写。要不要加、加什么，由人或 AI 显式编辑。

**Q：没有 Dockerfile 会自动生成吗？**
不会。预检会拦（已登记 → ERROR；未登记 → WARN）。你可以抄 `assets/Dockerfile.frontend.tmpl` / `Dockerfile.backend.tmpl`。

**Q：预检报「pom.xml 未声明 spring-boot-maven-plugin」是什么意思？**
该模块 `mvn package` 产出的是瘦 jar（无 `Main-Class`），镜像里 `java -jar` 启动就会失败。补上插件 + `repackage`。

**Q：预检报「端口未在 docs/port-registry.md 登记」？**
先去登记端口，再建流水线。这是硬约定，不是建议。

**Q：我在 Jenkins 界面上改了脚本，为什么没了？**
事实源在仓库。界面改动会被下次同步覆盖。改 `jobs.yaml` 或 `templates/`，再同步。

**Q：`--online` 报「无法列出 Jenkins 凭据（权限不足？）」是挂了吗？**
不是。只代表查不到，**不代表凭据不存在**。交付说明里应写「未确认」。

**Q：技能能不能用在 K8s / Jenkinsfile 入仓的项目？**
当前形态固定为「Job 内嵌 + docker run」。换形态要改模板与发布段，属于变更，不是配置项。判别见 `references/jenkins-profiles.md`。

**Q：改技能文件后要做什么？**
只改 `.claude/skills/` 那一份 → Export（若实现先改）→ Check → `sync-skills.ps1`。

---

## 8. 边界与已知限制

| 限制 | 现状 | 规避 |
|---|---|---|
| 端口登记文件固定 `docs/port-registry.md` | 缺文件时跳过端口检查（WARN） | 建同名文件，或改 `preflight.py` 顶部常量 |
| 前端质量门固定 `lint` / `type-check` / `build` | 缺脚本 → ERROR | 补 package.json 脚本，别删检查 |
| 健康检查约定 `/healthz`、`/actuator/health` | 不符 → WARN | 改 `health_path`，前端记得 nginx 配 `/healthz` |
| 部署形态固定 docker + `docker run` | K8s 需改模板 | 另开变更 |
| 磁盘扫描只看 `frontend/`、`backend/` | 换布局时覆盖缺口检查失效 | 用 `unit_path`，其余检查照常 |
| 技能不生成 Dockerfile / 不建凭据 | 设计与权限边界 | 抄模板 / 人工在 Jenkins 建 |

**本轮未验证**：真实 Jenkins 上没有跑通五阶段（凭据为 0、控制器在重启），`docker build` 与容器启动未实测。

---

## 9. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-04 | 初版技能：六步流程 + 预检门禁 + 唯一事实源 + 离线渲染 + 幂等应用 |
| 2026-11-04 | 收敛双源：Job 表/密码/输入契约各留一份；guide 降为人读手册 |
| 2026-11-04 | 加 `repackage` 缺失、瘦 jar、覆盖缺口三类派生检查 |
| 2026-11-04 | 技能自包含 + bootstrap（New/Check/Export）+ Dockerfile/nginx 模板；支持 `unit_path` 适配别家布局 |
