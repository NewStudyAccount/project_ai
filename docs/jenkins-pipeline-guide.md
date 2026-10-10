# Jenkins 流水线创建指导

> 面向：需要为 `frontend/<system>` 或 `backend/<system>/<module>` 新建自动构建/部署流水线的同学与 AI 工具。
> 范围：如何**为指定服务建流水线**、阶段如何划分、如何对接多系统目录、AI 交付边界与验收。
> 不包含：公司 Jenkins 安装、K8s 发布细节、具体服务器账号密钥（放凭据，不进本文）。
>
> **AI 使用方式：** 按 §3.3 填「服务流水线输入表」→ §4 步骤产出 Jenkinsfile + Job 配置 → §5 模板改占位符 → §4 步骤 5 验收 → §7 登记。缺输入项先向人索取或从 `docs/test-env.md` / `docs/port-registry.md` 补齐，**禁止编造**主机、端口、凭据 ID。

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
   - 前端：Node.js 24+（Agent label 统一 **`node24`**）
   - 后端：JDK 21 + Maven（Agent label 统一 **`jdk21`**）
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

为指定服务建流水线前，先填满下表。字段是生成 Jenkinsfile / Job 的**唯一参数来源**；能从 `docs/test-env.md`、`docs/port-registry.md` 读到的填实值，读不到的标 `（待补）` 并停下来问人，**禁止猜测**。

| 字段 | 必填 | 示例 | 说明 |
|------|------|------|------|
| `system` | 是 | `user-center` | 与 `frontend/` / `backend/` 子目录一致 |
| `target` | 是 | `user-service` | 可部署单元短名，用于 Job 名 |
| `kind` | 是 | `frontend` \| `backend-service` \| `gateway` | 决定骨架与构建物 |
| `unit_path` | 是 | `backend/user-center/user-service` | Jenkinsfile 所在目录（可部署单元根） |
| `jenkinsfile_path` | 是 | `backend/user-center/user-service/Jenkinsfile` | 相对仓库根 |
| `agent_label` | 是 | `node24` \| `jdk21` | 见 §2 |
| `build_tool` | 是 | `npm` \| `maven` | 前端 npm；后端 maven |
| `build_cmd` | 是 | 见 §5 | 质量门+构建命令（可在骨架上裁剪） |
| `artifact` | 是 | `dist/` \| `xxx.jar` | 构建物相对 `unit_path` |
| `deploy_mode` | 是 | `host-process` \| `docker` | 见 §4 步骤 4；全组统一 |
| `deploy_env` | 是 | `test`（`prod` 另建或参数） | 对应 Spring profile |
| `deploy_host` | 是* | `192.168.99.100` | 来自 `test-env.md`；*主机进程/Docker 必填 |
| `deploy_user` | 是* | `（test-env SSH 用户）` | 不写密码 |
| `ssh_cred_id` | 是* | `ssh-deploy-test` | 见 §2.1 |
| `deploy_path` | 前端/主机必填 | `/data/apps/user-admin` 或 Nginx 站点目录 | 静态目录或 jar 目录 |
| `container_name` | Docker 必填 | `user-service` | `docker run --name` |
| `image_repo` | Docker 必填 | `registry.example.local/user-center/user-service` | 无协议、可含 tag 约定 |
| `registry_cred_id` | Docker 必填 | `registry-push-…` | 推送凭据 |
| `app_port` | 是 | `18173` | 与 `port-registry.md` 一致 |
| `health_url` | 是 | `http://127.0.0.1:18173/actuator/health` | 前端可为 `http://127.0.0.1/` 或状态码约定 |
| `profiles_active` | 是 | `test` | 与 `DEPLOY_ENV` 一致（§6） |
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

**推荐：Jenkinsfile 进仓库**（与代码同版本），Job 只指向路径。

```
frontend/<system>/Jenkinsfile              # 该系统前端
backend/<system>/<module>/Jenkinsfile      # 该可部署模块
deploy/jenkins/                            # 共享流水线库（可选，多系统复用）
```

- 多系统规则相似时，用 `deploy/jenkins/shared/` 存共享片段（`vars/` 或 `@Library`），避免复制粘贴。
- 禁止把密码、私钥、Token 写入 Jenkinsfile；一律 `credentials('id')` 或 Jenkins Credential 绑定。
- **AI 交付物（仓库内）：** 按 §3.3 + §5 生成/修改该单元的 `Jenkinsfile`，必要时补 `deploy/nginx/` 片段说明；不提交密钥、不提交与本次无关的重构。

### 步骤 3 — 创建 Jenkins Job

#### 3.A 交付边界（AI 与人）

