# Dot-source from the same PowerShell used to start the backend. Does not print credentials.
param([string]$EnvFile = (Join-Path $PSScriptRoot '.env'))
$ErrorActionPreference = 'Stop'
$allowed = @(
    'MINIO_BIND_ADDRESS', 'XSY_FILE_STORAGE_MODE', 'XSY_FILE_REGION', 'XSY_FILE_ENDPOINT',
    'XSY_FILE_BUCKET', 'XSY_FILE_ACCESS_KEY', 'XSY_FILE_SECRET_KEY', 'XSY_FILE_PATH_STYLE',
    'XSY_FILE_SEND_OBJECT_ACL', 'XSY_FILE_PUBLIC_URL_PREFIX', 'XSY_FILE_PRIVATE_URL_EXPIRE'
)
foreach ($line in Get-Content -LiteralPath $EnvFile) {
    if ($line.Trim() -eq '' -or $line.TrimStart().StartsWith('#')) { continue }
    if ($line -notmatch '^([A-Z_]+)=(.*)$' -or $Matches[1] -notin $allowed) {
        throw 'Unsupported .env entry; expected a documented F0 NAME=value assignment.'
    }
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
}
Write-Host 'F0 file storage variables loaded into this process; credentials withheld.'
