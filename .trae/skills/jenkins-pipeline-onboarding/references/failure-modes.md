# 失败排查（先取证，再改）

> 铁律（CLAUDE.md §7.9）：**先拿到具体报错**——完整堆栈/错误消息、HTTP 状态与响应体、构建号与阶段名、相关日志——再定位根因，然后做**最小**修改。禁止凭猜测批量试错。

## 0. 先做这三件事

1. 取哪个阶段失败：`<job>/<build>/consoleText`，或 Jenkins UI 的 stage 视图。
2. 取版本与分支：构建参数 `BRANCH`、`git rev-parse --short=12 HEAD`。
3. 判断是**环境**问题还是**产物**问题：同一提交在本地容器里能否复现（`docker run` 命令在预检产物里可直接抄）。

## 1. 按现象定位

| 现象 | 常见根因 | 定位方法 | 修法 |
|---|---|---|---|
| 连不上 Jenkins（超时） | 测试环境未开机/网络不通/VPN 未连 | `python deploy/jenkins/preflight.py --online` | 让预检报「不可达」并跳过，**不要**把地址改成本机假地址 |
| `HTTP 403` + crumb | 未带 crumb，或 crumb 与 POST 不是同一 JSESSIONID | 看 `jenkins_api.py` 是否复用了同一 opener | 保持 Cookie 会话共享 |
| `HTTP 401` | 账号密码错 / 用了用户名+密码但该账号要 API Token | `curl -u user:token $JENKINS_URL/api/json` | 换 API Token |
| Checkout 失败 | 分支名不存在 / `github-token` 凭据过期 | 列出远端分支核对 `BRANCH` | 换分支或更新凭据 |
| Quality 阶段 `npm ci` 失败 | 无 `package-lock.json`，或 lock 与 `package.json` 不一致 | 本地 `npm ci` 复现 | 提交 lock 文件；不要改成 `npm install` 糊过去 |
| `npm run type-check` 失败 | 类型错误（真问题） | 本地 `npm run type-check` | 修代码；**禁止**把 type-check 从流水线里删掉 |
| Test 阶段 Maven 失败 | 模块名写错（`-pl` 找不到）、依赖未安装 | `docker run ... mvn -pl <module> -am test` 手工跑 | 核对 `module` 是否**启动模块** |
| Image 阶段 `docker build` 失败：`no such file or directory: Dockerfile` | 构建上下文目录没有 Dockerfile（部署单元选错） | `ls backend/<sys>/<module>/Dockerfile` | 改 `module` 为真正有 Dockerfile 的启动模块（见 `input-contract.md` §3） |
| Image 阶段 `COPY target/*.jar` 为空 | package 阶段产物没落在该模块 `target/`，或 `finalName` 变了 | 看模块 `pom.xml` 的 `finalName`，看 `target/` 实际文件名 | 对齐 `finalName`；或让 CI 先 `mvn package` |
| Deploy 阶段 `docker login` 失败 | `registry-push-test` 凭据缺失/权限不足 | Jenkins → Manage Credentials 查 ID 是否存在 | 人工创建凭据（见 `credentials.md`） |
| Deploy 阶段 SSH 失败：`Permission denied (publickey)` | `ssh-deploy-test` 私钥与目标机 `authorized_keys` 不匹配 | `ssh -i <key> root@<host>` 手工试 | 换私钥；不要关 `StrictHostKeyChecking` 以外还去关别的 |
| Deploy 阶段 `port is already allocated` | 目标机端口被旧容器/别的进程占用 | 目标机 `docker ps -a`、`netstat -ano` | 停旧容器；端口冲突要回 `port-registry.md` 重新规划 |
| Smoke 阶段 `curl` 失败 | 应用真没起来 / 健康路径不对 / 启动慢 | 目标机 `docker logs <container>` | 看真实启动日志；必要时加 `--retry`，**禁止**把 Smoke 阶段删掉 |
| 健康检查一直 404 | 前端 nginx 没有 `/healthz` 路由 | 看 `frontend/<system>/nginx.conf` | 补 `location = /healthz` |
| 后端容器起来但 503（网关侧） | 网关 `lb://` 缺 LoadBalancer，或 Nacos 未注册 | 网关日志、Nacos 实例列表 | 见 CLAUDE.md §2.1（属代码问题，不属流水线问题） |
| 构建很慢 | `.m2`/npm 缓存没挂上 | 看是否用了 `-v "$WORKSPACE/.m2-cache"` | 保持缓存挂载；不要改成每次全量下载 |
| 宿主机报 `docker: command not found` | Agent 上没装 Docker | 该节点 shell 执行 `docker version` | 换到有 Docker 的节点（本项目不绑 label，先确认默认节点） |

## 2. 预检报错的修法

| 预检信息 | 含义 | 动作 |
|---|---|---|
| `可部署单元目录不存在` | `system`/`module` 拼错或模块还没建 | 核对目录；模块不存在就别建流水线 |
| `下没有 Dockerfile` | 构建上下文不能 docker build | 补 Dockerfile，或换 `module` |
| `package.json 缺少脚本` | 质量门命令不存在 | 在脚手架里补脚本（CLAUDE.md §3.2 明确要求），不要从流水线里删命令 |
| `端口 X 未在 docs/port-registry.md 登记` | 端口没走登记流程 | 先登记端口，再建流水线 |
| `端口 X 与 Y 冲突` | 两条流水线抢同一宿主机端口 | 回 `port-registry.md` 重新规划，不许改流水线绕过 |
| `疑似明文敏感信息` | 文件里出现 password/token/私钥 | 删掉，改走凭据 ID |
| `目录内有 Dockerfile 但 jobs.yaml 未登记流水线` | 有可部署单元却没有 CD（覆盖缺口） | 询问用户是否要补；确认不需要就说明原因 |

## 3. 绝不能做的「修复」

- `|| true`、`try/catch` 吞掉失败、把失败阶段注释掉。
- 把 `SKIP_TESTS` 默认值改成 `true`。
- 为了让预检通过，把 `preflight.py` 的检查项删掉或降级成 WARN。
- 直接把 `master` 上的 Job 脚本手改后不回写 `jobs.yaml`（下次同步会被覆盖，且形成影子配置）。
- 不看日志就重跑几次「碰运气」。

## 4. 取证材料要贴什么

交付/上报时给出：`job` 名 + 构建号 + 失败阶段 + **原文报错片段** + 已排除的假设 + 结论。缺这些就别说是「环境问题」。
