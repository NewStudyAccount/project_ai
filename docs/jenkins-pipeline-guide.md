# Jenkins 流水线创建指导

> 面向：需要为 `frontend/<system>` 或 `backend/<system>/<module>` 新建自动构建/部署流水线的同学与 AI 工具。
> 范围：如何**为指定服务建流水线**、阶段如何划分、如何对接多系统目录、AI 交付边界与验收。
> 不包含：公司 Jenkins 安装、K8s 发布细节、部署服务器账号密钥（放 Jenkins Credentials，不进本文）。
> Jenkins **管理台**访问信息见下节（仅测试环境；与 `docs/test-env.md` 同步登记）。
>
> **AI 使用方式：** 按 §3.3 填「服务流水线输入表」→ 用 `deploy/jenkins/create-jobs.py` 创建/更新 Job（**脚本内嵌**，Jenkinsfile 不入仓）→ §5 模板改占位符 → §4 步骤 5 验收 → §7 登记。缺输入项先向人索取或从 `docs/test-env.md` / `docs/port-registry.md` 补齐，**禁止编造**主机、端口、凭据 ID。

---

## 0. 本项目落地摘要（2026-10）

本仓库流水线已按下列约定建成（细节见 §7 与 `deploy/jenkins/README.md`）：

| 项 | 约定 |
|----|------|
| 交付形态 | **Jenkinsfile 不入仓**；Pipeline 内嵌在 Job（`CpsFlowDefinition`） |
| 创建/同步 | `deploy/jenkins/create-jobs.py`（幂等 create/update，直连 Jenkins REST） |
| Job 清单 | 9 条：前端 4（`user-admin-cd` / `auth-admin-cd` / `auth-portal-cd` / `blog-admin-cd`）+ 后端 5（`auth-service-cd` / `blog-gateway-cd` / `blog-service-cd` / `user-gateway-cd` / `user-service-cd`） |
| 触发参数 | **仅 `BRANCH`**（选分支，默认 `feature-base-design`）；**不提供** `DEPLOY_ENV` 选项 |
| 环境 | **后端**：`application.yml` → `spring.profiles.active`（代码指定），流水线不注入 `--spring.profiles.active`；**前端**：构建期 `VITE_*`，API 同源 `/api` |
| 部署形态 | `docker`（镜像仓库 `192.168.99.100:5000`，网络 `project-ai`） |
| Agent | **`agent any`（不绑 label）**；**全 Docker 构建**（`node:24-alpine` / `maven:3.9-eclipse-temurin-21`），宿主机只需 Docker |
| 阶段 | Checkout → 质量门/测试打包（容器内）→ 构建镜像 → 发布 → 健康检查 |
| 镜像 tag | `{REGISTRY}/project_ai/{target}:{git-short-sha}` 与 `:latest` |
| 凭据 ID | `github-token`（已有）、`ssh-deploy-test`、`registry-push-test`（后两者须 Jenkins 创建） |
| owner | `qjj` |

**新建可部署模块时：** 在 `create-jobs.py` 的 `JOBS` 表加一行 → 跑脚本 → §7 登记；仓库内**不**新增 Jenkinsfile。

---

## Jenkins 访问信息（测试环境）

| 项 | 值 |
|----|-----|
| 地址 | `http://192.168.99.100:8080/` |
| 用户名 | `qjj` |
| 密码 | `Qjj327846@!!` |
| 环境 | 测试 |

- 仅**测试环境** Jenkins 管理台登录，便于建 Job、绑凭据、看构建日志。
- **生产 Jenkins 账号禁止写入本仓库**（`CLAUDE.md` §6.7）。
- 部署用 SSH / 镜像仓库 / Git 等密钥仍只绑定 **Jenkins Credentials**，禁止写入 Jenkinsfile。
- 同步登记：`docs/test-env.md` §1 / §4 / §6.1。

---

## 1. 何时需要新建流水线