| 角色 | 交付 | 不做 |
|------|------|------|
| AI | `Jenkinsfile`、Job **配置清单**（下表）、§7 登记、可脚本化验收结果 | 默认不直连 Jenkins UI；无 API/CLI 授权时不改 Jenkins 全局配置 |
| 人（运维/负责人） | 在 Jenkins 建 Job 或导入 Job DSL、绑定凭据、配 Webhook/节点 | — |

若环境已提供 Jenkins REST API / CLI 且允许自动化，可走 **3.C Job DSL**；否则按 **3.B 配置清单** 人工点一次并把结果登记 §7。

#### 3.B 标准 Job 配置清单（人工或 API 一次配齐）

| 配置项 | 值 |
|--------|-----|
| Item 类型 | **Multibranch Pipeline**（或 Pipeline + 指到仓库 Jenkinsfile） |
| Display Name | `{system}-{target}-ci` 或 `…-cd` |
| Branch Sources | 本 Git 仓库；凭据 `git-…` |
| Build Strategies | 按需（如「定期」/「变更」） |
| Filter by name / 路径 | 见 §3.2；只含本系统与 `deploy/**` |
| Build Configuration | Mode by **Jenkinsfile** |
| Script Path | `{jenkinsfile_path}`（来自 §3.3） |
| Credentials | `ssh-deploy-test` / `registry-…` 等 §2.1；**只绑 ID** |
| Agent label | `node24` 或 `jdk21` |
| CD 参数 | `DEPLOY_ENV` = `test` \| `prod`；`MODULE`（多模块时）；`SKIP_TESTS` 默认 `false`（生产禁止改 true） |
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
Checkout → （路径过滤）→ 质量门（lint/type-check/test）→ 构建 → 部署到 DEPLOY_ENV → 冒烟/健康检查
```

**质量门（必须，与 CLAUDE.md §3.2 一致）：**

| 类型 | 命令（在单元目录内） |
|------|----------------------|
| 前端 | `npm ci` → `npm run lint` → `npm run type-check` → `npm run test` → `npm run build` |
| 后端 | `mvn -pl <module> -am test` → `mvn -pl <module> -am package -DskipTests`（测试已在前一步完成） |

- 测试失败 **必须** 流水线失败，禁止 `|| true` 吞错。
- 后端启动/部署 profile 必须显式：`DEPLOY_ENV=test` → `--spring.profiles.active=test`。
- `SKIP_TESTS` 仅允许在明确调试时临时使用；生产 Job 建议不暴露或硬编码为 false。

**部署（按运行形态二选一，全组统一）：**

| 形态 | 前端 | 后端 |
|------|------|------|
| 主机进程 / 虚机 | 将 `dist` 同步到 Nginx 站点目录并 reload | 传 jar → 停旧/起新 → 健康检查 `/actuator/health` |
| Docker（无 K8s） | 构建 nginx 静态镜像或只挂卷 | 构建应用镜像 → 推仓库 → 目标机 `docker run/up` |

Nginx 配置变更走 `deploy/nginx/` 并随流水线/发布单同步，禁止只改服务器。

**发布阶段参数（来自 §3.3，写入 Jenkins `environment` 或 Job 参数，勿写密钥）：**

```text
DEPLOY_ENV, DEPLOY_HOST, DEPLOY_USER, SSH_CRED_ID,
DEPLOY_PATH | CONTAINER_NAME, IMAGE_REPO, REGISTRY_CRED_ID,
APP_PORT, HEALTH_URL, PROFILES_ACTIVE, MODULE
```

### 步骤 5 — 验收清单（新流水线必须过）

**人工验收：**

- [ ] 只改本系统无关目录时，不应误触发（验证路径过滤）
- [ ] lint / test 失败时构建失败
- [ ] `DEPLOY_ENV=test` 能发布且健康检查通过
- [ ] `prod` 都要审批，且能回滚到上一制品
- [ ] 构建日志中无密码、Token、私钥
- [ ] Job 名称与目录/模块名对应，已登记在 §7

**可脚本化子集（AI/CI 可在仓库侧执行，作为交付自检）：**

```bash
# 在仓库根执行；将 <JF> 换成 jenkinsfile_path，<UNIT> 换成 unit_path
JF=<jenkinsfile_path>
UNIT=<unit_path>

# 1) Jenkinsfile 存在
test -f "$JF" || { echo "缺少 Jenkinsfile: $JF"; exit 1; }

# 2) 含质量门命令（按 kind 二选一应命中）
grep -Eq 'npm run (lint|type-check|test|build)' "$JF" || grep -Eq 'mvn .*test' "$JF" || { echo "缺少质量门"; exit 1; }

# 3) 禁止明文密钥/常见密钥块
if grep -En 'password\s*[:=]\s*["'"'"'][^"'"'"']|BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY|secret\s*[:=]\s*["'"'"'][^"'"'"']' "$JF"; then
  echo "Jenkinsfile 疑似明文密钥"; exit 1
