<#
.SYNOPSIS
检查窗口意外关闭后，释放本检查包启动的串口进程，不停止正式工作台。
.PARAMETER OutputDirectory
检查结果父目录；默认脚本旁的 hardware-check。不存在时不做操作。
.OUTPUTS
匹配进程的停止说明；未匹配到进程时不改变现场，不删除任何数据。
#>
param([string]$OutputDirectory = (Join-Path $PSScriptRoot 'hardware-check'))
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) { Write-Host '没有本检查包的运行记录。'; exit 0 }
$count = 0
foreach ($run in Get-ChildItem -LiteralPath $OutputDirectory -Directory) {
    $ownerFile = Join-Path $run.FullName 'owned-process.json'
    if (-not (Test-Path -LiteralPath $ownerFile -PathType Leaf)) { continue }
    $owner = Get-Content -LiteralPath $ownerFile -Encoding UTF8 -Raw | ConvertFrom-Json
    $running = Get-Process -Id $owner.id -ErrorAction SilentlyContinue
    # PID 会被系统复用；必须核对完整路径和启动时间，不能只凭进程号停止。
    if ($null -ne $running -and $running.StartTime.ToUniversalTime().Ticks -eq $owner.started -and $running.Path -eq $owner.file) {
        Stop-Process -Id $running.Id
        $null = $running.WaitForExit(5000)
        $count++
    }
}
Write-Host ('已结束本检查包的进程：' + $count + '。诊断记录保留。')