| 场景 | 动作 |
|------|------|
| 新增系统 `frontend/<system>` 或 `backend/<system>` | 按本文建该系统流水线 |
| 已有系统内新增可部署微服务模块 | 按「服务级」流水线新增一条（或 Job 参数化模块名） |
| 只改 `common`、不影响可部署物 | 可并入受影响服务的流水线触发，不必单独部署 |
| 改 `docs/`、`openspec/`、`CLAUDE.md` | 不需要业务流水线 |

---

## 2. 前置条件

在建流水线前确认（缺一则先补齐）：

1. **代码入库**：模块可编译，测试命令可本地跑通（见 `CLAUDE.md` §3.2）。
2. **Jenkins 凭据**（Credentials，禁止写进 Jenkinsfile / 仓库）：
   - Git 拉取（或 Webhook 所需权限）
   - 镜像仓库（如 Harbor/Registry）账号
   - 目标环境 SSH / 部署账号
   - （如需）Nacos、配置下发相关密钥
3. **构建节点（Agent）**：
   - **本项目：`agent any` + 全 Docker 构建**（`node:24-alpine` / `maven:3.9-eclipse-temurin-21`），宿主机只需 Docker
   - 入仓/多节点形态：前端 label **`node24`**，后端 **`jdk21`**
   - 部署：目标主机或容器运行时（本项目默认不用 K8s，见 §8）
4. **部署拓扑已明确**：部署到哪台/哪组机器、目录或容器名、端口、如何切换 `dev`/`test` profile（见 `CLAUDE.md` §6.10；组件 IP/账密见 `docs/test-env.md`；端口见 `docs/port-registry.md`）。
5. **§3.3 输入表已填完整**（AI 建流水线时必填；人建可简化但建议同样留档）。

### 2.1 凭据 ID 命名约定

| 用途 | 推荐 Credential ID | 类型 |
|------|-------------------|------|
| Git 拉取 | `git-<host-or-org>` | Username/SSH |
| 镜像仓库 | `registry-<name>` | Username/Password 或 Token |
| 测试环境 SSH | `ssh-deploy-test` | SSH Username with private key |
| 生产环境 SSH | `ssh-deploy-prod` | SSH Username with private key |
| 镜像仓库（推送） | `registry-push-<name>` | 与拉取可分离 |

- ID 一旦登记勿随意改名；Jenkinsfile 只引用 ID，**禁止**出现密码/私钥/Token 明文。
- 具体已创建的 ID 在 §7 登记表备注或 `docs/test-env.md` §4 补充，不写密钥值。

---

## 3. 流水线分层与命名

本仓库是**多系统**结构，流水线按「系统 → 可部署单元」两级组织。

### 3.1 建议 Job / 流水线命名

```
{system}-{target}-ci          # 例：构建+测试（PR/日常）
{system}-{target}-cd          # 例：构建+发布到 test / prod
{system}-{target}-cd-test     # 仅测试环境（若 CI/CD 拆开）
{system}-{target}-cd-prod     # 仅生产（必须审批）
```

- `{system}`：与目录 `frontend/<system>` / `backend/<system>` 一致。
- `{target}`：
  - 前端应用名（如 `admin`、`portal`）或系统名；
  - 后端可部署模块短名（如 `gateway`、`user-service`，去掉 `{system}-` 前缀）。
- 大小写与仓库目录一致，使用小写连字符。

### 3.2 触发策略（建议）

| 流水线 | 触发 | 说明 |
|--------|------|------|
| `{system}-*-ci` | 相关路径 push / PR | 路径过滤：`frontend/<system>/**`、`backend/<system>/**`、公共 `deploy/**` |
| `{system}-*-cd-test` | `main` 或 tag `test-*` | 自动发到测试环境 |
| `{system}-*-cd-prod` | tag `prod-*` 或手动 | **默认手动审批**，禁止全自动发生产 |

**路径过滤（避免改 A 系统触发 B 系统）：**

- Multibranch / Webhook 按 Git 托管平台语法配置「仅当变更包含下列路径时构建」：

```
frontend/<system>/**
backend/<system>/**
deploy/**
docs/test-env.md
docs/port-registry.md
```

- 若插件不支持路径过滤，用轻量前置阶段做 diff 检查，**后续 stage 用 `when` 跳过**（无关变更标成功，不进入质量门；禁止用 `error`/`|| true` 把无关变更或测试失败做成假结果）：

