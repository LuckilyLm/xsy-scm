param(
    [string]$DotnetPath = 'dotnet',
    [string]$TestDirectory = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) '.codex-tmp\scale-agent-tests')
)
$ErrorActionPreference = 'Stop'
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$agentProject = Join-Path $PSScriptRoot 'src\Xsy.Scale.Agent\Xsy.Scale.Agent.csproj'
$mockProject = Join-Path $PSScriptRoot 'mock-platform\Xsy.Scale.Mock.csproj'
$testProject = Join-Path $PSScriptRoot 'tests\Xsy.Scale.Agent.Tests\Xsy.Scale.Agent.Tests.csproj'
& $DotnetPath build $agentProject --configuration Release
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $DotnetPath build $mockProject --configuration Release
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $DotnetPath run --project $testProject --configuration Release -- $TestDirectory
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot 'tests\Smoke-Agent.ps1') -DotnetPath $DotnetPath -TestDirectory $TestDirectory
exit $LASTEXITCODE
