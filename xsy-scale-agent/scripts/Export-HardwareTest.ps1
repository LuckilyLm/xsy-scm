param([Parameter(Mandatory=$true)][string]$StateDirectory, [string]$OutputDirectory = '')
$ErrorActionPreference = 'Stop'
$owner = Get-Content -LiteralPath (Join-Path $StateDirectory 'local-test-processes.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($owner.mode -ne 'HARDWARE_TEST' -or ([uri]$owner.agentUrl).Host -ne '127.0.0.1' -or ([uri]$owner.agentUrl).Scheme -ne 'http') { throw '仅允许导出本机硬件测试结果' }
$agentRoot = if (Test-Path -LiteralPath (Join-Path $PSScriptRoot '../src/Xsy.Scale.Agent')) { Split-Path $PSScriptRoot -Parent } else { $PSScriptRoot }
$outputRoot = if ($OutputDirectory) { $OutputDirectory } else { Join-Path $agentRoot 'hardware-workflow' }
$reportRoot = Join-Path $outputRoot ((Get-Date -Format 'yyyyMMddHHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $reportRoot -Force | Out-Null
$issues = @()
try {
    # 不导出密钥、Token或DPAPI数据库，只通过现有只读接口收集本次测试事实。
    $key = (Get-Content -LiteralPath (Join-Path $StateDirectory 'agent/local-api-key.txt') -Raw).Trim()
    $headers = @{'X-Agent-Key'=$key; 'X-Operator-Session'='MOCK-OPERATOR'}
    $boot = Invoke-RestMethod -Uri ($owner.agentUrl + '/api/agent/workstation') -Headers $headers -TimeoutSec 5
    if (-not $boot.hardwareTest -or -not $boot.localTest) { throw '运行服务不是硬件测试模式' }
    $boot | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $reportRoot 'workstation.json') -Encoding UTF8
    $status = Invoke-RestMethod -Uri ($owner.agentUrl + '/api/agent/status') -Headers $headers -TimeoutSec 5
    $status | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $reportRoot 'status.json') -Encoding UTF8
    $batch = Invoke-RestMethod -Uri ($owner.agentUrl + '/api/agent/tasks') -Headers $headers -TimeoutSec 5
    $batch | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $reportRoot 'tasks.json') -Encoding UTF8
    $itemIndex = 0
    foreach ($job in @($batch.data.tasks)) { foreach ($item in @($job.items)) {
        $itemIndex++
        $query = '?jobId=' + [uri]::EscapeDataString($job.jobId) + '&taskId=' + [uri]::EscapeDataString($job.taskId) + '&taskItemId=' + [uri]::EscapeDataString($item.taskItemId)
        foreach ($route in @('basket-summary','baskets','basket-queue','local-tickets')) {
            # PS5.1会给顶层空数组附加Count/value包装；原样保留响应，避免分析时误判数据结构。
            $response = Invoke-WebRequest -UseBasicParsing -Uri ($owner.agentUrl + '/api/agent/' + $route + $query) -Headers $headers -TimeoutSec 5
            [IO.File]::WriteAllText((Join-Path $reportRoot ($itemIndex.ToString() + '-' + $route + '.json')), $response.Content, [Text.UTF8Encoding]::new($false))
        }
    } }
} catch { $issues += $_.Exception.Message }
foreach ($name in @('agent.out.log','agent.err.log','mock.out.log','mock.err.log','local-test-processes.json')) {
    $source = Join-Path $StateDirectory $name
    if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination $reportRoot }
}
@{mode='HARDWARE_TEST'; exportedAt=(Get-Date).ToString('o'); exportErrors=$issues; physicalOutputVerified=$false; platformSyncVerified=$false} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $reportRoot 'result.json') -Encoding UTF8
Write-Output ('结果目录：' + $reportRoot)