fi

# 4) 发布/健康检查痕迹（骨架允许 echo 占位，但生产就绪前应替换为真实步骤）
grep -Eq 'DEPLOY_ENV|docker |scp |rsync |curl .*(health|actuator)' "$JF" || echo "WARN: 发布阶段仍为占位，请在就绪前替换"

# 5) 端口与 test-env / port-registry 对齐（APP_PORT 代入实际值后）
APP_PORT=<app_port>
grep -q "$APP_PORT" docs/port-registry.md || { echo "端口未登记: $APP_PORT"; exit 1; }
```

- 可脚本化只覆盖**仓库侧静态约束**；路径过滤、真实发布、回滚仍以人工清单为准。
- 质量门失败、发布失败必须使 Job 失败；禁止用 SKIP 或注释掉 stage 伪装成功。

---

## 5. 示例骨架

> 仅为阶段骨架，按 §3.3 输入表改占位符与公司 Jenkins 插件/部署方式；勿把密钥写进示例。
> Agent label 统一：`node24`（前端）、`jdk21`（后端），与 §2 一致。

### 5.1 前端 Jenkinsfile 骨架（主机 → Nginx 静态）

```groovy
pipeline {
  agent { label 'node24' }
  parameters {
    choice(name: 'DEPLOY_ENV', choices: ['test', 'prod'], description: '部署环境')
  }
  environment {
    APP_DIR     = 'frontend/<system>'
    DEPLOY_HOST = '<deploy_host>'
    DEPLOY_USER = '<deploy_user>'
    DEPLOY_PATH = '<deploy_path>'           // Nginx 站点目录
    HEALTH_URL  = '<health_url>'
    SSH_CRED    = '<ssh_cred_id>'           // 仅 ID
  }
  stages {
    stage('Checkout') { steps { checkout scm } }
    stage('质量门') {
      steps {
        dir("${APP_DIR}") {
          sh 'npm ci'
          sh 'npm run lint'
          sh 'npm run type-check'
          sh 'npm run test'
          sh 'npm run build'
        }
      }
    }
    stage('发布') {
      when { anyOf { branch 'main'; branch 'test-*' } }
      steps {
        sshagent(credentials: ["${SSH_CRED}"]) {
          sh '''
            set -e
            # dist → 目标机（示意；按环境改 rsync/scp 目标）
            rsync -az --delete "${APP_DIR}/dist/" "${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/"
            ssh "${DEPLOY_USER}@${DEPLOY_HOST}" 'nginx -t && nginx -s reload || systemctl reload nginx'
            curl -fsS --max-time 15 "${HEALTH_URL}" || curl -fsS --max-time 15 -o /dev/null -w "%{http_code}" "${HEALTH_URL}"
          '''
        }
      }
    }
  }
  post {
    failure { /* 通知：邮件/Webhook，按输入表 notify */ }
  }
}
```

### 5.2 后端服务 Jenkinsfile 骨架（主机进程 jar）

```groovy
pipeline {
  agent { label 'jdk21' }
  parameters {
    choice(name: 'DEPLOY_ENV', choices: ['test', 'prod'], description: '部署环境')
  }
  environment {
    SYS_DIR     = 'backend/<system>'
    MODULE      = '<system>-<domain>-service'
    DEPLOY_HOST = '<deploy_host>'
    DEPLOY_USER = '<deploy_user>'
    DEPLOY_PATH = '<deploy_path>'           // jar 目录
    APP_PORT    = '<app_port>'
    HEALTH_URL  = '<health_url>'
    SSH_CRED    = '<ssh_cred_id>'
    PROFILES    = "${params.DEPLOY_ENV}"   // 与 DEPLOY_ENV 一致（test/prod）
  }
  stages {
    stage('Checkout') { steps { checkout scm } }
    stage('测试') {
      steps {
        dir("${SYS_DIR}") {
          sh "mvn -pl ${MODULE} -am test"
        }
      }
    }
    stage('打包') {
      steps {
        dir("${SYS_DIR}") {
          sh "mvn -pl ${MODULE} -am package -DskipTests"
        }
      }
    }
    stage('发布') {
      when { anyOf { branch 'main'; branch 'test-*' } }
      steps {
        sshagent(credentials: ["${SSH_CRED}"]) {
          sh '''
            set -e
            JAR=$(ls "${SYS_DIR}"/*/target/*.jar | head -n1)
            # 传 jar → 备份旧包 → 停旧/起新 → 健康检查（示意）
            ssh "${DEPLOY_USER}@${DEPLOY_HOST}" "mkdir -p ${DEPLOY_PATH}"
            scp "${JAR}" "${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/app.jar"
            ssh "${DEPLOY_USER}@${DEPLOY_HOST}" "cd ${DEPLOY_PATH} && \
              ( [ -f app.jar ] && cp app.jar app.jar.bak || true ) && \
              pkill -f 'app.jar' || true; \
              nohup java -jar app.jar --spring.profiles.active=${PROFILES} > app.log 2>&1 &"
            sleep 5
            curl -fsS --retry 5 --retry-delay 3 --max-time 20 "${HEALTH_URL}"
          '''
        }
      }
    }
  }
  post {
    failure { /* 通知 */ }
  }
}
```

### 5.3 后端 Docker 形态发布片段（替换 §5.2 的「发布」stage）

```groovy
    stage('发布') {
      when { anyOf { branch 'main'; branch 'test-*' } }
      steps {
        withCredentials([usernamePassword(credentialsId: '<registry_cred_id>', usernameVariable: 'RUSER', passwordVariable: 'RPASS')]) {
          sh '''
            set -e
            SHORT_COMMIT=$(printf '%s' "${GIT_COMMIT:-unknown}" | cut -c1-12)
            IMAGE="<image_repo>:${SHORT_COMMIT}"
            docker build -t "${IMAGE}" -f "${SYS_DIR}/${MODULE}/Dockerfile" "${SYS_DIR}"
            echo "$RPASS" | docker login <registry-host> -u "$RUSER" --password-stdin
            docker push "${IMAGE}"
            # 目标机滚动/替换（示意）
            ssh "${DEPLOY_USER}@${DEPLOY_HOST}" "docker rm -f <container_name> || true; \
              docker run -d --name <container_name> --restart unless-stopped \
              -p <app_port>:<app_port> ${IMAGE} \
              --spring.profiles.active=${PROFILES}"
            curl -fsS --retry 5 --retry-delay 3 --max-time 20 "${HEALTH_URL}"
          '''
        }
      }
    }
