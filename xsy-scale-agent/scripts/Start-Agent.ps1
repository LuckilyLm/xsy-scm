param()
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$agentExecutable = Join-Path $PSScriptRoot 'Xsy.Scale.Agent.exe'
if (-not (Test-Path -LiteralPath $agentExecutable)) { throw '请先发布完整安装目录' }
$agentLogDirectory = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'XSY\ScaleAgent\logs'
New-Item -ItemType Directory -Path $agentLogDirectory -Force | Out-Null
$agentLogFile = Join-Path $agentLogDirectory ('agent-' + (Get-Date -Format 'yyyyMMdd') + '.log')
& $agentExecutable --contentRoot $PSScriptRoot *>> $agentLogFile
exit $LASTEXITCODE