```groovy
stage('路径过滤') {
  steps {
    script {
      env.SKIP_BUILD = 'false'
      def changed = sh(returnStdout: true, script: '''
        git diff --name-only HEAD~1 HEAD 2>/dev/null || git diff --name-only HEAD
      ''').trim()
      def related = changed.readLines().any { p ->
        p.startsWith('frontend/<system>/') || p.startsWith('backend/<system>/') || p.startsWith('deploy/')
      }
      if (!related) {
        echo '无本系统相关变更，跳过后续阶段'
        env.SKIP_BUILD = 'true'
        currentBuild.result = 'SUCCESS'
      }
    }
  }
}
// 后续质量门/构建/发布 stage 均加：
// when { environment name: 'SKIP_BUILD', value: 'false' }
```

> 注：上例仅示意「无关变更不触发构建」；**测试/质量门失败必须失败**，禁止用路径过滤或 `|| true` 吞错。

### 3.3 服务流水线输入表（AI 必填契约）

为指定服务建流水线前，先填满下表。字段是生成 Job（内嵌 Pipeline）的**唯一参数来源**；能从 `docs/test-env.md`、`docs/port-registry.md` 读到的填实值，读不到的标 `（待补）` 并停下来问人，**禁止猜测**。

| 字段 | 必填 | 示例 | 说明 |
|------|------|------|------|
| `system` | 是 | `user-center` | 与 `frontend/` / `backend/` 子目录一致 |
| `target` | 是 | `user-service` | 可部署单元短名，用于 Job 名 |
| `kind` | 是 | `frontend` \| `backend-service` \| `gateway` | 决定骨架与构建物 |
| `unit_path` | 是 | `backend/user/user-service` | 可部署单元根（构建上下文） |
| `script_host` | 是 | `Job 内嵌` / `仓库 Jenkinsfile` | **本项目固定 `Job 内嵌`**（不入仓）；若入仓则填 `jenkinsfile_path` |
| `agent_label` | 否 | （空 / `any`） | **本项目 `agent any`**；多节点再填 `node24` \| `jdk21` |
| `build_tool` | 是 | `npm` \| `maven` | 前端 npm；后端 maven |
| `build_cmd` | 是 | 见 §5 | 质量门+构建命令（可在骨架上裁剪） |
| `artifact` | 是 | `dist/` \| `xxx.jar` | 构建物相对 `unit_path` |
| `deploy_mode` | 是 | `host-process` \| `docker` | 见 §4 步骤 4；全组统一 |
| `deploy_env` | 否 | （由代码 profile 决定） | **本项目不入流水线参数**；后端看 `application.yml`，前端看 `VITE_*` |
| `deploy_host` | 是* | `192.168.99.100` | 来自 `test-env.md`；*主机进程/Docker 必填 |
| `deploy_user` | 是* | `（test-env SSH 用户）` | 不写密码 |
| `ssh_cred_id` | 是* | `ssh-deploy-test` | 见 §2.1 |
| `deploy_path` | 前端/主机必填 | `/data/apps/user-admin` 或 Nginx 站点目录 | 静态目录或 jar 目录 |
| `container_name` | Docker 必填 | `user-service` | `docker run --name` |
| `image_repo` | Docker 必填 | `registry.example.local/user-center/user-service` | 无协议、可含 tag 约定 |
| `registry_cred_id` | Docker 必填 | `registry-push-…` | 推送凭据 |
| `app_port` | 是 | `18173` | 与 `port-registry.md` 一致 |
| `health_url` | 是 | `http://127.0.0.1:18173/actuator/health` | 前端可为 `http://127.0.0.1/` 或状态码约定 |
| `profiles_active` | 否 | （留空） | **本项目不注入**；后端以 `application.yml` 的 `spring.profiles.active` 为准 |
| `notify` | 否 | （Webhook/邮件，无则空） | 失败通知方式 |
| `owner` | 是 | （负责人） | 写入 §7 登记表 |

**AI 校验（填表后立刻做）：**

