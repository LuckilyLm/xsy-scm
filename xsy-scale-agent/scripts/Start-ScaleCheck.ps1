<#
.SYNOPSIS
使用已发布的 Agent 检查真实串口重量，不创建业务称重记录。
.PARAMETER AgentDirectory
完整发布目录；省略时使用脚本所在目录，目录中须有 Xsy.Scale.Agent.exe。
.PARAMETER SerialPort
已核实的 COM 端口；默认 COM1，不自动尝试其他端口。
.PARAMETER AgentPort
本次诊断的回环 HTTP 端口，默认 33620；被占用时拒绝启动。
.PARAMETER Seconds
观察秒数，1 至 600；默认 60，也可在窗口中按 Q 提前结束。
.PARAMETER OutputDirectory
诊断结果的父目录；省略时使用程序目录下 hardware-check，每次新建子目录。
.OUTPUTS
readings.jsonl 与 result.json，不包含本机访问密钥。收到读数返回 0，无读数返回 2，其他错误返回 1。
#>
param(
    [string]$AgentDirectory = $PSScriptRoot,
    [ValidatePattern('^COM[1-9][0-9]*$')][string]$SerialPort = 'COM1',
    [ValidateRange(1024,65535)][int]$AgentPort = 33620,
    [ValidateRange(1,600)][int]$Seconds = 60,
    [string]$OutputDirectory = ''
)
$ErrorActionPreference = 'Stop'

function Convert-ScaleReading {
    <#
    .SYNOPSIS
    仅为诊断展示换算原始重量，保留设备报告的毛净重及稳定性。
    .PARAMETER Reading
    Agent status 的非空 reading 对象；单位仅允许 kg、g、sj，缺失或未知时抛出错误。
    .OUTPUTS
    原始重量、公斤、毛净重、稳定性及原始帧；不推算未知皮重，不决定业务是否可采集。
    #>
    param([Parameter(Mandatory=$true)]$Reading)
    if ($null -eq $Reading.rawWeight) { throw '读数缺少原始重量' }
    $raw = [Convert]::ToDecimal($Reading.rawWeight, [Globalization.CultureInfo]::InvariantCulture)
    $unit = [string]$Reading.rawUnit
    $kg = switch ($unit) {
        'kg' { $raw }
        'g' { $raw / [decimal]1000 }
        'sj' { $raw / [decimal]2 }
        default { throw ('未知重量单位：' + $unit) }
    }
    $unitName = switch ($unit) { 'kg' { '公斤' }; 'g' { '克' }; 'sj' { '市斤' } }
    $kind = switch ($Reading.netGrossFlag) { 'GROSS' { '毛重' }; 'NET' { '净重' }; default { '毛净重未知' } }
    $stability = if ($Reading.stableFlag -isnot [bool]) { '稳定性未知' } elseif ($Reading.stableFlag) { '稳定' } else { '不稳定' }
    [pscustomobject]@{
        rawWeight=$raw; rawUnit=$unit; unitName=$unitName; kilograms=$kg
        weightKind=$kind; stability=$stability; capturedAt=$Reading.capturedAt; rawFrameHex=$Reading.rawFrameHex
    }
}

