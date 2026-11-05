<#
.SYNOPSIS
    从 assets/templates 实例化一套业务设计产物骨架到 docs/<domain>-design/。

.DESCRIPTION
    配合 .claude/skills/business-design-docs/SKILL.md（Step 0/§3 末注）使用。
    只建骨架与替换占位符（{{DOMAIN}} / {{DOMAIN_CN}} / {{SYSTEM}} / {{DB}} / {{DATE}}）；
    未提供的占位符原样保留，提示人工补齐。不会覆盖已有文件（除非 -Force）。

    用法：
        powershell -NoProfile -File .claude/skills/business-design-docs/scripts/new-domain-design.ps1 `
            -Domain knowledge-base -DomainCn "知识库" -System blog -Database blog_db
        ... -DryRun       # 只打印将要做的动作
        ... -Force        # 允许覆盖已存在的同名文件
        ... -RootMarker X # 仓库根标记文件名（默认 CLAUDE.md，属项目绑定）

.NOTES
    约定：产物骨架在 assets/templates/；章节写作要领在 references/artifact-set.md；
    骨架只解决「起形」，内容裁决与质量门走 SKILL.md 流程。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Domain,
    [Parameter(Mandatory = $true)][string]$DomainCn,
    [string]$System,
    [string]$Database,
    [string]$DirName,
    [string]$RootMarker = 'CLAUDE.md',
    [switch]$DryRun,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

# ---- 校验 ----
if ($Domain -notmatch '^[a-z][a-z0-9]*(-[a-z0-9]+)*$') {
    Write-Error "域名须为 kebab-case（如 knowledge-base）：$Domain"
    exit 2
}
$skillRoot = Split-Path -Parent $PSScriptRoot
$templateDir = Join-Path $skillRoot 'assets/templates'
if (-not (Test-Path $templateDir)) {
    Write-Error "找不到模板目录：$templateDir"
    exit 2
}

# 向上找仓库根（根标记文件；默认值属项目绑定，见 references/project-bindings.md §7）
$repo = $PSScriptRoot
while ($repo -and -not (Test-Path (Join-Path $repo $RootMarker))) {
    $repo = Split-Path -Parent $repo
}
if (-not $repo) {
    Write-Error "向上未找到 $RootMarker，无法定位仓库根（可用 -RootMarker 指定契约文件名）"
    exit 2
}

if (-not $DirName) { $DirName = "$Domain-design" }
if ($DirName -notmatch '^[a-z0-9][a-z0-9-]*$') {
    Write-Error "目录名须为小写连字符：$DirName"
    exit 2
}
$targetDir = Join-Path $repo "docs/$DirName"
$today = Get-Date -Format 'yyyy-MM-dd'

# ---- 模板 → 目标文件映射 ----
$mapping = [ordered]@{
    'README.md.tmpl'       = 'README.md'
    'domain-design.md.tmpl' = "$Domain-design.md"
    'table-design.md.tmpl' = 'table-design.md'
    'api-design.md.tmpl'   = 'api-design.md'
    'er-diagram.html.tmpl' = "$Domain-er-diagram.html"
    'prototype.html.tmpl'  = "$Domain-prototype.html"
    'dataflow.html.tmpl'   = "$Domain-dataflow.html"
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
$created = @()
$skipped = @()

if (-not $DryRun) {
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
}

foreach ($tmpl in $mapping.Keys) {
    $tmplPath = Join-Path $templateDir $tmpl
    if (-not (Test-Path $tmplPath)) {
        Write-Output "[跳过] 模板不存在：$tmpl"
        continue
    }
    $outName = $mapping[$tmpl]
    $outPath = Join-Path $targetDir $outName

    if ((Test-Path $outPath) -and -not $Force) {
        $skipped += $outName
        Write-Output "[已存在] docs/$DirName/$outName（-Force 才覆盖）"
        continue
    }

    $text = [System.IO.File]::ReadAllText($tmplPath, $utf8)
    $text = $text.Replace('{{DOMAIN}}', $Domain).Replace('{{DOMAIN_CN}}', $DomainCn).Replace('{{DATE}}', $today)
    if ($System) { $text = $text.Replace('{{SYSTEM}}', $System) }
    if ($Database) { $text = $text.Replace('{{DB}}', $Database) }

    if ($DryRun) {
        Write-Output "[DryRun] 将生成 docs/$DirName/$outName"
    } else {
        [System.IO.File]::WriteAllText($outPath, $text, $utf8)
        Write-Output "[生成] docs/$DirName/$outName"
    }
    $created += $outName

    # 提示未替换的占位符
    $left = [regex]::Matches($text, '\{\{[A-Z_]+\}\}') | ForEach-Object { $_.Value } | Sort-Object -Unique
    if ($left) {
        Write-Output "        待人工补齐占位符：$($left -join ' ')"
    }
}

Write-Output ''
Write-Output "目标目录：docs/$DirName"
Write-Output "生成 $($created.Count) 个 / 跳过 $($skipped.Count) 个。"
Write-Output '下一步：按 references/artifact-set.md 填充内容；裁决与质量门走 SKILL.md 流程。'
exit 0
