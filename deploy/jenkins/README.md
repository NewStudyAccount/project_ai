# Jenkins 流水线交付物

> 按 `docs/jenkins-pipeline-guide.md` §3.3 / §4 交付。
> **约定：Jenkinsfile 不入仓**——Pipeline 脚本内嵌在 Jenkins Job 中；仓库只保留 Dockerfile / 部署配置 / 本目录创建脚本。
> 密钥只绑 Jenkins Credentials，禁止写入本目录或任何仓库文件。

## 1. 已创建 Job（Jenkins `http://192.168.99.100:8080/`）

| Job 名 | 系统 | 目标 | kind | 构建 | 宿主机端口 |
|--------|------|------|------|------|-----------|
| `user-admin-cd` | user-admin | 前端 | frontend | Docker(node:24) | 5173 |
| `auth-admin-cd` | auth-admin | 前端 | frontend | Docker(node:24) | 5175 |
| `auth-portal-cd` | auth-portal | 前端 | frontend | Docker(node:24) | 5174 |
| `blog-admin-cd` | blog-admin | 前端 | frontend | Docker(node:24) | 5176 |
| `auth-service-cd` | auth | auth-service | backend | Docker(maven:jdk21) | 9080 |
| `blog-gateway-cd` | blog | blog-gateway | gateway | Docker(maven:jdk21) | 9100 |
| `blog-service-cd` | blog | blog-service | backend | Docker(maven:jdk21) | 19100 |
| `user-gateway-cd` | user | user-gateway | gateway | Docker(maven:jdk21) | 8173 |
| `user-service-cd` | user | user-service | backend | Docker(maven:jdk21) | 18173 |

创建/同步脚本：[`create-jobs.py`](create-jobs.py)（幂等；`python deploy/jenkins/create-jobs.py`）。

## 2. 公共参数（测试环境）

| 项 | 值 |
|----|-----|
| 源码仓库 | `https://github.com/NewStudyAccount/project_ai.git` |
| 默认分支 | `feature-base-design` |
| `GIT_CRED_ID` | `github-token`（Jenkins 已有） |
| `SSH_CRED_ID` | `ssh-deploy-test` |
| `REGISTRY_CRED_ID` | `registry-push-test` |
| `DEPLOY_HOST` / `DEPLOY_USER` | `192.168.99.100` / `root` |
| 镜像仓库 | `192.168.99.100:5000` |
| **触发参数** | **`BRANCH`**（默认 `feature-base-design`，选分支） |
| **后端环境** | **代码 `application.yml` → `spring.profiles.active`**（如 `local`/`test`）；流水线**不**传 `--spring.profiles.active` |
| **前端环境** | **构建期 `VITE_*`**（应用目录 `.env` / `.env.production`，或 `docker build --build-arg`）；API 为同源 `/api`，不靠运行时环境变量 |
| 部署形态 | `docker`（网络 `project-ai`） |
| owner | `qjj` |

## 3. 仍须人工在 Jenkins 完成

1. **创建凭据**（若尚无）：
   - `ssh-deploy-test`：SSH Username with private key（用户 `root`，部署机私钥）
   - `registry-push-test`：Username/Password（`192.168.99.100:5000` 推送账号）
2. **Agent**：流水线使用 **`agent any`** + **全 Docker 构建**（宿主机**无需** JDK/Maven/Node）。
   - 节点只要：**Docker**（可拉 `node:24-alpine`、`maven:3.9-eclipse-temurin-21` 及目标镜像）
   - 前端质量门：`docker run node:24-alpine` 内 `npm ci/lint/type-check/build`
   - 后端测试/打包：`docker run maven:3.9-eclipse-temurin-21` 内 `mvn`（`.m2` 缓存挂 workspace）
   - 若以后要拆构建机，再把脚本改回 `agent { label '...' }` 并配 label。
3. **插件**：Pipeline、Credentials Binding、Git、SSH Credentials（用 `sshUserPrivateKey` + `ssh -i`，**无需** SSH Agent 插件）。
4. **首次冒烟**：对 `user-admin-cd` 等执行构建（参数选 `BRANCH`），确认 checkout / 质量门 / 镜像 / 发布 / 健康检查全绿。
5. （可选）Webhook / 分支触发；分支名 `prod` / `prod-*` 必须人工确认后才会发布。

## 4. 流水线阶段

```
Checkout(所选 BRANCH) → 质量门/测试打包 → 构建镜像 → 发布 → 健康检查
```

- 前端质量门：`npm ci` → `lint` → `type-check` → `build`（当前包无 `test` 脚本）
- 后端质量门：`mvn -pl <module> -am test` → `package -DskipTests`
- 测试失败必须使 Job 失败；禁止 `|| true` 吞错。

### 环境变量落在哪

| 层 | 机制 | 流水线是否介入 |
|----|------|----------------|
| **后端 profile** | 各模块 `application.yml` → `spring.profiles.active`（当前多为 `local`；发测试请改成 `test` 或按 `CLAUDE.md` §6.10 三份配置约定） | **否**（`docker run` 只起 jar，不传 `--spring.profiles.active`） |
| **前端构建配置** | Vite 构建期 `import.meta.env.VITE_*`（如 `VITE_OIDC_ISSUER`）；来源：应用目录 `.env` / `.env.production`，或 Docker `--build-arg` | **否**（只 `npm run build`） |
| **前端 API** | 同源相对路径 `/api/v1`，由接入层 Nginx 反代到网关 | 不需要环境变量 |

前端若要区分环境：在对应应用目录放 `.env.production`（提交或 CI 注入），或：

```bash
docker build --build-arg VITE_OIDC_ISSUER=https://... frontend/user-admin
```

（需 Dockerfile 接 ARG/ENV；当前镜像未强制，缺省走代码内 fallback。）

## 5. 镜像命名与回滚

```text
{REGISTRY}/project_ai/{target}:{git-short-sha}
{REGISTRY}/project_ai/{target}:latest
```

```bash
# 用上一镜像 tag 重新 run
ssh root@192.168.99.100 "docker rm -f <container> && docker run -d --name <container> --restart unless-stopped --network project-ai -p <port>:<port|80> <image>:<prev-sha> ..."
```

prod 回滚须人工审批；建议保留至少 5 个历史镜像 tag。