- `system` / `unit_path` 与仓库目录一致；
- `app_port` 已在 `docs/port-registry.md` 登记；
- `deploy_host` 等接入信息与 `docs/test-env.md` 一致；
- 无任何密码/token 字段；密钥只出现在 `*_cred_id`。

---

## 4. 为指定服务创建流水线（步骤）

### 步骤 1 — 确定流水线类型

| 可部署单元 | 类型 | 构建物 | 典型阶段 |
|------------|------|--------|----------|
| 前端应用 | Frontend | 静态资源（`dist`）→ 同步到 Nginx 目录或打包进镜像 | install → lint/test → build → 发布静态 |
| 网关 / 业务微服务 | Backend Service | 可执行 jar / 镜像 | test → package → （镜像）→ 发布 jar/容器 |
| 仅 `common` | 不单建 CD | — | 随下游服务 CI 一起 `mvn install` |

### 步骤 2 — 在仓库中放置 Jenkinsfile

**默认推荐：Jenkinsfile 进仓库**（与代码同版本），Job 只指向路径。

> **本项目例外（已裁决）：Jenkinsfile 不入仓。** Pipeline 以 Job 内嵌脚本交付，重建用 `deploy/jenkins/create-jobs.py`；仓库保留 Dockerfile 与 `deploy/jenkins/` 说明。下表路径规则仍适用于其它按仓库 Jenkinsfile 方式接入的工程。

```
frontend/<system>/Jenkinsfile              # 该系统前端
backend/<system>/<module>/Jenkinsfile      # 该可部署模块
deploy/jenkins/                            # 共享流水线库（可选，多系统复用）
```

- 多系统规则相似时，用 `deploy/jenkins/shared/` 存共享片段（`vars/` 或 `@Library`），避免复制粘贴。
- 禁止把密码、私钥、Token 写入 Jenkinsfile；一律 `credentials('id')` 或 Jenkins Credential 绑定。
- **AI 交付物（仓库内）：** 更新 `deploy/jenkins/create-jobs.py` 的 `JOBS` 与脚本模板；**不**新增仓库 Jenkinsfile；不提交密钥、不提交无关重构。

### 步骤 3 — 创建 Jenkins Job

#### 3.A 交付边界（AI 与人）

| 角色 | 交付 | 不做 |
|------|------|------|
| AI | `deploy/jenkins/create-jobs.py`（内嵌 Pipeline）、Job 配置说明、§7 登记、可脚本化验收 | 不写明文密钥；无授权时不改 Jenkins 全局凭据/节点 |
| 人（运维/负责人） | 绑定凭据（`ssh-deploy-test` / `registry-push-test`）、首次冒烟、Webhook | — |

若环境已提供 Jenkins REST API / CLI 且允许自动化，可走 **3.C Job DSL**；否则按 **3.B 配置清单** 人工点一次并把结果登记 §7。

#### 3.B 标准 Job 配置清单（人工或 API 一次配齐）

| 配置项 | 值 |
|--------|-----|
| Item 类型 | **Multibranch Pipeline**（或 Pipeline + 指到仓库 Jenkinsfile） |
| Display Name | `{system}-{target}-ci` 或 `…-cd` |
| Branch Sources | 本 Git 仓库；凭据 `git-…` |
| Build Strategies | 按需（如「定期」/「变更」） |
| Filter by name / 路径 | 见 §3.2；只含本系统与 `deploy/**` |
| Build Configuration | **本项目：Pipeline script（Job 内嵌）**；入仓形态则 Mode by **Jenkinsfile** |
| Script Path | （仅入仓形态）`{jenkinsfile_path}`；本项目不适用 |
| Credentials | `ssh-deploy-test` / `registry-…` 等 §2.1；**只绑 ID** |
| Agent | **`agent any`**（本项目）；多节点时用 `node24` 或 `jdk21` |
| CD 参数 | **`BRANCH`**（选分支；本项目不提供 `DEPLOY_ENV`）；`MODULE`（多模块时）；`SKIP_TESTS` 默认 `false`（生产禁止改 true） |
| 通知 | 按输入表 `notify` |
| 保存后 | 空跑/测试分支，确认 Agent、JDK/Node、Maven 缓存可用 |

