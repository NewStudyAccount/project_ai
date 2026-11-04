# 凭据与访问（只写 ID，不写值）

## 1. 原则（CLAUDE.md §6.7）

- **禁止入仓**：生产密码、私钥、Token、连接串。
- **允许入仓**：开发/测试账号密码 → `docs/test-env.md`、`application-local.yml` / `application-test.yml`。
- 仓库内的流水线工具**只引用 Credentials ID**，脚本自身凭据从环境变量或 gitignored 文件读。

## 2. 三类凭据

| 用途 | Credential ID | 类型 | 谁创建 |
|---|---|---|---|
| Git 拉取 | `github-token` | Username with password 或 API Token | 已有 |
| 部署机 SSH | `ssh-deploy-test` | SSH Username with private key | 人工（运维） |
| 镜像仓库推送 | `registry-push-test` | Username with password | 人工（运维） |
| 生产 SSH（未来） | `ssh-deploy-prod` | SSH Username with private key | 人工（运维） |
| 生产镜像推送（未来） | `registry-push-prod` | Username with password | 人工（运维） |

命名规则：`{用途}-{目标}`；ID 一旦登记**不要改名**（改名 = 所有 Job 失效）。

## 3. Jenkins 管理台访问（测试环境）

- 地址与账号：**只在 `docs/test-env.md`**（§1 组件表 / §4 Jenkins 发布 / §6.1 账密）。本 skill 与 `deploy/jenkins/` 下任何文件都不复制密码。
- 生产 Jenkins 账号**禁止**写入本仓库。

## 4. 本地跑脚本时怎么给凭据（三选一）

推荐 API Token（不是登录密码）：

```powershell
$env:JENKINS_URL   = 'http://192.168.99.100:8080'
$env:JENKINS_USER  = 'qjj'
$env:JENKINS_TOKEN = '<Jenkins → 用户 → Configure → API Token>'
python deploy/jenkins/create-jobs.py --dry-run
python deploy/jenkins/create-jobs.py
```

或写本地文件 `deploy/jenkins/.jenkins.env`（**已 gitignore**，每行 `KEY=VALUE`）：

```text
JENKINS_URL=http://192.168.99.100:8080
JENKINS_USER=qjj
JENKINS_TOKEN=<token>
```

都没有时脚本会明确报「缺少 Jenkins 凭据」并指出 `docs/test-env.md`，**不会**回退到任何内置密码。

## 5. 凭据是否已就绪怎么判断

```bash
python deploy/jenkins/preflight.py --online
```

- 输出 `凭据 ID 不存在于 Jenkins：ssh_cred_id=...` → 让人去建，AI 无权限建全局凭据。
- 输出 `无法列出 Jenkins 凭据（权限不足？）` → 只代表查不到，**不代表不存在**，交付说明里要写「未确认」。

## 6. 交付时怎么描述凭据状态

```
凭据：github-token 已存在；ssh-deploy-test / registry-push-test 需人工在 Jenkins 创建（AI 无权限）
```

不要写「已配置好」——除非 `--online` 明确验证过。
