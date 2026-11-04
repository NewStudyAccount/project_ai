# 验收清单（新/改流水线必过）

> 分两段：**可脚本化**（AI 必须先跑完并贴结果）+ **必须人工**（真实发布与回滚）。
> 「未验证」要明确写出来，不能因为跑不了就当作通过。

## 1. 可脚本化（AI 负责，全部必须为通过）

```bash
python deploy/jenkins/preflight.py              # 期望：ERROR 0
python deploy/jenkins/create-jobs.py --dry-run  # 期望：n/n 通过静态校验
python deploy/jenkins/preflight.py --online     # 期望：凭据 ID 全部存在；不可达要写明
```

- [ ] 预检 ERROR 为 0（WARN 需逐条解释，不许忽略）
- [ ] 新增/修改的 job 在 `jobs.yaml` 中只出现一次，端口不与他人冲突
- [ ] dry-run 产物中：有 `BRANCH`、无 `DEPLOY_ENV`、无 `spring.profiles`
- [ ] 前端产物含 `npm ci` + `lint` + `type-check` + `build`；后端含 `mvn -pl <module> -am test` + `package`
- [ ] 产物含 `docker build`、`docker push`、目标机 `docker run`、健康检查 `curl`
- [ ] 产物与 `jobs.yaml`/模板中无密码、Token、私钥（`grep -Ei 'password|token|BEGIN .*PRIVATE'`）
- [ ] 未改动与本次任务无关的 job（`git diff deploy/jenkins/jobs.yaml` 只多/改自己那条）
- [ ] `git status` 里没有 `.jenkins.env`、`*.config.xml`、渲染产物

## 2. 必须人工（在 Jenkins 上做，AI 不代劳）

- [ ] Job 首次构建：`BRANCH` 选一个真实分支，五阶段全绿（Checkout → 质量门/测试打包 → Image → Deploy → Smoke）
- [ ] 质量门有效：**故意**让 lint 或 test 失败一次，确认构建是 FAILURE 而不是 SUCCESS
- [ ] 产物落地：目标机 `docker ps` 看到新容器，`docker images` 看到 `{sha}` 与 `latest` 两个 tag
- [ ] 健康检查真实通过：目标机 `curl -fsS <health_url>` 返回 2xx/`UP`
- [ ] 端口可用：宿主机 `-p <port>` 映射正确，外部经 Nginx 能访问（前端）
- [ ] 后端容器启动日志中的 profile 与 `application.yml` 的 `spring.profiles.active` 一致（流水线**没有**注入）
- [ ] 回滚演练：用上一个 `{sha}` tag 重新 `docker run` 能回滚成功
- [ ] 凭据核验：`ssh-deploy-test` / `registry-push-test` 在 Jenkins 里真实存在且有权限
- [ ] 生产分支/tag 发布有审批（如已接生产）

## 3. 交付说明模板（把这份填完再回话）

```
流水线：<job>
变更：  新增 / 修改（改了什么，一行）
预检：  ERROR 0 / WARN <n>（逐条说明）
渲染：  dry-run n/n
应用：  created / updated / 未执行（原因）
人工验收：通过 <k> 项 / 未验 <m> 项（列出未验项与原因）
未验证项：<例如 Jenkins 不可达、凭据待创建>
回滚：  <备份路径或「删除该 Job」>
```

## 4. 一票否决

出现下列任一情况，**不允许**说「已完成」：

- 预检有 ERROR 却强行应用；
- 质量门被删、被注释、被 `|| true`；
- 明文密码/Token 进了仓库文件；
- dry-run 产物没看就应用；
- 把人工验收项当成「自动完成」。
