# Jenkins 流水线创建指导

> 面向：需要为 `frontend/<system>` 或 `backend/<system>/<module>` 新建自动构建/部署流水线的同学与 AI 工具。
> 范围：如何**为指定服务建流水线**、阶段如何划分、如何对接多系统目录。
> 不包含：公司 Jenkins 安装、K8s 发布细节、具体服务器账号密钥（放凭据，不进本文）。

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
   - 前端：Node.js 24+
   - 后端：JDK 21 + Maven
   - 部署：目标主机或容器运行时（本项目默认不用 K8s，见 §8）
4. **部署拓扑已明确**：部署到哪台/哪组机器、目录或容器名、端口、如何切换 `dev`/`test` profile（见 `CLAUDE.md` §6.10；组件 IP/账密见 `docs/test-env.md`）。

---

## 3. 流水线分层与命名

本仓库是**多系统**结构，流水线按「系统 → 可部署单元」两级组织。

### 3.1 建议 Job / 流水线命名

```
{system}-{target}-ci          # 例：构建+测试（PR/日常）
{system}-{target}-cd          # 例：构建+发布到 test / prod
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

**路径过滤示例（Multibranch / Webhook）：** 只监听本系统目录，避免改 A 系统触发 B 系统构建。

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

### 步骤 3 — 创建 Jenkins Job

1. **新建 Item** → 推荐 **Multibranch Pipeline**（或 Pipeline + 指到仓库 Jenkinsfile）。
2. **Branch Sources**：本 Git 仓库；按需配置 **Build Strategies** 与 **Filter by name**。
3. **Build Configuration**：
   - Mode by **Jenkinsfile**；
   - Script Path：`frontend/<system>/Jenkinsfile` 或 `backend/<system>/<module>/Jenkinsfile`。
4. **凭据**：绑定 §2 中所需 Credentials ID。
5. **参数（CD Job）**：
   - `DEPLOY_ENV`：`test` | `prod`（Spring profile 与 `application-{profile}.yml` 一致；仓库内必备 `application.yml` / `application-dev.yml` / `application-test.yml`）
   - `MODULE`：后端 `-pl` 模块名（若一个 Job 管多模块）
   - `SKIP_TESTS`：默认 `false`；**生产建议禁止手动改成 true**
6. **保存后先跑空跑/测试分支**，确认 Agent、JDK/Node、Maven 仓库缓存可用。

### 步骤 4 — 实现阶段（契约）

阶段顺序建议固定，便于审批与回滚：

```
Checkout → 质量门（lint/type-check/test）→ 构建 → 部署到 DEPLOY_ENV → 冒烟/健康检查
```

**质量门（必须，与 CLAUDE.md §3.2 一致）：**

| 类型 | 命令（在单元目录内） |
|------|----------------------|
| 前端 | `npm ci` → `npm run lint` → `npm run type-check` → `npm run test` → `npm run build` |
| 后端 | `mvn -pl <module> -am test` → `mvn -pl <module> -am package -DskipTests`（测试已在前一步完成） |

- 测试失败 **必须** 流水线失败，禁止 `|| true` 吞错。
- 后端启动/部署 profile 必须显式：`DEPLOY_ENV=test` → `--spring.profiles.active=test`。

**部署（按运行形态二选一，全组统一）：**

| 形态 | 前端 | 后端 |
|------|------|------|
| 主机进程 / 虚机 | 将 `dist` 同步到 Nginx 站点目录并 reload | 传 jar → 停旧/起新 → 健康检查 `/actuator/health` |
| Docker（无 K8s） | 构建 nginx 静态镜像或只挂卷 | 构建应用镜像 → 推仓库 → 目标机 `docker run/up` |

Nginx 配置变更走 `deploy/nginx/` 并随流水线/发布单同步，禁止只改服务器。

### 步骤 5 — 验收清单（新流水线必须过）

- [ ] 只改本系统无关目录时，不应误触发（验证路径过滤）
- [ ] lint / test 失败时构建失败
- [ ] `DEPLOY_ENV=test` 能发布且健康检查通过
- [ ] `prod` 需要审批，且能回滚到上一制品
- [ ] 构建日志中无密码、Token、私钥
- [ ] Job 名称与目录/模块名对应，已登记在 §7

---

## 5. 示例骨架

> 仅为阶段骨架，按公司 Jenkins 插件与部署方式改写；勿把密钥写进示例。

### 5.1 前端 Jenkinsfile 骨架

```groovy
pipeline {
  agent { label 'node18' }
  parameters {
    choice(name: 'DEPLOY_ENV', choices: ['test', 'prod'], description: '部署环境')
  }
  environment {
    APP_DIR = 'frontend/<system>'
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
      when { branch 'main' }
      steps {
        // 同步 dist 到 Nginx 站点目录或构建镜像并推送
        // 按 DEPLOY_ENV 选择目标目录 / 主机
        sh 'echo deploy frontend to ${DEPLOY_ENV}'
      }
    }
  }
  post {
    failure { /* 通知 */ }
  }
}
```

### 5.2 后端服务 Jenkinsfile 骨架

```groovy
pipeline {
  agent { label 'jdk21' }
  parameters {
    choice(name: 'DEPLOY_ENV', choices: ['test', 'prod'], description: '部署环境')
  }
  environment {
    SYS_DIR  = 'backend/<system>'
    MODULE   = '<system>-<domain>-service'
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
      when { branch 'main' }
      steps {
        // 发布 jar 或 docker run，使用 --spring.profiles.active=${DEPLOY_ENV}
        // 完成后 curl /actuator/health
        sh 'echo deploy ${MODULE} to ${DEPLOY_ENV}'
      }
    }
  }
}
```

---

## 6. 与环境配置的对应

| Jenkins `DEPLOY_ENV` | Spring profile | 用途 |
|----------------------|----------------|------|
| `test` | `test` | 测试环境自动/半自动发布 |
| `prod` | `prod` | 生产，默认人工确认 |

本地开发用 `dev`（`application-dev.yml`），**不**由 Jenkins 发布。后端配置三份：`application.yml` + `application-dev.yml` + `application-test.yml`。详见 `CLAUDE.md` §6.10；组件 IP/端口/账密见 `docs/test-env.md`。

---

## 7. 流水线登记表

新流水线创建后在此登记（避免影子 Job）：

| Job 名 | 系统 | 目标 | Jenkinsfile 路径 | 环境 | 负责人 |
|--------|------|------|------------------|------|--------|
| （示例）`demo-admin-cd` | demo | admin 前端 | `frontend/demo/Jenkinsfile` | test/prod | — |

---

## 8. 明确不做 / 演进

- **当前不引入 K8s**：默认主机或 Docker Compose/`docker run`；若未来上 K8s，另开变更重写「发布」阶段（镜像与清单），**不**在未归档前改本契约。
- 不在 Jenkins 管数据库变更脚本的自动执行（migration/seed 属不可逆操作，需单独流程与确认）。
- 不把生产密钥放入多分支扫描可见的文件。

---

## 9. 维护

- 本文是 CI/CD **操作指导**，不是业务规范；业务契约见根目录 `CLAUDE.md`。
- 阶段命令若与 `CLAUDE.md` §3.2 不一致，以 §3.2 为准并回写本文。
- 部署目标机器、端口等易变信息放环境配置或运维清单，不写死在本文正文（登记表可写 Job 名）。
