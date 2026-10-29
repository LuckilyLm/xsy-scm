param(
    [string]$DotnetPath = 'dotnet',
    [string]$StateDirectory = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'XSY/ScaleAgent-Exercise-0.4.0'),
    [int]$AgentPort = 33610,
    [int]$PlatformPort = 33611,
    [switch]$Development,
    [switch]$NoBrowser,
    [switch]$HardwareTest,
    [switch]$EnableScaleControl,
    [switch]$GroupingFixture,
    [string]$SerialPort = 'COM1',
    [string]$PrinterName = 'X7'
)
$ErrorActionPreference = 'Stop'
$agentRoot = if (Test-Path -LiteralPath (Join-Path $PSScriptRoot '../src/Xsy.Scale.Agent')) { Split-Path $PSScriptRoot -Parent } else { $PSScriptRoot }
if ($HardwareTest) {
    if (-not $PSBoundParameters.ContainsKey('StateDirectory')) { $StateDirectory = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'XSY/ScaleAgent-HardwareWorkflow-0.4.0' }
    if (-not $PSBoundParameters.ContainsKey('AgentPort')) { $AgentPort = 33630 }
    if (-not $PSBoundParameters.ContainsKey('PlatformPort')) { $PlatformPort = 33631 }
    # 无打印需求也可称重保存；仅提示打印问题，不清队列或回退默认打印机。
    $printer = @(Get-CimInstance Win32_Printer | Where-Object { $_.Name -eq $PrinterName })
    if ($printer.Count -ne 1 -or $printer[0].WorkOffline) { Write-Warning '指定打印队列不存在或脱机，可以使用“记录上传”；打印前请检查X7' }
    if (@(Get-CimInstance Win32_PrintJob | Where-Object { $_.Name.StartsWith($PrinterName + ',') }).Count -gt 0) { Write-Warning '打印队列还有旧任务，可以使用“记录上传”；打印前请先核对，程序不会清空或重打' }
    if (-not ([IO.Ports.SerialPort]::GetPortNames() -contains $SerialPort)) { throw ('找不到串口：' + $SerialPort) }
}
$stateRoot = [IO.Path]::GetFullPath($StateDirectory)
New-Item -ItemType Directory -Path $stateRoot -Force | Out-Null
$ownerFile = Join-Path $stateRoot 'local-test-processes.json'
if (Test-Path -LiteralPath $ownerFile) {
    $previous = Get-Content -LiteralPath $ownerFile -Encoding UTF8 -Raw | ConvertFrom-Json
    foreach ($entry in @($previous.processes)) {
        $running = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
        if ($null -ne $running -and $running.StartTime.ToUniversalTime().Ticks -eq $entry.started) { throw '该演练目录已有运行实例，请先运行Stop-LocalTest' }
    }
}
$agentUrl = 'http://127.0.0.1:' + $AgentPort
$platformUrl = 'http://127.0.0.1:' + $PlatformPort
if ($AgentPort -eq $PlatformPort -or $AgentPort -lt 1024 -or $AgentPort -gt 65535 -or $PlatformPort -lt 1024 -or $PlatformPort -gt 65535) { throw '演练端口须为不同的1024至65535端口' }
foreach ($port in @($AgentPort,$PlatformPort)) {
    $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
    try { $probe.Start() } catch { throw ('演练端口已占用：' + $port + '，请停止已知演练或选择其他端口') } finally { $probe.Stop() }
}
$owned = @()