```

### 5.4 回滚约定

- 主机 jar：保留 `app.jar.bak`（或制品库上一版本），回滚 = 覆盖回旧 jar 后重启并健康检查。
- Docker：使用上一镜像 tag 重新 `docker run`（或 compose `up`）。
- **prod 回滚必须可执行且有人审批**；禁止只写文档无命令。

---

## 6. 与环境配置的对应

| Jenkins `DEPLOY_ENV` | Spring profile | 用途 |
|----------------------|----------------|------|
| `test` | `test` | 测试环境自动/半自动发布 |
| `prod` | `prod` | 生产，默认人工确认 |

本地开发用 `local`（`application-local.yml`），**不**由 Jenkins 发布。后端配置三份：`application.yml` + `application-local.yml` + `application-test.yml`。详见 `CLAUDE.md` §6.10；组件 IP/端口/账密见 `docs/test-env.md`；端口登记见 `docs/port-registry.md`。

---

## 7. 流水线登记表

新流水线创建后在此登记（避免影子 Job）：

| Job 名 | 系统 | 目标 | Jenkinsfile 路径 | 环境 | 部署形态 | 负责人 |
|--------|------|------|------------------|------|----------|--------|
| （示例）`demo-admin-cd` | demo | admin 前端 | `frontend/demo/Jenkinsfile` | test/prod | host-process | — |

登记时建议附注：凭据 ID（只写 ID）、Agent label、`app_port`、健康检查 URL。

---

## 8. 明确不做 / 演进

- **当前不引入 K8s**：默认主机或 Docker Compose/`docker run`；若未来上 K8s，另开变更重写「发布」阶段（镜像与清单），**不**在未归档前改本契约。
- 不在 Jenkins 管数据库变更脚本的自动执行（migration/seed 属不可逆操作，需单独流程与确认）。
- 不把生产密钥放入多分支扫描可见的文件。
- 不强制 Job DSL / JCasC；有插件可选用 3.C，没有则 3.B。
- 不在本文写死真实主机密码、生产域名、完整服务器清单（见 `docs/test-env.md`）。

---

## 9. 维护

- 本文是 CI/CD **操作指导**，不是业务规范；业务契约见根目录 `CLAUDE.md`。
- 阶段命令若与 `CLAUDE.md` §3.2 不一致，以 §3.2 为准并回写本文。
- 部署目标机器、端口等易变信息放环境配置或运维清单，不写死在本文正文（登记表可写 Job 名）。
- §3.3 输入表字段若在落地中不够用，**增量改表并回写本节**，禁止各 Jenkinsfile 私自发明同义字段名。
- Agent label 以 §2 为准（`node24` / `jdk21`）；与节点实际 label 不一致时，先改节点或本文，禁止静默使用错误 label。