$agentProcess = $null
$ownedStart = 0L
$writer = $null
$resultFile = $null
$exitCode = 1
$summary = [ordered]@{
    source='PHYSICAL_SERIAL_VIA_AGENT'; result='INTERRUPTED'; startedAt=[DateTimeOffset]::Now.ToString('o')
    serialPort=$SerialPort; baudRate=9600; protocol='SOHE_RS232_V101'
    pollsWithReading=0; observedZero=$false; observedNonZero=$false; lastError=$null
    businessRecordsCreated=$false; printRequested=$false; platformUploadRequested=$false; ownedProcessStopped=$false
}
try {
    $agentRoot = [IO.Path]::GetFullPath($AgentDirectory)
    $agentFile = Join-Path $agentRoot 'Xsy.Scale.Agent.exe'
    if (-not (Test-Path -LiteralPath $agentFile -PathType Leaf)) { throw '请把本检查包解压到 Xsy.Scale.Agent.exe 所在的完整程序目录。' }
    # 不连接已有服务；先排除端口占用，再核对新实例的随机设备标识。
    $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $AgentPort)
    try { $probe.Start() } catch { throw ('检查端口 ' + $AgentPort + ' 已被占用，请先结束上次检查。') } finally { $probe.Stop() }
    $runId = (Get-Date -Format 'yyyyMMddHHmmss') + '-' + [guid]::NewGuid().ToString('N')
    $deviceCode = 'DIAG-' + $runId
    $stateRoot = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) ('XSY/ScaleAgent-HardwareCheck/' + $runId)
    if ([string]::IsNullOrWhiteSpace($OutputDirectory)) { $OutputDirectory = Join-Path $agentRoot 'hardware-check' }
    $reportRoot = Join-Path ([IO.Path]::GetFullPath($OutputDirectory)) $runId
    New-Item -ItemType Directory -Path $stateRoot,$reportRoot -ErrorAction Stop | Out-Null
    $resultFile = Join-Path $reportRoot 'result.json'
    $writer = [IO.StreamWriter]::new((Join-Path $reportRoot 'readings.jsonl'), $false, [Text.UTF8Encoding]::new($false))
    $writer.AutoFlush = $true
    Write-Host '鲜蔬园 · 实秤读数检查' -ForegroundColor Cyan
    Write-Host ('使用 ' + $SerialPort + '，9600 / 8N1；请先关闭串口调试助手。')
    Write-Host '请依次：空秤 → 放物品并等待稳定 → 移除物品。'
    Write-Host ('观察 ' + $Seconds + ' 秒，按 Q 可提前结束。只读重量，不去皮、置零、保存业务或打印。')
    Write-Host ('结果目录：' + $reportRoot)
    # 命令行优先级高于个人配置和环境变量。新目录没有平台凭证或待打印任务。
    # 路径单独加引号以兼容 Windows PowerShell 5.1 的 Start-Process 参数拼接。
    if ($stateRoot.Contains('"')) { throw '诊断数据路径不能包含双引号' }
    $arguments = @(
        '--urls', ('http://127.0.0.1:' + $AgentPort),
        ('--Agent:DataDirectory="' + $stateRoot + '"'), ('--Agent:DeviceCode=' + $deviceCode),
        '--Agent:StationCode=DIAG-LOCAL', '--Agent:Protocol=SOHE_RS232_V101',
        ('--Agent:SerialPort=' + $SerialPort), '--Agent:BaudRate=9600', '--Agent:AdAddress=A',
        '--Agent:AllowScaleControl=false', '--Agent:PlatformBaseUrl=http://127.0.0.1:1',
        '--Agent:AllowLoopbackPlatform=false', '--Agent:PrinterName=DISABLED-HARDWARE-CHECK',
        '--Workstation:LocalTest=false', '--Workstation:LocalTicketsEnabled=false',
        '--Workstation:InitialOperatorSession=', '--Workstation:MaintenanceCode='
    )
    $agentProcess = Start-Process -FilePath $agentFile -ArgumentList $arguments -WorkingDirectory $agentRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $reportRoot 'agent.out.log') -RedirectStandardError (Join-Path $reportRoot 'agent.err.log')
    $ownedStart = $agentProcess.StartTime.ToUniversalTime().Ticks
    @{id=$agentProcess.Id; started=$ownedStart; file=$agentFile} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reportRoot 'owned-process.json') -Encoding UTF8
    $url = 'http://127.0.0.1:' + $AgentPort
    $ready = $false
    for ($attempt=0; $attempt -lt 40; $attempt++) {
        if ($agentProcess.HasExited) { throw '秤端程序提前退出，请将本次结果目录中的日志发回。' }
        try { $health = Invoke-RestMethod -Uri ($url + '/health') -TimeoutSec 1; $ready=$true; break }
        catch { Start-Sleep -Milliseconds 250 }
    }
    if (-not $ready -or $health.status -ne 'running' -or $health.version -ne '0.3.0') { throw '未能启动预期版本 0.3.0，请检查本次启动日志。' }
    # 复用已有同源会话，不读取或导出 local-api-key.txt。
    $null = Invoke-WebRequest -Uri ($url + '/') -UseBasicParsing -SessionVariable scaleSession -TimeoutSec 3
    $headers = @{'X-Workstation-Request'='1'}
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $Seconds) {
        if ($agentProcess.HasExited) { throw '检查期间秤端程序退出，请查看本次启动日志。' }
        $status = Invoke-RestMethod -Uri ($url + '/api/agent/status') -WebSession $scaleSession -Headers $headers -TimeoutSec 3
        if ($status.deviceCode -ne $deviceCode -or $status.deviceAuthorized) { throw '诊断实例身份不符，停止读取。' }
        $entry = [ordered]@{observedAt=[DateTimeOffset]::Now.ToString('o'); scaleConnected=$status.scaleConnected; error=$status.error; reading=$status.reading}
        $writer.WriteLine(($entry | ConvertTo-Json -Depth 6 -Compress))
        if ($status.scaleConnected -and $null -ne $status.reading) {
            $view = Convert-ScaleReading -Reading $status.reading
            $summary.pollsWithReading++
            $summary.lastError = $null
            if ($view.kilograms -eq 0) { $summary.observedZero=$true } else { $summary.observedNonZero=$true }
            Write-Host ('{0:HH:mm:ss} | 原始 {1} {2} | {3} 公斤 | {4} | {5}' -f (Get-Date),$view.rawWeight,$view.unitName,$view.kilograms,$view.weightKind,$view.stability)
        } else {
            $summary.lastError = $status.error
            Write-Host ('等待读数：' + $status.error + '（请确认串口助手已关闭）') -ForegroundColor Yellow
        }
        if (-not [Console]::IsInputRedirected -and [Console]::KeyAvailable) {
            if ([Console]::ReadKey($true).Key -eq [ConsoleKey]::Q) { break }
        }
        Start-Sleep -Milliseconds 1000
    }
    if ($summary.pollsWithReading -gt 0) { $summary.result='READINGS_RECEIVED'; $exitCode=0 }
    else { $summary.result='NO_READINGS'; $exitCode=2 }
} catch {
    $summary.result='ERROR'; $summary.lastError=$_.Exception.Message
    Write-Host ('检查未完成：' + $_.Exception.Message) -ForegroundColor Red
} finally {
    if ($null -ne $writer) { $writer.Dispose() }
    # 只停止本次启动且路径、启动时刻都相符的进程，不按进程名批量终止。
    if ($null -ne $agentProcess) {
        $running = Get-Process -Id $agentProcess.Id -ErrorAction SilentlyContinue
        if ($null -eq $running) { $summary.ownedProcessStopped=$true }
        elseif ($running.StartTime.ToUniversalTime().Ticks -eq $ownedStart -and $running.Path -eq $agentFile) {
            Stop-Process -Id $running.Id -ErrorAction SilentlyContinue
            $summary.ownedProcessStopped=$running.WaitForExit(5000)
        }
    }
    $summary.finishedAt=[DateTimeOffset]::Now.ToString('o')
    if ($null -ne $resultFile) {
        $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $resultFile -Encoding UTF8
        Write-Host ('检查结束：' + $summary.result + '；结果已保存到 ' + $resultFile)
    }
}
exit $exitCode