function Start-OwnedProcess([string]$File, [string[]]$Arguments, [hashtable]$Variables, [string]$Label) {
    $oldVariables = @{}
    try {
        foreach ($name in $Variables.Keys) { $oldVariables[$name] = [Environment]::GetEnvironmentVariable($name, 'Process'); [Environment]::SetEnvironmentVariable($name, $Variables[$name], 'Process') }
        return Start-Process -FilePath $File -ArgumentList $Arguments -WorkingDirectory $agentRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $stateRoot ($Label + '.out.log')) -RedirectStandardError (Join-Path $stateRoot ($Label + '.err.log'))
    } finally { foreach ($name in $Variables.Keys) { [Environment]::SetEnvironmentVariable($name, $oldVariables[$name], 'Process') } }
}
function Wait-LocalHost([string]$Url) {
    for ($attempt = 0; $attempt -lt 50; $attempt++) {
        try { $null = Invoke-RestMethod -Uri ($Url + '/health') -TimeoutSec 1; return }
        catch { Start-Sleep -Milliseconds 300 }
    }
    throw ('演练服务启动失败：' + $Url + '，请查看演练目录日志')
}
try {
    $mockFile = Join-Path $agentRoot 'local-test/Xsy.Scale.Mock.exe'
    $mockArgs = @('--urls', $platformUrl, '--Fixture', $(if ($HardwareTest) { 'false' } else { 'true' }))
    if ($GroupingFixture) { $mockArgs += @('--Grouping', 'true') }
    $agentFile = Join-Path $agentRoot 'Xsy.Scale.Agent.exe'
    $agentArgs = @('--urls', $agentUrl)
    if ($Development) {
        $mockFile = $DotnetPath
        $mockArgs = @(('"' + (Join-Path $agentRoot 'mock-platform/bin/Release/net10.0/Xsy.Scale.Mock.dll') + '"')) + $mockArgs
        $agentFile = $DotnetPath
        $agentArgs = @(('"' + (Join-Path $agentRoot 'src/Xsy.Scale.Agent/bin/Release/net10.0-windows/Xsy.Scale.Agent.dll') + '"')) + $agentArgs
    }
    $mock = Start-OwnedProcess $mockFile $mockArgs @{} 'mock'
    $owned += @{id=$mock.Id; started=$mock.StartTime.ToUniversalTime().Ticks; file=$mock.Path}
    Wait-LocalHost $platformUrl
    $variables = @{
        'XSY_Agent__DeviceCode'='TEST-SCALE-01'; 'XSY_Agent__StationCode'='TEST-STATION-01'
        'XSY_Agent__DataDirectory'=(Join-Path $stateRoot 'agent'); 'XSY_Agent__PlatformBaseUrl'=$platformUrl
        'XSY_Agent__AllowLoopbackPlatform'='true'; 'XSY_Agent__Protocol'='SOHE_HTTP'
        'XSY_Agent__HttpScaleUrl'=$platformUrl; 'XSY_Agent__HttpUnit'='sj'; 'XSY_Agent__HttpStableField'='stable'
        'XSY_Agent__AllowScaleControl'=$(if ($HardwareTest -or $EnableScaleControl) { 'true' } else { 'false' })
        'XSY_Agent__PrinterName'='EXERCISE-NO-PHYSICAL-PRINTER'; 'XSY_Workstation__LocalTest'='true'
        'XSY_Workstation__LocalTicketsEnabled'='true'; 'XSY_Workstation__InitialOperatorSession'='MOCK-OPERATOR'
        'XSY_Workstation__MaintenanceCode'='LOCAL-TEST-MAINTENANCE'
        'XSY_Workstation__HardwareTest'='false'; 'XSY_Workstation__TraceUrlTemplate'=''
        'XSY_Workstation__PaperWidthMm'='60'; 'XSY_Workstation__PaperHeightMm'='40'
    }
    if ($HardwareTest) {
        $variables['XSY_Agent__DeviceCode']='TEST-HW-01'; $variables['XSY_Agent__StationCode']='TEST-HW-STATION'
        $variables['XSY_Agent__Protocol']='SOHE_RS232_V101'; $variables['XSY_Agent__SerialPort']=$SerialPort
        $variables['XSY_Agent__BaudRate']='9600'; $variables['XSY_Agent__PrinterName']=$PrinterName
        $variables['XSY_Agent__DivisionKg']='0.05'
        $variables['XSY_Workstation__HardwareTest']='true'
    }
    $agent = Start-OwnedProcess $agentFile $agentArgs $variables 'agent'
    $owned += @{id=$agent.Id; started=$agent.StartTime.ToUniversalTime().Ticks; file=$agent.Path}
    Wait-LocalHost $agentUrl
    $key = (Get-Content -LiteralPath (Join-Path $variables['XSY_Agent__DataDirectory'] 'local-api-key.txt') -Raw).Trim()
    $headers = @{'X-Agent-Key'=$key; 'X-Operator-Session'='MOCK-OPERATOR'}
    $null = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/activate') -Method Post -Headers $headers -ContentType 'application/json' -Body '{"pairingCode":"MOCK-PAIR"}'
    $null = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/tasks') -Headers $headers
    $mode = if ($HardwareTest) { 'HARDWARE_TEST' } else { 'LOCAL_TEST' }
    @{processes=$owned; agentUrl=$agentUrl; platformUrl=$platformUrl; mode=$mode} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ownerFile -Encoding UTF8
    if (-not $NoBrowser) { Start-Process -FilePath $agentUrl }
    [pscustomobject]@{Mode=$mode; Workstation=$agentUrl; ControlPanel=$platformUrl; State=$stateRoot}
    if ($HardwareTest) { Write-Output '实机联动测试已启动：空秤置零、放空筐后去皮，等待设备稳定零值；将秤切换市斤，稳定正净重按原数记录，不设软件固定量程；也可手工录入其他大秤的净重。手动保存后打印测试标签；结束请双击Stop-HardwareTest.cmd导出结果。' }
} catch {
    foreach ($entry in $owned) {
        $running = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
        if ($null -ne $running -and $running.StartTime.ToUniversalTime().Ticks -eq $entry.started -and $running.Path -eq $entry.file) { Stop-Process -Id $entry.id }
    }
    throw
}
