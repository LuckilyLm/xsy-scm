param([string]$Url = 'http://127.0.0.1:33600')
$ErrorActionPreference = 'Stop'
$target = [uri]$Url
if (-not $target.IsLoopback -or $target.Scheme -ne 'http') { throw '只允许打开本机工作台' }
$agentExecutable = Join-Path $PSScriptRoot 'Xsy.Scale.Agent.exe'
try { $health = Invoke-RestMethod -Uri ($Url.TrimEnd('/') + '/health') -TimeoutSec 2 }
catch {
    if (-not (Test-Path -LiteralPath $agentExecutable)) { throw '未找到设备服务，请使用完整发布目录' }
    $logRoot = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'XSY/ScaleAgent/logs'
    New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
    $startStamp = Get-Date -Format 'yyyyMMddHHmmss'
    Start-Process -FilePath $agentExecutable -WorkingDirectory $PSScriptRoot -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logRoot ($startStamp + '.out.log')) `
        -RedirectStandardError (Join-Path $logRoot ($startStamp + '.err.log')) | Out-Null
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try { $health = Invoke-RestMethod -Uri ($Url.TrimEnd('/') + '/health') -TimeoutSec 1; $ready = $true; break }
        catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $ready) { throw '设备服务启动失败，请查看本机日志' }
}
if ($health.status -ne 'running' -or $health.version -ne '0.3.0') { throw '该端口不是预期版本的秤端服务，请先核对运行实例' }
Start-Process -FilePath $Url
