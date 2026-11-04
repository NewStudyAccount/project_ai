<#
.SYNOPSIS
    把 .claude/skills 同步到其它 AI 工具的 skills 目录，消除多份手抄漂移。

.DESCRIPTION
    唯一源：.claude/skills
    镜像目标：.trae/skills、.codex/skills、.agents/skills

    用法：
        powershell -NoProfile -File .agents/sync-skills.ps1            # 同步（覆盖镜像，多余目录会被删除）
        powershell -NoProfile -File .agents/sync-skills.ps1 -Check     # 只比对，不写；有漂移退出码 1
        powershell -NoProfile -File .agents/sync-skills.ps1 -DryRun    # 只打印将要做的动作

    约定（与 CLAUDE.md §4 对齐）：技能只在 .claude/skills 里编辑，其它目录都是生成的。
#>
[CmdletBinding()]
param(
    [switch]$Check,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repo '.claude/skills'
$targets = @(
    (Join-Path $repo '.trae/skills'),
    (Join-Path $repo '.codex/skills'),
    (Join-Path $repo '.agents/skills')
)

if (-not (Test-Path $source)) {
    Write-Error "找不到技能源目录：$source"
    exit 2
}

# 只比内容，不比时间戳；忽略编辑器/系统垃圾文件
$ignore = @('.DS_Store', 'Thumbs.db', '.gitkeep')

function Get-SkillFiles {
    param([string]$Root)
    if (-not (Test-Path $Root)) { return @() }
    Get-ChildItem -Path $Root -Recurse -File -Force |
        Where-Object { $ignore -notcontains $_.Name } |
        ForEach-Object { [pscustomobject]@{ Rel = $_.FullName.Substring($Root.Length).TrimStart([char[]]@('\','/')); Hash = (Get-FileHash -Algorithm SHA256 $_.FullName).Hash } }
}

$sourceFiles = Get-SkillFiles -Root $source
$sourceMap = @{}
foreach ($f in $sourceFiles) { $sourceMap[$f.Rel] = $f.Hash }

$drift = 0
foreach ($target in $targets) {
    $targetFiles = Get-SkillFiles -Root $target
    $targetMap = @{}
    foreach ($f in $targetFiles) { $targetMap[$f.Rel] = $f.Hash }

    $missing = @($sourceMap.Keys | Where-Object { -not $targetMap.ContainsKey($_) })
    $differ = @($sourceMap.Keys | Where-Object { $targetMap.ContainsKey($_) -and $targetMap[$_] -ne $sourceMap[$_] })
    $extra = @($targetMap.Keys | Where-Object { -not $sourceMap.ContainsKey($_) })

    $rel = $target.Substring($repo.Length).TrimStart([char[]]@('\','/'))
    if ($missing.Count -eq 0 -and $differ.Count -eq 0 -and $extra.Count -eq 0) {
        Write-Output "[OK]   $rel 与源一致（$($sourceMap.Count) 个文件）"
        continue
    }

    $drift++
    Write-Output "[DRIFT] $rel : 缺 $($missing.Count) / 不同 $($differ.Count) / 多 $($extra.Count)"
    foreach ($m in $missing) { Write-Output "        缺失 $m" }
    foreach ($d in $differ) { Write-Output "        内容不同 $d" }
    foreach ($e in $extra) { Write-Output "        多余 $e" }

    if ($Check) { continue }

    if ($DryRun) {
        Write-Output "        (DryRun) 将镜像 $source -> $target"
        continue
    }

    # 镜像：先清空目标技能目录（保留目录本身），再整体复制
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    Get-ChildItem -Path $target -Force | Remove-Item -Recurse -Force
    Copy-Item -Path (Join-Path $source '*') -Destination $target -Recurse -Force
    Write-Output "        已镜像"
}

if ($Check) {
    if ($drift -gt 0) {
        Write-Output "`n有 $drift 个镜像目录与 .claude/skills 不一致，请运行： powershell -NoProfile -File .agents/sync-skills.ps1"
        exit 1
    }
    Write-Output "`n全部镜像一致"
    exit 0
}

Write-Output "`n完成。技能只在 .claude/skills 里编辑，改完跑本脚本。"
exit 0