#### 3.C 可选：Job DSL（减少手工、便于 AI 生成配置）

当 Jenkins 安装了 Job DSL 插件时，可把 §3.B 落成 `deploy/jenkins/job-dsl/{system}-{target}.groovy` 并用 seed Job 导入：

```groovy
// deploy/jenkins/job-dsl/<system>-<target>.groovy
multibranchPipelineJob('<system>-<target>-ci') {
  displayName('<system>-<target>-ci')
  branchSources {
    git {
      id('<system>-<target>')
      remote('<git-repo-url>')      // 勿写凭据明文；用 credentialId
      credentialsId('git-<host-or-org>')
    }
  }
  configure { project ->
    project / 'factory' / 'scriptPath' << '<jenkinsfile_path>'
  }
  orphanedItemStrategy { discardOldItems { numToKeep(20) } }
}
```

- Job DSL **不是**本项目强制路径；没有插件时用 3.B 即可。
- 禁止在 groovy 里写密码；只写 `credentialsId`。

### 步骤 4 — 实现阶段（契约）

阶段顺序建议固定，便于审批与回滚：

```
Checkout → （路径过滤）→ 质量门（lint/type-check/test）→ 构建 → 发布 → 冒烟/健康检查
```

**本项目阶段（与 §0 一致）：**

```
Checkout(BRANCH) → 质量门/测试打包 → 构建镜像 → 发布 → 健康检查
```

**质量门（必须，与 CLAUDE.md §3.2 一致；本项目在容器内执行）：**

| 类型 | 命令（在单元目录内） | 本项目执行方式 |
|------|----------------------|----------------|
| 前端 | `npm ci` → `npm run lint` → `npm run type-check` → `npm run build`（有 `test` 再加） | `docker run --rm node:24-alpine` 内执行 |
| 后端 | `mvn -pl <module> -am test` → `mvn -pl <module> -am package -DskipTests` | `docker run --rm maven:3.9-eclipse-temurin-21` 内执行（`.m2` 挂 workspace） |

- 测试失败 **必须** 流水线失败，禁止 `|| true` 吞错。
- **宿主机只需 Docker**，不装 JDK/Maven/Node（全 Docker 构建）。
- **后端 profile（本项目）：** 以代码 `application.yml` 的 `spring.profiles.active` 为准；发布命令**不**传 `--spring.profiles.active`。
- **前端环境（本项目）：** 构建期 `VITE_*`（`.env*` / `docker build-arg`）；API 同源 `/api`，不靠运行时开关。
- `SKIP_TESTS` 仅允许在明确调试时临时使用；生产 Job 建议不暴露或硬编码为 false。

**部署（本项目统一 Docker）：**

| 形态 | 前端 | 后端 |
|------|------|------|
| Docker | `docker build` nginx 静态镜像 → 推仓库 → 目标机 `docker run -p <port>:80` | `docker build` jar 镜像 → 推仓库 → 目标机 `docker run -p <port>:<port>` |

Nginx 配置变更走 `deploy/nginx/` 并随流水线/发布单同步，禁止只改服务器。

**发布阶段参数（来自 §3.3，写入 Job `environment`，勿写密钥）：**

```text
BRANCH, GIT_URL, GIT_CRED_ID,
DEPLOY_HOST, DEPLOY_USER, SSH_CRED_ID,
IMAGE_REPO, REGISTRY, REGISTRY_CRED_ID,
CONTAINER_NAME, APP_PORT, HEALTH_URL
```

### 步骤 5 — 验收清单（新流水线必须过）

**人工验收：**

- [ ] 只改本系统无关目录时，不应误触发（验证路径过滤）
- [ ] lint / test 失败时构建失败
- [ ] 选定 `BRANCH` 能发布且健康检查通过
- [ ] 生产分支/标签可回滚到上一镜像 tag
- [ ] 构建日志中无密码、Token、私钥
- [ ] Job 名称与目录/模块名对应，已登记在 §7
- [ ] 后端容器启动日志中的 profile 与 `application.yml` 的 `spring.profiles.active` 一致（流水线未注入）

**可脚本化子集（AI/CI 可在仓库侧执行，作为交付自检）：**

