param([string]$StateDirectory = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'XSY/ScaleAgent-Exercise'), [switch]$HardwareTest)
$ErrorActionPreference = 'Stop'
if ($HardwareTest -and -not $PSBoundParameters.ContainsKey('StateDirectory')) { $StateDirectory = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'XSY/ScaleAgent-HardwareWorkflow' }
$ownerFile = Join-Path ([IO.Path]::GetFullPath($StateDirectory)) 'local-test-processes.json'
if (-not (Test-Path -LiteralPath $ownerFile)) { Write-Output '没有本脚本记录的演练实例'; exit 0 }
$owner = Get-Content -LiteralPath $ownerFile -Encoding UTF8 -Raw | ConvertFrom-Json
if ($owner.mode -ne $(if ($HardwareTest) { 'HARDWARE_TEST' } else { 'LOCAL_TEST' })) { throw '进程记录与请求模式不一致，拒绝结束' }
if ($HardwareTest) { & (Join-Path $PSScriptRoot 'Export-HardwareTest.ps1') -StateDirectory $StateDirectory }
foreach ($entry in @($owner.processes)) {
    $running = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
    if ($null -ne $running -and $running.StartTime.ToUniversalTime().Ticks -eq $entry.started -and $running.Path -eq $entry.file) {
        Stop-Process -Id $entry.id
    }
}
Write-Output '本脚本启动的演练已停止；记录和日志全部保留'
