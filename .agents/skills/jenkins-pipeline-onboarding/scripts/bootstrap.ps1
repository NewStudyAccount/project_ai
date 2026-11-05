<#
.SYNOPSIS
    Jenkins 流水线技能包的自举工具：把本 skill 自带的机器安装到目标项目、或校验两边是否一致。

.DESCRIPTION
    三种模式：
        New      在目标项目里安装 deploy/jenkins（脚本 + 模板 + jobs.yaml 模板）
        Check    比对「技能快照」与目标项目里的实现是否一致（防止两边漂移）
        Export   把某个可用项目里的实现回收成技能快照（升级技能包用）

    快照位置：<skill>/assets/deploy-jenkins
    安装位置：<target>/deploy/jenkins

    用法：
        powershell -NoProfile -File <skill>\scripts\bootstrap.ps1 -Target D:\newproj
        powershell -NoProfile -File <skill>\scripts\bootstrap.ps1 -Target D:\newproj -DryRun
        powershell -NoProfile -File <skill>\scripts\bootstrap.ps1 -Target D:\newproj -Mode Check
        powershell -NoProfile -File <skill>\scripts\bootstrap.ps1 -Target D:\myproj -Mode Export

    约定：
        - jobs.yaml 是项目自己的数据，New 模式永不覆盖已存在的它；
        - Check/Export 只比对代码与模板（*.py、templates/*.tmpl），不碰 jobs.yaml；
        - 本脚本不联网、不写 Jenkins、不写入任何凭据。
#>
[CmdletBinding()]
param(
    [string]$Target = '',
    [ValidateSet('New', 'Check', 'Export')]
    [string]$Mode = 'New',
    [switch]$Force,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$skillRoot = Split-Path -Parent $PSScriptRoot
$snapshot = Join-Path $skillRoot 'assets/deploy-jenkins'
if (-not (Test-Path $snapshot)) {
    Write-Error "找不到技能快照：$snapshot"
    exit 2
}

# 默认目标 = 本 skill 所在的仓库（.claude/skills/<name> 往上三层）
if (-not $Target) {
    $Target = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $skillRoot))
}
if (-not (Test-Path $Target)) {
    Write-Error "目标项目目录不存在：$Target"
    exit 2
}
$Target = (Resolve-Path $Target).Path
$dest = Join-Path $Target 'deploy/jenkins'

$ignore = @('.DS_Store', 'Thumbs.db')
$codeFiles = @('preflight.py', 'render.py', 'create-jobs.py', 'jenkins_api.py')
$templateFiles = @('frontend.groovy.tmpl', 'backend.groovy.tmpl', 'job.xml.tmpl')
$commandFiles = @('init.md', 'add.md', 'check.md', 'apply.md')
$commandSrc = Join-Path $skillRoot 'assets/commands'
$commandDst = Join-Path $Target '.claude/commands/jenkins'

function Get-Hash([string]$p) {
    if (-not (Test-Path $p)) { return '' }
    (Get-FileHash -Algorithm SHA256 $p).Hash
}

# ---------------- Check ----------------
if ($Mode -eq 'Check') {
    $drift = 0
    foreach ($f in $codeFiles) {
        $a = Get-Hash (Join-Path $snapshot $f)
        $b = Get-Hash (Join-Path $dest $f)
        if ($a -eq '' -or $b -eq '') {
            Write-Output "[MISSING] $f（快照有=$([bool]$a) 目标有=$([bool]$b)）"; $drift++; continue
        }
        if ($a -ne $b) { Write-Output "[DIFF]    $f"; $drift++ }
    }
    foreach ($f in $templateFiles) {
        $a = Get-Hash (Join-Path $snapshot "templates/$f")
        $b = Get-Hash (Join-Path $dest "templates/$f")
        if ($a -eq '' -or $b -eq '') {
            Write-Output "[MISSING] templates/$f（快照有=$([bool]$a) 目标有=$([bool]$b)）"; $drift++; continue
        }
        if ($a -ne $b) { Write-Output "[DIFF]    templates/$f"; $drift++ }
    }
    if (Test-Path $commandDst) {
        foreach ($f in $commandFiles) {
            $a = Get-Hash (Join-Path $commandSrc $f)
            $b = Get-Hash (Join-Path $commandDst $f)
            if ($b -eq '') { Write-Output "[WARN]    命令未安装：/jenkins:$($f.Replace('.md',''))"; continue }
            if ($a -ne $b) { Write-Output "[DIFF]    commands/$f"; $drift++ }
        }
    }
    else {
        Write-Output "[WARN]    目标未安装 slash 命令（可选）：$commandDst"
    }
    if (-not (Test-Path (Join-Path $dest 'jobs.yaml'))) {
        Write-Output "[WARN]    目标没有 jobs.yaml（尚未初始化）"
    }
    if ($drift -eq 0) {
        Write-Output "一致：技能快照与 $dest 的代码/模板完全相同"
        exit 0
    }
    Write-Output "`n有 $drift 处不一致。若目标项目才是最新实现：加 -Mode Export 回收快照；否则重新安装。"
    exit 1
}

# ---------------- Export ----------------
if ($Mode -eq 'Export') {
    if (-not (Test-Path $dest)) {
        Write-Error "目标没有 deploy/jenkins，无法回收：$dest"
        exit 2
    }
    foreach ($f in $codeFiles) {
        $src = Join-Path $dest $f
        if (Test-Path $src) { Copy-Item $src (Join-Path $snapshot $f) -Force; Write-Output "回收 $f" }
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $snapshot 'templates') | Out-Null
    foreach ($f in $templateFiles) {
        $src = Join-Path $dest "templates/$f"
        if (Test-Path $src) { Copy-Item $src (Join-Path $snapshot "templates/$f") -Force; Write-Output "回收 templates/$f" }
    }
    Write-Output "`n快照已更新。记得同步到 .trae / .codex / .agents 镜像：powershell -NoProfile -File .agents/sync-skills.ps1"
    exit 0
}

# ---------------- New ----------------
$installed = Test-Path $dest
if ($installed -and -not $Force -and -not $DryRun) {
    Write-Output "目标已存在 $dest"
    Write-Output "  - 只想检查是否一致：-Mode Check"
    Write-Output "  - 确实要覆盖脚本与模板：加 -Force（jobs.yaml 不会被覆盖）"
    exit 3
}

$plan = @()
foreach ($f in $codeFiles) { $plan += [pscustomobject]@{ From = (Join-Path $snapshot $f); To = (Join-Path $dest $f) } }
foreach ($f in $templateFiles) { $plan += [pscustomobject]@{ From = (Join-Path $snapshot "templates/$f"); To = (Join-Path $dest "templates/$f") } }

$jobsDst = Join-Path $dest 'jobs.yaml'
$needJobs = -not (Test-Path $jobsDst)

Write-Output "技能快照：$snapshot"
Write-Output "安装目标：$dest"
Write-Output ""

if ($DryRun) {
    foreach ($p in $plan) { Write-Output ("(DryRun) {0} -> {1}" -f (Split-Path $p.From -Leaf), $p.To) }
    if ($needJobs) { Write-Output "(DryRun) jobs.example.yaml -> $jobsDst" }
    foreach ($f in $commandFiles) {
        $to = Join-Path $commandDst $f
        if (-not (Test-Path $to)) { Write-Output ("(DryRun) 安装命令 /jenkins:{0}" -f $f.Replace('.md', '')) }
    }
    Write-Output "`n(DryRun) 未写入任何文件"
    exit 0
}

New-Item -ItemType Directory -Force -Path (Join-Path $dest 'templates') | Out-Null
foreach ($p in $plan) {
    Copy-Item $p.From $p.To -Force
    Write-Output ("安装 {0}" -f (Split-Path $p.To -Leaf))
}
if ($needJobs) {
    Copy-Item (Join-Path $snapshot 'jobs.example.yaml') $jobsDst
    Write-Output "生成 jobs.yaml（含 <TODO: ...> 占位，必须逐项填真实值）"
}

# slash 命令（可选便利层）：只在缺失时安装；-Force 时覆盖
if (Test-Path $commandSrc) {
    New-Item -ItemType Directory -Force -Path $commandDst | Out-Null
    foreach ($f in $commandFiles) {
        $to = Join-Path $commandDst $f
        if ($Force -or -not (Test-Path $to)) {
            Copy-Item (Join-Path $commandSrc $f) $to -Force
            Write-Output ("安装命令 /jenkins:{0}" -f $f.Replace('.md', ''))
        }
    }
}

# 文档骨架（只在缺失时创建，绝不覆盖已有手册）
$guide = Join-Path $Target 'docs/jenkins-pipeline-guide.md'
if (-not (Test-Path $guide)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $guide) | Out-Null
    @'
# Jenkins 流水线指导（人读手册）

> 本文件由 jenkins-pipeline-onboarding 技能初始化生成，可自由改写。
> Job 清单的唯一事实源是 `deploy/jenkins/jobs.yaml`；本文只放约定与索引。

## 固定形态

| 项 | 约定 |
|---|---|
| 脚本位置 | Job 内嵌（CpsFlowDefinition），仓库内不放 Jenkinsfile |
| 同步 | `python deploy/jenkins/create-jobs.py`（幂等，先备份后覆盖） |
| 触发参数 | 只有 `BRANCH` |
| Agent | `agent any` + 全 Docker 构建（node / maven 容器） |
| 阶段 | Checkout → 质量门/测试打包 → 构建镜像 → 发布 → 健康检查 |
| 部署 | docker；镜像 tag 为 `{git-short-sha}` 与 `latest` |

## 怎么查

| 想查什么 | 去哪 |
|---|---|
| 有哪些 Job / 端口 / 健康检查 | `deploy/jenkins/jobs.yaml` |
| 缺口与可部署性风险 | `python deploy/jenkins/preflight.py` |
| 建流水线的完整步骤 | 本文档配套的技能：`jenkins-pipeline-onboarding` |
| 环境地址与账号 | `docs/test-env.md` |
| 端口登记 | `docs/port-registry.md` |

## 阶段契约

- 前端质量门：`npm ci` → `npm run lint` → `npm run type-check` → `npm run build`
- 后端质量门：`mvn -pl <module> -am test` → `mvn -pl <module> -am package -DskipTests`
- 失败必须让构建失败，禁止 `|| true`、禁止注释 stage
- 后端 profile 由 `application.yml` 决定，流水线不注入
'@ | Set-Content -Path $guide -Encoding UTF8
    Write-Output "生成 docs/jenkins-pipeline-guide.md（骨架）"
}

Write-Output @"

下一步（必须人工完成，改完才能建 Job）：
  1. 编辑 $jobsDst，把所有 <TODO: ...> 换成真实值
  2. 确认端口已在 docs/port-registry.md 登记
  3. 每个可部署单元要有 Dockerfile（缺就抄 <skill>/assets/Dockerfile.*.tmpl）
  4. 后端起模块要有 spring-boot-maven-plugin + repackage
  5. python deploy/jenkins/preflight.py          # ERROR 必须清零
  6. python deploy/jenkins/create-jobs.py --dry-run
  7. 设置 JENKINS_URL / JENKINS_USER / JENKINS_TOKEN 后再 create-jobs.py

装了 slash 命令（Claude Code）后可以少打字：
  /jenkins:init            初始化（bootstrap + 填 jobs.yaml + 预检 + dry-run）
  /jenkins:add <服务>      加一条流水线
  /jenkins:check           体检（预检 + 快照一致性 + 镜像一致性）
  /jenkins:apply [Job]     同步到 Jenkins
"@
exit 0
