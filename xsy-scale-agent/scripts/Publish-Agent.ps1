param(
    [ValidateSet('win-x64','win-x86')][string]$Runtime = 'win-x64',
    [string]$DotnetPath = 'dotnet',
    [switch]$SkipWorkstationBuild
)
$ErrorActionPreference = 'Stop'
$agentRoot = Split-Path $PSScriptRoot -Parent
$publishDirectory = Join-Path $agentRoot ('publish\' + $Runtime)
if (-not $SkipWorkstationBuild) {
    & (Join-Path $PSScriptRoot 'Build-Workstation.ps1') -SkipInstall:(Test-Path -LiteralPath (Join-Path $agentRoot 'workstation/node_modules'))
}
if (-not (Test-Path -LiteralPath (Join-Path $agentRoot 'src/Xsy.Scale.Agent/wwwroot/index.html'))) { throw '请先构建本地工作台资源' }
if (Test-Path -LiteralPath $publishDirectory) {
    $backupRoot = Join-Path (Split-Path (Split-Path $agentRoot -Parent) -Parent) '.codex-tmp/publish-backups'
    New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
    $resolvedPublish = [IO.Path]::GetFullPath($publishDirectory)
    if (-not $resolvedPublish.StartsWith([IO.Path]::GetFullPath((Join-Path $agentRoot 'publish')) + [IO.Path]::DirectorySeparatorChar)) { throw '发布目录不在预期模块中' }
    Move-Item -LiteralPath $resolvedPublish -Destination (Join-Path $backupRoot ([guid]::NewGuid().ToString('N')))
}
& $DotnetPath publish (Join-Path $agentRoot 'src\Xsy.Scale.Agent\Xsy.Scale.Agent.csproj') `
    --configuration Release --runtime $Runtime --self-contained true `
    -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true --output $publishDirectory
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $DotnetPath publish (Join-Path $agentRoot 'mock-platform/Xsy.Scale.Mock.csproj') `
    --configuration Release --runtime $Runtime --self-contained true `
    -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true --output (Join-Path $publishDirectory 'local-test')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
foreach ($name in @('Open-Workstation','Start-LocalTest','Stop-LocalTest')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot ($name + '.ps1')) -Destination $publishDirectory
    $launcher = '@echo off' + "`r`n" + 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0' + $name + '.ps1"' + "`r`n" + 'if errorlevel 1 pause' + "`r`n"
    [IO.File]::WriteAllText((Join-Path $publishDirectory ($name + '.cmd')), $launcher, [Text.Encoding]::ASCII)
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Start-Agent.ps1') -Destination $publishDirectory
foreach ($action in @('Start','Stop')) {
    $launcher = '@echo off' + "`r`n" + 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0' + $action + '-LocalTest.ps1" -HardwareTest' + "`r`n" + 'pause' + "`r`n"
    [IO.File]::WriteAllText((Join-Path $publishDirectory ($action + '-HardwareTest.cmd')), $launcher, [Text.Encoding]::ASCII)
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Export-HardwareTest.ps1') -Destination $publishDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'HardwareTest-README.txt') -Destination $publishDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Install-Autostart.ps1') -Destination $publishDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Inspect-Device.ps1') -Destination $publishDirectory
$publishDocs = Join-Path $publishDirectory 'docs'
$publishApi = Join-Path $publishDirectory '.codex-api-spec'
$publishSql = Join-Path $publishDirectory '.codex-sql'
New-Item -ItemType Directory -Path $publishDocs,$publishApi,$publishSql -Force | Out-Null
$publishReadme = (Get-Content -LiteralPath (Join-Path $agentRoot 'README.md') -Encoding UTF8 -Raw).Replace('../docs/sohe-scale-integration-handoff.md','docs/sohe-scale-integration-handoff.md')
$publishReadme | Set-Content -LiteralPath (Join-Path $publishDirectory 'README.md') -Encoding UTF8
Copy-Item -LiteralPath (Join-Path $agentRoot '.codex-api-spec\秤端本地接口文档.md') -Destination $publishApi
# 增量结构随版本累计，发布时包含全部记录，避免升级包漏掉多筐新表说明。
Get-ChildItem -LiteralPath (Join-Path $agentRoot '.codex-sql') -Filter '*.md' -File | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $publishSql
}
Copy-Item -LiteralPath (Join-Path (Split-Path $agentRoot -Parent) 'docs\sohe-scale-integration-handoff.md') -Destination $publishDocs
Copy-Item -LiteralPath (Join-Path $agentRoot 'appsettings.Local.example.json') -Destination $publishDirectory
Copy-Item -LiteralPath (Join-Path $agentRoot 'FIRST-GENERATION-HANDOFF.md') -Destination $publishDirectory
Copy-Item -LiteralPath (Join-Path $agentRoot 'THIRD-PARTY-NOTICES.txt') -Destination $publishDirectory
Write-Output $publishDirectory
