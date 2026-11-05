# 命令索引（任何 AI 工具通用）

这 4 个文件是**普通的提示词文件**：开头一段 frontmatter，正文是「让 AI 怎么执行」的步骤。
Claude Code 会把它们当成 `/jenkins:*` 斜杠命令；其它工具不支持斜杠，但**可以读文件后照做**，效果一样。

| 文件 | Claude Code | 作用 | 会写 Jenkins |
|---|---|---|---|
| `init.md` | `/jenkins:init` | 新项目初始化（bootstrap → 问齐 5 项 → 填 jobs.yaml → 预检 → dry-run） | ❌ 只到 dry-run |
| `add.md` | `/jenkins:add <服务>` | 加一条流水线（取证 → 写 jobs.yaml → 预检 → dry-run → 可选应用） | ⚠️ 需确认 |
| `check.md` | `/jenkins:check` | 体检（预检 + 快照一致性 + 镜像一致性），只读 | ❌ |
| `apply.md` | `/jenkins:apply [Job]` | 同步到 Jenkins（预检 → 备份 → 创建/更新 → 回读） | ✅ |

## 通用句式（任何工具都能用）

把 `<技能根>` 换成本机实际路径即可：

```
读 <技能根>/assets/commands/add.md 并严格按它执行，参数：blog-portal
```

```
读 <技能根>/assets/commands/init.md 并严格按它执行
```

```
读 <技能根>/assets/commands/check.md 并严格按它执行
```

## 各工具的 `<技能根>` 在哪

| 工具 | 技能目录 | 斜杠命令 |
|---|---|---|
| Claude Code | `.claude/skills/jenkins-pipeline-onboarding` | ✅ `/jenkins:*`（来自 `.claude/commands/jenkins/`） |
| Trae | `.trae/skills/jenkins-pipeline-onboarding` | ❌ 用上面的通用句式 |
| Codex | `.codex/skills/jenkins-pipeline-onboarding` | ❌ 用上面的通用句式 |
| 通用代理镜像 | `.agents/skills/jenkins-pipeline-onboarding` | ❌ 用上面的通用句式 |
| 纯终端 | `deploy/jenkins/` 脚本 | 不需要 AI，直接跑命令 |

> 上表的目录由 `.agents/sync-skills.ps1` 保持内容一致；命令文件也随技能一起被镜像。

## 为什么要写成「路径无关」

命令正文里不写死 `.claude/skills/...`，而用 `<技能根>`。
否则把技能搬到 Trae（路径变 `.trae/skills/...`）后，命令里指向 `.claude` 的路径就不存在了。

## 不想读文件？两个更短的办法

1. **直接点名技能**：「用 jenkins-pipeline-onboarding 技能给这个项目接入 Jenkins 流水线，先 bootstrap，缺信息问我不要猜」——技能被工具加载后，语义触发即可。
2. **纯终端**：跳过 AI，直接 `python deploy/jenkins/preflight.py`、`create-jobs.py --dry-run`、`create-jobs.py --only <name>`。