> 本项目 Jenkinsfile 不入仓，对 `deploy/jenkins/create-jobs.py` 生成的脚本做静态检查，或从 Job `config.xml` 导出后检查：

```bash
# 本项目：检查创建脚本与 Job 配置
python deploy/jenkins/create-jobs.py   # 幂等同步后，核对输出中的 checks

# 通用（入仓 Jenkinsfile 时）：将 <JF> 换成 jenkinsfile_path
JF=<jenkinsfile_path>
test -f "$JF" || { echo "缺少 Jenkinsfile: $JF"; exit 1; }
grep -Eq 'npm run (lint|type-check|test|build)|mvn .*test' "$JF" || { echo "缺少质量门"; exit 1; }
if grep -En 'password\s*[:=]\s*["'"'"'][^"'"'"']|BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY|secret\s*[:=]\s*["'"'"'][^"'"'"']' "$JF"; then
  echo "Jenkinsfile 疑似明文密钥"; exit 1
fi
grep -Eq 'docker |curl .*(health|actuator|healthz)' "$JF" || echo "WARN: 发布阶段仍为占位"
```

- 可脚本化只覆盖**仓库侧静态约束**；路径过滤、真实发布、回滚仍以人工清单为准。
- 质量门失败、发布失败必须使 Job 失败；禁止用 SKIP 或注释掉 stage 伪装成功。

---

## 5. 示例骨架（本项目已落地形态）

> **权威实现在 `deploy/jenkins/create-jobs.py`**（Job 内嵌脚本）。下文为对齐后的骨架，便于对照与新建模块；勿把密钥写进示例。
> Agent：**`agent any`**；质量门/打包在容器内，宿主机只要 Docker。

### 5.1 前端骨架（全 Docker → nginx 静态镜像）

```groovy
pipeline {
  agent any
  parameters {
    string(name: 'BRANCH', defaultValue: 'feature-base-design', description: 'Git branch to build and deploy', trim: true)
  }
  environment {
    APP_DIR     = 'frontend/<system>'
    NODE_IMAGE  = 'node:24-alpine'
    // GIT_* / DEPLOY_* / IMAGE_REPO / REGISTRY_* / APP_PORT / HEALTH_URL …
  }
  stages {
    stage('Checkout') {
      steps {
        checkout([$class: 'GitSCM',
          branches: [[name: "*/${params.BRANCH}"]],
          userRemoteConfigs: [[url: env.GIT_URL, credentialsId: env.GIT_CRED_ID]]])
      }
    }
    stage('Quality') {
      steps {
        sh '''
          docker run --rm -v "${WORKSPACE}/${APP_DIR}":/app -w /app "${NODE_IMAGE}" sh -c "
            npm ci && npm run lint && npm run type-check && npm run build
          "
        '''
      }
    }
    stage('Image') {
      steps {
        sh 'docker build -t "$IMAGE_REPO:$SHORT_COMMIT" -t "$IMAGE_REPO:latest" "$APP_DIR"'
      }
    }
    stage('Deploy') {
      steps {
        sshagent(credentials: ["${SSH_CRED}"]) {
          // login → push → 目标机 docker run -p <port>:80 → curl healthz
        }
      }
    }
    stage('Smoke') {
      steps {
        sshagent(credentials: ["${SSH_CRED}"]) {
          sh 'ssh "$DEPLOY_USER@$DEPLOY_HOST" "curl -fsS --retry 5 \"$HEALTH_URL\""'
        }
      }
    }
  }
}
```

### 5.2 后端骨架（全 Docker mvn → jar 镜像）

