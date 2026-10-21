param([switch]$SkipInstall)
$ErrorActionPreference = 'Stop'
$agentRoot = Split-Path $PSScriptRoot -Parent
$uiRoot = Join-Path $agentRoot 'workstation'
$taskRoot = Split-Path (Split-Path $agentRoot -Parent) -Parent
$backupRoot = Join-Path $taskRoot '.codex-tmp/workstation-build-backups'
if (Test-Path -LiteralPath (Join-Path $uiRoot 'dist')) {
    New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
    Move-Item -LiteralPath (Join-Path $uiRoot 'dist') -Destination (Join-Path $backupRoot ([guid]::NewGuid().ToString('N')))
}
Push-Location -LiteralPath $uiRoot
try {
    if (-not $SkipInstall) {
        & npm.cmd ci --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw '工作台依赖安装失败' }
    }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw '工作台类型检查或构建失败' }
    $webRoot = Join-Path $agentRoot 'src/Xsy.Scale.Agent/wwwroot'
    # 旧生成资源保留在任务临时目录，避免混入新包，也不递归删除文件。
    if (Test-Path -LiteralPath $webRoot) {
        New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
        Move-Item -LiteralPath $webRoot -Destination (Join-Path $backupRoot ([guid]::NewGuid().ToString('N')))
    }
    New-Item -ItemType Directory -Path $webRoot -Force | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $uiRoot 'dist') -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $webRoot -Recurse
    }
    Write-Output $webRoot
} finally { Pop-Location }
