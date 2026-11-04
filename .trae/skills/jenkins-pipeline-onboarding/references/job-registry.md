# 登记与覆盖

## 1. 唯一登记处

`deploy/jenkins/jobs.yaml`。**不要在任何 md 里手抄 Job 清单。**

| 想查什么 | 去哪 |
|---|---|
| 有哪些 Job、对应哪个系统/端口/健康检查 | `jobs.yaml`（`python deploy/jenkins/preflight.py` 可读性更好） |
| Job 的 Pipeline 长什么样 | `python deploy/jenkins/create-jobs.py --dry-run` 的产物 |
| 接口/组件端口规则 | `docs/port-registry.md` |
| 环境地址与账号 | `docs/test-env.md` |
| 流水线约定与边界（人读） | `docs/jenkins-pipeline-guide.md` |

`docs/jenkins-pipeline-guide.md` §7 与 `deploy/jenkins/README.md` §1 的历史表格已改为**索引**，不再维护第二份明细。

## 2. 覆盖状态怎么查（派生，不手写）

**本文不记录「当前覆盖情况」**——那是一份带时间戳的快照，新增一条流水线就过期，而且会变成 `jobs.yaml` 之外的第二个事实源，让 AI 不知道该信谁。

覆盖状态由 `preflight.py` 从仓库事实现场推导：

```bash
python deploy/jenkins/preflight.py
```

它推导三类缺口，每条都能在仓库里找到唯一物证，不需要额外登记表：

| 推导结果（WARN） | 物证 | 说明 |
|---|---|---|
| 目录内有 Dockerfile 但 jobs.yaml 未登记流水线 | 有 `Dockerfile`，无对应 job | 可部署但没接 CI，属于覆盖缺口 |
| pom.xml 未声明 `spring-boot-maven-plugin` | 有 `Dockerfile` + `pom.xml`，无该插件 | `mvn package` 产出瘦 jar（无 `Main-Class`），容器 `java -jar` 启动失败 |
| 前端目录缺 Dockerfile | 有 `package.json`，无 `Dockerfile` | 无法构建发布镜像，补齐前建不了 CD |

要确认「某条流水线在不在」，直接看 `jobs.yaml` 或上面命令的输出；**不要把输出结果抄回本文**。

## 3. 建 CD 的前提（规则，不是清单）

一个目录要能建 CD，必须同时满足四条：

1. 有 `Dockerfile`（docker build 的上下文）；
2. 后端是**启动模块**（有 `main` 类 / Boot 启动类）；
3. 后端 `pom.xml` 声明了 `spring-boot-maven-plugin` 与 `repackage`（否则产物不可执行）；
4. 端口已在 `docs/port-registry.md` 登记。

**库模块不单独建 CD**：只被依赖的 `common` / `framework` / `-api` / 业务库模块，随启动模块一起打包。
（本仓库 `backend/<system>` 下同时存在启动模块与业务模块时，判定依据是「有 Dockerfile 且能产出可执行 jar」的那一个；服务名以 `finalName` / `spring.application.name` 为准，不一定是模块名。）

## 4. 新增一条登记的标准动作

1. 在 `jobs.yaml` 的 `jobs:` 追加条目（字段见 `input-contract.md`）；
2. `python deploy/jenkins/preflight.py` → ERROR 必须清零；新增的 WARN 逐条解释或修掉；
3. `python deploy/jenkins/create-jobs.py --dry-run` → 自查产物；
4. `python deploy/jenkins/create-jobs.py --only <name>` → 应用；
5. 按 `assets/acceptance-checklist.md` 验收；
6. 若 `docs/jenkins-pipeline-guide.md` 里还残留该 Job 的旧明细行，删掉它（只留索引）。

## 5. 与线上对账（发现影子 Job）

```bash
python deploy/jenkins/preflight.py --online   # 会打印每条 job「已存在/将新建」
```

再加一步人工对账：Jenkins 里存在、`jobs.yaml` 里没有的 Job = 影子 Job。处理方式二选一：

- 它确实还要用 → 补登记到 `jobs.yaml`；
- 它已废弃 → 在 Jenkins 删除，并在交付说明里写明。

**不要**让「Jenkins 上有、仓库里没有」的 Job 长期存在（下次全量同步不会动它，但没人知道它为什么在）。

## 6. 命名回溯

`{system}-{target}-cd` 的 `{system}` 必须等于目录名；`{target}` 是镜像名/容器名/健康检查的锚点。
历史 Job 名允许「Job 名保留、module 不同」（例如 Job 名沿用服务名，而构建单元是 `{system}-server`），但必须在 `jobs.yaml` 的 `notes:` 里写一句为什么。
