# 输入契约（建流水线前必须填满）

> 字段的**取值来源**写死在这里；**具体值**一律去仓库真实文件里取，不许猜。
> 填完写进 `deploy/jenkins/jobs.yaml`；`defaults:` 里已有的字段（分支、仓库、主机、凭据 ID、镜像仓库）**不要**在单条 job 里重复。

## 1. 每条形如必填

| 字段 | 必填 | 取值怎么来 | 校验规则 |
|---|---|---|---|
| `name` | 是 | `{target}-cd` | 全局唯一；小写连字符；建 CD 用 `-cd` 后缀 |
| `kind` | 是 | `frontend` \| `backend` | 只有这两种；网关也填 `backend` |
| `system` | 是 | 目录名，`frontend/<system>` 或 `backend/<system>` | 必须真实存在；与 `port-registry.md` 的组件名一致 |
| `target` | 是 | 镜像名/容器名/Job 名主干 | 小写连字符；不得与其它 job 的 `target` 相同 |
| `module` | backend 必填 | `backend/<system>/` 下**有 Dockerfile 且是启动模块**的那个目录 | 该目录必须同时有 `pom.xml` + `Dockerfile`；省略时等于 `target` |
| `port` | 是 | **宿主机发布端口**（`-p` 左侧），取 `docs/port-registry.md` 已登记的值 | 全局唯一（唯一冲突面）；未登记 → 先去登记再建流水线 |
| `container_port` | backend 是（frontend 可省，默认 80） | **容器内监听端口**（`-p` 右侧）= 代码事实：后端 `application.yml` 的 `server.port`，前端 nginx `listen` | 与 `port` 解耦；容器间可重复；preflight 会与代码取证比对，不一致报 ERROR |
| `health_path` | 是 | 前端 `/healthz`；后端 `/actuator/health` | 必须以 `/` 开头；容器内可达 |
| `owner` | 是 | 负责人 | 默认取 `defaults.owner` |

## 2. 前端额外要求（预检会卡）

`frontend/<system>/package.json` 的 `scripts` 必须有：`lint`、`type-check`、`build`。
（本仓库现状：均无 `test` 脚本，质量门不跑单测——这是既有事实，不是遗漏。）

`frontend/<system>/Dockerfile` 必须存在。缺失就**先补 Dockerfile**，不要先用「跳过 Image 阶段」绕过。

## 3. 后端额外要求

- `pom.xml` 必须存在；
- 该模块必须是**可执行 jar 的启动模块**（有 `spring-boot-maven-plugin` 与 `<finalName>`），业务库模块不能当部署单元；
- 判断方法：`grep -l "spring-boot-maven-plugin" backend/<system>/*/pom.xml`，再看哪个目录有 `Dockerfile`。两者重叠的那个才是部署单元。

> 反例（已发生的真实缺陷）：`blog-service` 是业务模块（无 Dockerfile、无 Boot 插件），部署单元其实是 `blog-server`（`finalName=blog-service`）。所以那条 job 的 `target` 是 `blog-service`（镜像名/服务名不变），`module` 是 `blog-server`（构建上下文）。

## 4. 派生字段（不用手填，渲染时算）

| 派生值 | 规则 |
|---|---|
| `image` | `{defaults.registry}/{defaults.image_prefix}/{target}` |
| `container` | `= target` |
| `health_url` | `http://127.0.0.1:{port}{health_path}`（`port` = 宿主机端口） |
| `description` | 前端 `{target} frontend docker static`；后端 `{target} docker jar` |
| `unit_dir` | 前端 `frontend/{system}`；后端 `backend/{system}/{module}` |

## 5. 凭据字段（只写 ID）

`defaults` 里已固定：`git_cred_id`、`ssh_cred_id`、`registry_cred_id`。
**任何 job 都不需要也不会写凭据值。** 值在哪、谁去建见 `credentials.md`。

## 6. 填写前的三问（拿不准就停下来问人）

1. 这个服务**真的要独立发布**吗？（只被依赖的 `common`/`framework`/业务库不建 CD）
2. 端口在 `port-registry.md` 登记了吗？（没有就先登记）
3. 部署单元确定是**启动模块**吗？（有 Dockerfile + Boot 插件的那个）

## 7. 提交前自检

- [ ] 我没有编造任何主机、端口、凭据 ID、镜像仓库
- [ ] `jobs.yaml` 里没有密码/Token/私钥
- [ ] 没把已有 job 的字段顺手改掉（只加自己这条）
- [ ] `python deploy/jenkins/preflight.py` 为 PASS
