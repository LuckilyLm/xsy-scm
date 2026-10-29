<#
.SYNOPSIS
只读采集真实串口字节，供读秤协议排查；不发命令、不保存称重业务或打印。
.PARAMETER Port
当前已确认的串口，默认COM1；不存在或被占用时记录错误，不猜测其他端口。
.PARAMETER BaudRate
与设备一致的波特率，默认9600，固定8N1及无流控；不修改设备端配置。
.PARAMETER Seconds
采集时长，3至30秒，默认20秒；达到64KiB字节上限提前结束。
.PARAMETER OutputDirectory
诊断输出父目录，空值使用脚本旁serial-raw-check；每轮新建目录，保留旧结果。
#>
param(
    [ValidatePattern('^COM[0-9]{1,4}$')][string]$Port = 'COM1',
    [ValidateSet(9600,19200,38400,57600,115200)][int]$BaudRate = 9600,
    [ValidateRange(3,30)][int]$Seconds = 20,
    [string]$OutputDirectory = ''
)

function Get-ScaleRawSummary {
    <#
    .SYNOPSIS
    汇总原始字节的分行形状，不将未知帧解释为可信净重或修正单位。
    .PARAMETER Bytes
    完整采集字节；允许空集合，空或null返回零字节摘要。
    .OUTPUTS
    字节数、CRLF数量及最多12条原始行预览；仅13字节行列出协议单位位置的原文。
    #>
    param([AllowNull()][AllowEmptyCollection()][byte[]]$Bytes)
    # PowerShell会把管道中的空数组展开成null，空采样不能直接传给Encoding.GetString。
    $rawText = if ($null -eq $Bytes -or $Bytes.Length -eq 0) { '' } else { [Text.Encoding]::ASCII.GetString($Bytes) }
    $lines = @([regex]::Matches($rawText, '[^\n]*\n') | Select-Object -First 12 | ForEach-Object {
        $line = $_.Value
        [pscustomobject]@{
            length = $line.Length
            ascii = $line.Substring(0, [Math]::Min(160, $line.Length))
            unitSlot = $(if ($line.Length -eq 13) { $line.Substring(9, 2) } else { $null })
        }
    })
    [pscustomobject]@{ byteCount=$Bytes.Length; crlfCount=[regex]::Matches($rawText, '\r\n').Count; linePreview=$lines }
}

# 点入脚本只加载纯汇总函数，供本地自动验证；正常双击才执行真实只读采集。
if ($MyInvocation.InvocationName -eq '.') { return }
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false) } catch { }
$outputRoot = if ($OutputDirectory) { $OutputDirectory } else { Join-Path $PSScriptRoot 'serial-raw-check' }
$runDirectory = Join-Path $outputRoot ((Get-Date -Format 'yyyyMMddHHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$report = [ordered]@{
    mode='READ_ONLY_SERIAL'; startedAt=(Get-Date).ToString('o'); port=$Port; baudRate=$BaudRate
    dataBits=8; parity='None'; stopBits=1; handshake='None'; durationRequested=$Seconds
    portOpened=$false; chunks=0; byteCount=0; availablePorts=@(); summary=$null
    result='NOT_STARTED'; errorType=$null; errorMessage=$null
    sendsCommands=$false; writesBusinessRecords=$false; prints=$false
}
$serial = $null
$writer = $null
$memory = [IO.MemoryStream]::new()
$watch = [Diagnostics.Stopwatch]::new()
try {
    $report.availablePorts = @([IO.Ports.SerialPort]::GetPortNames())
    if ($report.availablePorts -notcontains $Port) { throw ('找不到已指定的串口：' + $Port) }
    Write-Host '只读串口检查：请保持斤设置，关闭串口助手和称重程序。本工具不发送去皮或置零命令。'
    $serial = [IO.Ports.SerialPort]::new($Port, $BaudRate, [IO.Ports.Parity]::None, 8, [IO.Ports.StopBits]::One)
    $serial.Handshake = [IO.Ports.Handshake]::None
    $serial.ReadTimeout = 200
    $serial.Open()
    $report.portOpened = $true
    $writer = [IO.StreamWriter]::new((Join-Path $runDirectory 'raw-chunks.jsonl'), $false, [Text.UTF8Encoding]::new($false))
    $buffer = [byte[]]::new(256)
    Write-Host ('正在读取 ' + $Port + '，约' + $Seconds + '秒；可在期间放入、拿走一个小物品。')
    $watch.Start()
    while ($watch.Elapsed.TotalSeconds -lt $Seconds -and $memory.Length -lt 65536) {
        try { $count = $serial.Read($buffer, 0, [Math]::Min($buffer.Length, 65536 - [int]$memory.Length)) }
        catch [TimeoutException] { continue }
        if ($count -le 0) { continue }
        $memory.Write($buffer, 0, $count)
        $report.chunks++
        $chunk = [ordered]@{
            at=(Get-Date).ToString('o'); count=$count
            hex=[BitConverter]::ToString($buffer, 0, $count).Replace('-', '')
            ascii=[Text.Encoding]::ASCII.GetString($buffer, 0, $count)
        }
        $writer.WriteLine(($chunk | ConvertTo-Json -Compress))
        $writer.Flush()
    }
    $report.result = if ($memory.Length -gt 0) { 'RAW_BYTES_RECEIVED' } else { 'NO_BYTES_RECEIVED' }
} catch {
    $report.result = 'CAPTURE_FAILED'
    $report.errorType = $_.Exception.GetBaseException().GetType().Name
    $report.errorMessage = $_.Exception.Message
} finally {
    $watch.Stop()
    if ($null -ne $serial) { $serial.Dispose() }
    if ($null -ne $writer) { $writer.Dispose() }
    $report.summary = Get-ScaleRawSummary -Bytes $memory.ToArray()
    $report.byteCount = $memory.Length
    $report.elapsedSeconds = [Math]::Round($watch.Elapsed.TotalSeconds, 3)
    $report.finishedAt = (Get-Date).ToString('o')
    $memory.Dispose()
    [IO.File]::WriteAllText((Join-Path $runDirectory 'result.json'), ($report | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
}
Write-Host ('检查结果：' + $report.result + '；收到 ' + $report.byteCount + ' 字节。')
if ($report.errorMessage) { Write-Host $report.errorMessage -ForegroundColor Red }
Write-Host ('结果目录：' + $runDirectory)
if ($report.result -eq 'CAPTURE_FAILED') { exit 1 }
