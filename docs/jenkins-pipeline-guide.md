# Jenkins 流水线指导（人读手册）

> 面向：需要为 `frontend/<system>` 或 `backend/<system>/<module>` 新建自动构建/部署流水线的人。
> **AI 工具请走 skill：`.claude/skills/jenkins-pipeline-onboarding/`**（含输入契约、预检门禁、失败排查、验收清单）。
> 本文只保留**人与 AI 都需要知道的稳定约定**与**索引**；Job 明细、输入字段、阶段模板一律不在本文重复。
> 不包含：Jenkins 安装、K8s 发布、部署服务器密钥（凭据只进 Jenkins Credentials）。

## 0. 一句话现状

本仓库流水线是 **Job 内嵌 Pipeline**（Jenkinsfile 不入仓），由脚本从**唯一事实源**渲染并幂等同步到 Jenkins。

| 想知道 | 去哪里 |
|---|---|
| 有哪些 Job / 端口 / 健康检查 / 归属 | **`deploy/jenkins/jobs.yaml`**（唯一事实源） |
| 建流水线的完整步骤与门禁 | **`.claude/skills/jenkins-pipeline-onboarding/SKILL.md`** |
| 字段怎么填、怎么校验 | `.claude/skills/jenkins-pipeline-onboarding/references/input-contract.md` |
| Pipeline 长什么样 | `deploy/jenkins/templates/*.tmpl`，或 `--dry-run` 的产物 |
| 脚本怎么跑、怎么回滚 | `deploy/jenkins/README.md` |
| 环境地址与账号 | `docs/test-env.md`（§1 / §4 / §6.1） |
| 端口登记 | `docs/port-registry.md` |

固定形态（六条，别发明第二种）：

| 项 | 约定 |
|---|---|
| 脚本位置 | **Job 内嵌**（`CpsFlowDefinition`）；仓库**无 Jenkinsfile** |
| 同步 | `python deploy/jenkins/create-jobs.py`（幂等 create/update，先备份后覆盖） |
| 触发参数 | **只有 `BRANCH`**；**不提供** `DEPLOY_ENV` |
| Agent | **`agent any` + 全 Docker 构建**（宿主机只需 Docker：`node:24-alpine` / `maven:3.9-eclipse-temurin-21`） |
| 阶段 | Checkout → 质量门/测试打包 → 构建镜像 → 发布 → 健康检查 |
| 部署 | Docker（网络 `project-ai`），镜像 tag 为 `{git-short-sha}` 与 `latest` |

## Jenkins 访问信息（测试环境）

- 地址与账号：**只登记在 `docs/test-env.md`**（§1 组件表、§4 Jenkins 发布、§6.1 账密）。
- 生产 Jenkins 账号**禁止**写入本仓库（`CLAUDE.md` §6.7）。
- 本地跑脚本时用环境变量 `JENKINS_URL` / `JENKINS_USER` / `JENKINS_TOKEN`，详见
  `.claude/skills/jenkins-pipeline-onboarding/references/credentials.md`。

## 1. 何时需要新建流水线

| 场景 | 动作 |
|---|---|
| 新增系统 `frontend/<system>` 或 `backend/<system>` | 按 skill 建该系统流水线 |
| 已有系统内新增**可部署**微服务模块 | 在 `jobs.yaml` 加一条 |
| 只改 `common`/`framework`/业务库模块 | 不单独建 CD，随部署单元一起打包 |
| 改 `docs/`、`openspec/`、`CLAUDE.md` | 不需要业务流水线 |

## 2. 前置条件

1. **代码可构建**：模块能编译，质量门命令本地能跑通（`CLAUDE.md` §3.2）。
2. **Dockerfile 存在**：前端 `frontend/<system>/Dockerfile`；后端在**启动模块**目录（有 `spring-boot-maven-plugin` 的那个）。
3. **端口已登记**：`docs/port-registry.md`；未登记先登记。
4. **凭据已存在**（Jenkins Credentials，见 §2.1）：Git、镜像仓库、部署机 SSH。
5. **部署拓扑明确**：主机、容器名、端口、健康检查路径。

### 2.1 凭据 ID 命名约定

| 用途 | Credential ID | 类型 |
|---|---|---|
| Git 拉取 | `github-token` | Username/Token |
| 镜像仓库推送 | `registry-push-test` / `registry-push-prod` | Username/Password |
| 测试环境 SSH | `ssh-deploy-test` | SSH Username with private key |
| 生产环境 SSH | `ssh-deploy-prod` | SSH Username with private key |

- ID 登记后勿改名（改名 = 所有 Job 失效）；**值**只存在于 Jenkins，仓库内任何文件都不得出现。

## 3. 命名与触发

### 3.1 Job 命名

```
{system}-{target}-cd
```

- `{system}`：与 `frontend/<system>` / `backend/<system>` 目录一致；
- `{target}`：镜像名 / 容器名 / 健康检查锚点（后端可去掉 `{system}-` 前缀）；
- 小写连字符；与 `jobs.yaml` 的 `name` 完全一致。

### 3.2 触发策略

| 流水线 | 触发 | 说明 |
|---|---|---|
| `*-cd` | 手动 + `BRANCH` 参数 | 当前形态：选分支构建发布 |
| （未来）CI | 相关路径 push / PR | 路径过滤：`frontend/<system>/**`、`backend/<system>/**`、`deploy/**` |
| （未来）生产 | tag `prod-*` + 人工审批 | **禁止**全自动发生产 |

