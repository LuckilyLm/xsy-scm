<#
.SYNOPSIS
只测试原始串口诊断的字节汇总和明确不存在端口，不打开办公电脑真实串口。
.PARAMETER TestDirectory
项目.codex-tmp下的独立证据目录；非空，保留拒绝案例报告。
#>
param([Parameter(Mandatory=$true)][string]$TestDirectory)
$ErrorActionPreference = 'Stop'
$probe = Join-Path (Split-Path $PSScriptRoot -Parent) 'scripts/Collect-ScaleRaw.ps1'
. $probe
$checks = 0
$empty = Get-ScaleRawSummary -Bytes @()
if ($empty.byteCount -ne 0 -or $empty.crlfCount -ne 0 -or @($empty.linePreview).Count -ne 0) { throw '空采样摘要错误' }
$checks++
foreach ($unit in @('sj','kg','X2','lb')) {
    $frame = 'sn0008.55' + $unit + "`r`n"
    $summary = Get-ScaleRawSummary -Bytes ([Text.Encoding]::ASCII.GetBytes($frame + $frame))
    if ($summary.byteCount -ne 26 -or $summary.crlfCount -ne 2 -or $summary.linePreview[0].unitSlot -cne $unit) {
        throw ('不能原样保留单位位置：' + $unit)
    }
    $checks++
}
$bad = Get-ScaleRawSummary -Bytes ([Text.Encoding]::ASCII.GetBytes("noise`nshort`r`npartial"))
if ($bad.crlfCount -ne 1 -or @($bad.linePreview).Count -ne 2 -or $bad.linePreview[0].unitSlot) { throw '异常帧被误解释为重量帧' }
$checks++
if ([IO.Ports.SerialPort]::GetPortNames() -contains 'COM4095') { throw '验证端口实际存在，拒绝运行' }
$null = & "$env:SystemRoot/System32/WindowsPowerShell/v1.0/powershell.exe" -NoProfile -ExecutionPolicy Bypass -File $probe -Port COM4095 -Seconds 3 -OutputDirectory $TestDirectory
if ($LASTEXITCODE -ne 1) { throw '不存在串口未明确拒绝' }
$resultFile = Get-ChildItem -LiteralPath $TestDirectory -Filter result.json -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$result = Get-Content -LiteralPath $resultFile.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
if ($result.portOpened -or $result.byteCount -ne 0 -or $result.result -ne 'CAPTURE_FAILED' -or $result.sendsCommands -or $result.prints) {
    throw '拒绝案例报告错误或触碰了设备'
}
$checks++
Write-Output ('PASS ' + $checks + ' raw serial diagnostic checks; no physical port opened')
