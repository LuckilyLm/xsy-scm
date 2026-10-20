param()
$ErrorActionPreference = 'Stop'
$agentExecutable = Join-Path $PSScriptRoot 'Xsy.Scale.Agent.exe'
if (-not (Test-Path -LiteralPath $agentExecutable)) { throw '必须在发布目录中安装自启动' }
$agentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$agentAction = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument ('-NoProfile -WindowStyle Hidden -File "' + (Join-Path $PSScriptRoot 'Start-Agent.ps1') + '"') `
    -WorkingDirectory $PSScriptRoot
$agentTrigger = New-ScheduledTaskTrigger -AtLogOn -User $agentUser
$agentPrincipal = New-ScheduledTaskPrincipal -UserId $agentUser -LogonType Interactive -RunLevel Limited
$agentSettings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)
Register-ScheduledTask -TaskName 'XSY-ScaleAgent' -Action $agentAction -Trigger $agentTrigger `
    -Principal $agentPrincipal -Settings $agentSettings -Description '鲜蔬园电子秤采集与打印服务' -Force | Out-Null
Write-Output '已为当前 Windows 用户配置登录自启；凭证和打印驱动应使用同一用户。'
