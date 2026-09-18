# Both platform entry points share checks and exit codes.
param(
    [ValidateSet('all', 'backend', 'frontend', 'e2e')]
    [string]$Scope = 'all'
)
$ErrorActionPreference = 'Stop'
try {
    $python = Get-Command python -ErrorAction Stop
    & $python.Source (Join-Path $PSScriptRoot 'tools/verify.py') $Scope
    exit $LASTEXITCODE
}
catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
}
