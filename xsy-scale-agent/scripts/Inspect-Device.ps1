param(
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [switch]$ProbeVendorHttp
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$agentInventory = [ordered]@{
    capturedAt = [DateTimeOffset]::Now.ToString('o')
    osVersion = [Environment]::OSVersion.VersionString
    is64BitOperatingSystem = [Environment]::Is64BitOperatingSystem
    serialPorts = @([System.IO.Ports.SerialPort]::GetPortNames())
    portDevices = @(Get-PnpDevice -Class Ports -ErrorAction SilentlyContinue | Select-Object FriendlyName,Status)
    printers = @(Get-Printer -ErrorAction SilentlyContinue | Select-Object Name,DriverName,PortName,PrinterStatus)
}
if ($ProbeVendorHttp) {
    try { $agentInventory.vendorWeight = Invoke-RestMethod -Uri 'http://127.0.0.1:33582/api/Weight' -TimeoutSec 5 }
    catch { $agentInventory.vendorHttpError = 'VENDOR_HTTP_UNAVAILABLE' }
}
$agentInventory | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'device-inventory.json') -Encoding UTF8
Write-Output '只读检查完成；未执行去皮、置零、标定或物理打印。'