```groovy
pipeline {
  agent any
  parameters {
    string(name: 'BRANCH', defaultValue: 'feature-base-design', description: 'Git branch to build and deploy', trim: true)
  }
  environment {
    SYS_DIR     = 'backend/<system>'
    MODULE      = '<module>'
    MAVEN_IMAGE = 'maven:3.9-eclipse-temurin-21'
    // profile 由 application.yml spring.profiles.active 指定，流水线不注入
  }
  stages {
    stage('Checkout') { /* 同前端，检出 BRANCH */ }
    stage('Test') {
      steps {
        sh '''
          docker run --rm -v "${WORKSPACE}/${SYS_DIR}":/src -w /src \
            -v "${WORKSPACE}/.m2-cache":/root/.m2 \
            "${MAVEN_IMAGE}" mvn -pl ${MODULE} -am test
        '''
      }
    }
    stage('Package') {
      steps {
        sh '''
          docker run --rm -v "${WORKSPACE}/${SYS_DIR}":/src -w /src \
            -v "${WORKSPACE}/.m2-cache":/root/.m2 \
            "${MAVEN_IMAGE}" mvn -pl ${MODULE} -am package -DskipTests
        '''
      }
    }
    stage('Image') {
      steps {
        sh 'docker build -t "$IMAGE_REPO:$SHORT_COMMIT" -t "$IMAGE_REPO:latest" "$SYS_DIR/$MODULE"'
      }
    }
    stage('Deploy') {
      // login → push → 目标机 docker run -p <port>:<port>（不传 --spring.profiles.active）
    }
    stage('Smoke') {
      // curl http://127.0.0.1:<port>/actuator/health
    }
  }
}
```

### 5.3 发布片段（Docker，前后端共用要点）

```groovy
sshagent(credentials: ["${SSH_CRED}"]) {
  withCredentials([usernamePassword(credentialsId: "${REGISTRY_CRED}",
      usernameVariable: 'RUSER', passwordVariable: 'RPASS')]) {
    sh '''
      set -e
      echo "$RPASS" | docker login "$REGISTRY" -u "$RUSER" --password-stdin
      docker push "$IMAGE_REPO:$SHORT_COMMIT"
      docker push "$IMAGE_REPO:latest"
      ssh "$DEPLOY_USER@$DEPLOY_HOST" "
        docker network create project-ai 2>/dev/null || true
        docker rm -f $CONTAINER_NAME 2>/dev/null || true
        docker pull $IMAGE_REPO:$SHORT_COMMIT
        docker run -d --name $CONTAINER_NAME --restart unless-stopped \
          --network project-ai -p $APP_PORT:$APP_PORT $IMAGE_REPO:$SHORT_COMMIT
      "
    '''
  }
}
```

### 5.4 回滚约定

- Docker：使用上一镜像 tag（`{git-short-sha}`）重新 `docker run`。
- 建议保留至少 5 个历史镜像 tag。
- **生产回滚必须可执行且有人审批**；禁止只写文档无命令。

---

## 6. 与环境配置的对应（本项目）

| 层 | 谁决定 | 流水线 |
|----|--------|--------|
| 后端 Spring profile | `application.yml` → `spring.profiles.active`（代码） | **不**注入 `--spring.profiles.active` |
| 前端构建配置 | Vite `VITE_*`（`.env*` / build-arg） | 只 `npm run build` |
| 前端 API | 同源 `/api`，Nginx 反代 | 无需环境变量 |
| 构建/发布分支 | Job 参数 `BRANCH` | checkout + 镜像 tag `{sha}` / `latest` |

本地开发用 `local`（`application-local.yml`），**不**由 Jenkins 发布。后端配置三份：`application.yml` + `application-local.yml` + `application-test.yml`。详见 `CLAUDE.md` §6.10；组件 IP/端口/账密见 `docs/test-env.md`；端口登记见 `docs/port-registry.md`。

> 若仍使用 `DEPLOY_ENV` 参数形态（非本项目）：`test` → `application-test.yml`，`prod` 默认人工确认。

---

## 7. 流水线登记表

新流水线创建后在此登记（避免影子 Job）。

**本项目约定：Jenkinsfile 不入仓**，Pipeline 脚本内嵌在 Jenkins Job（`CpsFlowDefinition`）；重建/变更用 `deploy/jenkins/create-jobs.py` 同步到 Jenkins。