无关变更不触发构建时，用 `when` 跳过后续 stage，**不要**用 `|| true` 吞错。

### 3.3 输入契约

字段定义、取值来源与校验规则在
`.claude/skills/jenkins-pipeline-onboarding/references/input-contract.md`；
落地载体是 `deploy/jenkins/jobs.yaml`。**本文不再复制字段表**（防双源漂移）。

## 4. 建/改流水线（标准动作）

```bash
# 1) 改事实源：deploy/jenkins/jobs.yaml 追加或修改一条
# 2) 预检（ERROR 必须清零）
python deploy/jenkins/preflight.py
python deploy/jenkins/preflight.py --online   # 额外核凭据 ID 与可达性

# 3) 离线渲染自查，产物在临时目录
python deploy/jenkins/create-jobs.py --dry-run

# 4) 应用（先备份旧 config.xml，再覆盖；单条可用 --only）
python deploy/jenkins/create-jobs.py --only <job-name>

# 5) 验收：按 skill 的 assets/acceptance-checklist.md
```

> 手改 Jenkins 界面上的 Job 脚本会被下次同步覆盖。**只改仓库，再同步。**

## 5. 阶段契约

### 5.1 阶段顺序

```
Checkout(所选 BRANCH) → 质量门/测试打包 → 构建镜像 → 发布 → 健康检查
```

| 类型 | 质量门（在容器内执行） |
|---|---|
| 前端 | `npm ci` → `npm run lint` → `npm run type-check` → `npm run build` |
| 后端 | `mvn -pl <module> -am test` → `mvn -pl <module> -am package -DskipTests` |

- 测试/检查失败**必须**让构建失败；禁止 `|| true`、禁止注释 stage、禁止默认 `SKIP_TESTS=true`。
- 宿主机不装 JDK/Maven/Node；缓存挂 `${WORKSPACE}/.m2-cache`。
- 后端 profile：由 `application.yml` 的 `spring.profiles.active` 决定，流水线**不传** `--spring.profiles.active`。
- 前端环境：构建期 `VITE_*`；API 同源 `/api`。

### 5.2 发布与回滚

- 镜像：`{registry}/project_ai/{target}:{git-short-sha}` 与 `:latest`。
- 发布：`docker login → push → 目标机 docker run --network project-ai -p <port>:<port|80>`。
- 健康检查：前端 `http://127.0.0.1:<port>/healthz`；后端 `http://127.0.0.1:<port>/actuator/health`。
- 回滚：用上一 `{sha}` tag 重新 `docker run`；建议保留 ≥ 5 个历史 tag；**生产回滚必须可执行且有人审批**。

模板与完整命令见 `deploy/jenkins/templates/`（`frontend.groovy.tmpl` / `backend.groovy.tmpl` / `job.xml.tmpl`）。

## 6. 与环境配置的对应

| 层 | 谁决定 | 流水线 |
|---|---|---|
| 后端 Spring profile | `application.yml` → `spring.profiles.active` | **不**注入 |
| 前端构建配置 | Vite `VITE_*`（`.env*` / build-arg） | 只 `npm run build` |
| 前端 API | 同源 `/api`，Nginx 反代 | 无需环境变量 |
| 分支 | Job 参数 `BRANCH` | checkout + 镜像 tag |

本地开发用 `local`（`application-local.yml`），**不**由 Jenkins 发布。详见 `CLAUDE.md` §6.10。

## 7. 登记

**登记处是 `deploy/jenkins/jobs.yaml`**，不是本文。本文**不列 Job 清单**（列了就会过期，还会与事实源打架）：

| 想查什么 | 命令 / 位置 |
|---|---|
| 有哪些 Job、端口、健康检查、归属 | `deploy/jenkins/jobs.yaml` |
| 覆盖缺口与可部署性风险 | `python deploy/jenkins/preflight.py`（从仓库事实推导，不靠手写清单） |
| 登记规则、命名回溯、线上对账 | `.claude/skills/jenkins-pipeline-onboarding/references/job-registry.md` |

> 历史说明：`blog-service-cd` 的 docker 构建上下文曾指向 `backend/blog/blog-service`（该目录无 Dockerfile），
> 已改为 `backend/blog/blog-server`（`finalName=blog-service`，镜像名/服务名不变）。

## 8. 明确不做 / 演进

- **不引入 K8s**：默认 `docker run`（网络 `project-ai`）；上 K8s 需另开变更重写发布阶段。
- 不在 Jenkins 里自动执行 DB migration/seed（不可逆操作，需单独流程与确认）。
- 不强制 Job DSL / JCasC（有插件可选，但不能成为第二事实源）。
- 本文不写死生产密钥、生产域名、完整服务器清单。
- 不手维护第二份 Job 表（事实源只有一个）。

## 9. 维护

- 本文是**操作手册与索引**，不是业务规范；业务契约见 `CLAUDE.md`。
- 阶段命令若与 `CLAUDE.md` §3.2 不一致，以 §3.2 为准并回写本文。
- 新增/修改字段只改 `input-contract.md` + `jobs.yaml`，**不要**在本文复制字段表。
- 形态（内嵌 / 入仓 / Multibranch）变更属裁决项，须先改 §0 并走变更流程。