| Job 名 | 系统 | 目标 | Pipeline 脚本位置 | 环境 | 部署形态 | 负责人 |
|--------|------|------|-------------------|------|----------|--------|
| `user-admin-cd` | user-admin | 前端管理端 | Job 内嵌（`deploy/jenkins/create-jobs.py` 生成） | test/prod | docker | qjj |
| `auth-admin-cd` | auth-admin | 认证运营后台 | Job 内嵌 | test/prod | docker | qjj |
| `auth-portal-cd` | auth-portal | 登录门面 | Job 内嵌 | test/prod | docker | qjj |
| `blog-admin-cd` | blog-admin | blog 管理端 | Job 内嵌 | test/prod | docker | qjj |
| `auth-service-cd` | auth | auth-service | Job 内嵌 | test/prod | docker | qjj |
| `blog-gateway-cd` | blog | blog-gateway | Job 内嵌 | test/prod | docker | qjj |
| `blog-service-cd` | blog | blog-server | Job 内嵌 | test/prod | docker | qjj |
| `user-gateway-cd` | user | user-gateway | Job 内嵌 | test/prod | docker | qjj |
| `user-service-cd` | user | user-service | Job 内嵌 | test/prod | docker | qjj |

**附注（凭据只写 ID）：**

| 项 | 值 |
|----|-----|
| Agent | **`agent any`（不绑 label）**；全 Docker 构建，节点只需 Docker |
| `GIT_CRED_ID` | `github-token`（已有） |
| `SSH_CRED_ID` | `ssh-deploy-test`（**须在 Jenkins 创建**） |
| `REGISTRY_CRED_ID` | `registry-push-test`（**须在 Jenkins 创建**） |
| 源码仓库 | `https://github.com/NewStudyAccount/project_ai.git` |
| **触发参数** | **`BRANCH`**（选分支，默认 `feature-base-design`；不提供 `DEPLOY_ENV` 选项） |
| 环境约定 | **后端**：`application.yml` 的 `spring.profiles.active`（代码指定），流水线不注入；**前端**：构建期 `VITE_*`，API 同源 `/api` |
| 镜像仓库 | `192.168.99.100:5000`（镜像名 `…/project_ai/{target}:{git-short-sha}`） |
| `DEPLOY_HOST` / `DEPLOY_USER` | `192.168.99.100` / `root` |
| `app_port` / 健康检查 | user-admin `5173` `/healthz`；auth-portal `5174` `/healthz`；auth-admin `5175` `/healthz`；blog-admin `5176` `/healthz`；auth-service `9080` `/actuator/health`；blog-gateway `9100` `/actuator/health`；blog-service `19100` `/actuator/health`（以 `application.yml` 为准）；user-gateway `8173` `/actuator/health`；user-service `18173` `/actuator/health` |
| Job 配置清单 | `deploy/jenkins/README.md` |
| 创建脚本 | `deploy/jenkins/create-jobs.py`（幂等 create/update） |

登记时建议附注：凭据 ID（只写 ID）、Agent label、`app_port`、健康检查 URL。

---

## 8. 明确不做 / 演进

- **当前不引入 K8s**：默认主机或 Docker Compose/`docker run`；若未来上 K8s，另开变更重写「发布」阶段（镜像与清单），**不**在未归档前改本契约。
- 不在 Jenkins 管数据库变更脚本的自动执行（migration/seed 属不可逆操作，需单独流程与确认）。
- 不把生产密钥放入多分支扫描可见的文件。
- 不强制 Job DSL / JCasC；有插件可选用 3.C，没有则 3.B。
- 不在本文写死**生产**密钥、生产域名、完整服务器清单（见 `docs/test-env.md`）；测试环境 Jenkins 管理台登录见文首「Jenkins 访问信息」。

---

## 9. 维护

- 本文是 CI/CD **操作指导**，不是业务规范；业务契约见根目录 `CLAUDE.md`。
- 阶段命令若与 `CLAUDE.md` §3.2 不一致，以 §3.2 为准并回写本文。
- 部署目标机器、端口等易变信息放环境配置或运维清单，不写死在本文正文（登记表可写 Job 名）。
- §3.3 输入表字段若在落地中不够用，**增量改表并回写本节**，禁止各 Jenkinsfile 私自发明同义字段名。
- Agent：**本项目 `agent any` + 全 Docker 构建**（见 §0）；多节点入仓形态才用 `node24` / `jdk21` label。
