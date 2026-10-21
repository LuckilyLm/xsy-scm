param(
    [Parameter(Mandatory=$true)][string]$DotnetPath,
    [Parameter(Mandatory=$true)][string]$TestDirectory,
    [string]$AgentExecutablePath = ''
)
$ErrorActionPreference = 'Stop'
$agentRoot = Split-Path $PSScriptRoot -Parent
$scenarioDirectory = Join-Path $TestDirectory ([Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scenarioDirectory -Force | Out-Null
$agentProcess = $null
$mockProcess = $null

function Get-TestPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    return $port
}

function Start-TestHost([string]$DllPath, [string]$Url, [hashtable]$Variables) {
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $DotnetPath
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WorkingDirectory = Split-Path $DllPath -Parent
    $start.Arguments = '"' + $DllPath + '" --urls "' + $Url + '"'
    if ($DllPath.EndsWith('.exe')) {
        $start.FileName = $DllPath
        $start.Arguments = '--urls "' + $Url + '"'
        $start.WorkingDirectory = $scenarioDirectory
    }
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($entry in $Variables.GetEnumerator()) { $start.EnvironmentVariables[$entry.Key] = $entry.Value }
    $process = [System.Diagnostics.Process]::Start($start)
    return @{ Process=$process; Output=$process.StandardOutput.ReadToEndAsync(); Error=$process.StandardError.ReadToEndAsync() }
}

function Wait-TestHost([string]$Url) {
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        try { return Invoke-RestMethod -Uri ($Url + '/health') -TimeoutSec 1 }
        catch { Start-Sleep -Milliseconds 150 }
    }
    throw '测试服务未就绪'
}

function Stop-TestHost($HostState, [string]$Name) {
    if ($null -ne $HostState) {
        if (-not $HostState.Process.HasExited) { $HostState.Process.Kill() }
        $HostState.Process.WaitForExit()
        $HostState.Output.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $scenarioDirectory ($Name + '.log')) -Encoding UTF8
        $HostState.Error.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $scenarioDirectory ($Name + '-error.log')) -Encoding UTF8
        $HostState.Process.Dispose()
    }
}

try {
    $agentUrl = 'http://127.0.0.1:' + (Get-TestPort)
    $mockUrl = 'http://127.0.0.1:' + (Get-TestPort)
    $mockDll = Join-Path $agentRoot 'mock-platform\bin\Release\net10.0\Xsy.Scale.Mock.dll'
    $agentDll = Join-Path $agentRoot 'src\Xsy.Scale.Agent\bin\Release\net10.0-windows\Xsy.Scale.Agent.dll'
    if ($AgentExecutablePath) { $agentDll = $AgentExecutablePath }
    $mockProcess = Start-TestHost $mockDll $mockUrl @{}
    $null = Wait-TestHost $mockUrl
    $variables = @{
        'XSY_Agent__DataDirectory' = (Join-Path $scenarioDirectory 'state')
        'XSY_Agent__DeviceCode' = 'MOCK-ONLY-SCALE'
        'XSY_Agent__StationCode' = 'MOCK-STATION'
        'XSY_Agent__PlatformBaseUrl' = $mockUrl
        'XSY_Agent__AllowLoopbackPlatform' = 'true'
    }
    $agentProcess = Start-TestHost $agentDll $agentUrl $variables
    $null = Wait-TestHost $agentUrl
    $unauthorized = $false
    try { $null = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/status') }
    catch { $unauthorized = [int]$_.Exception.Response.StatusCode -eq 401 }
    if (-not $unauthorized) { throw '无本机密钥请求未被拒绝' }
    $key = (Get-Content -LiteralPath (Join-Path $variables['XSY_Agent__DataDirectory'] 'local-api-key.txt') -Raw).Trim()
    $headers = @{ 'X-Agent-Key'=$key; 'X-Operator-Session'='MOCK-OPERATOR' }
    $null = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/activate') -Method Post -Headers $headers `
        -ContentType 'application/json' -Body '{"pairingCode":"MOCK-PAIR"}'
    $tasks = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/tasks') -Headers $headers
    if ($tasks.cached -or $tasks.data.tasks[0].taskId -cne '9007199254740993') { throw '任务投影或长整数标识不正确' }
    $claimed = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/jobs/mock-job/claim') -Method Post -Headers $headers
    if ($claimed.claimedByDeviceCode -cne 'MOCK-ONLY-SCALE') { throw '作业领取未更新设备上下文' }
    $status = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/status') -Headers $headers
    if (-not $status.deviceAuthorized -or $status.scaleConnected) { throw '未连接硬件时不应宣称称重成功' }
    $captureRejected = $false
    try {
        $null = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/captures') -Method Post -Headers $headers -ContentType 'application/json' `
            -Body '{"operationId":"smoke","sampleId":"missing","taskId":"9007199254740993","taskItemId":"9007199254740994","operatorSessionId":"MOCK-OPERATOR","jobId":"mock-job","jobVersion":"0","taskItemVersion":0}'
    } catch { $captureRejected = [int]$_.Exception.Response.StatusCode -eq 409 }
    if (-not $captureRejected) { throw '无真实设备采样时不能生成业务事件' }
    $rows = Invoke-WebRequest -Uri ($agentUrl + '/api/agent/events') -Headers $headers -UseBasicParsing
    if ($rows.Content.Trim() -ne '[]') { throw '不应生成模拟称重事件' }
    $basketQuery = '?jobId=mock-job&taskId=9007199254740993&taskItemId=9007199254740994'
    $summary = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/basket-summary' + $basketQuery) -Headers $headers
    $basketPage = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/baskets' + $basketQuery) -Headers $headers
    $basketQueue = Invoke-WebRequest -Uri ($agentUrl + '/api/agent/basket-queue' + $basketQuery) -Headers $headers -UseBasicParsing
    if ($summary.basketCount -ne 0 -or $summary.totalQuantity -ne '0' -or $summary.manifestHash.Length -ne 64 `
        -or $summary.locallyCompleted -or $basketPage.items.Count -ne 0 -or $basketQueue.Content.Trim() -ne '[]') {
        throw '空多筐投影、分页或队列响应不正确'
    }
    $basketRejected = $false
    try {
        $null = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/baskets') -Method Post -Headers $headers -ContentType 'application/json' `
            -Body '{"operationId":"basket-smoke","sampleId":"missing","taskId":"9007199254740993","taskItemId":"9007199254740994","operatorSessionId":"MOCK-OPERATOR","jobId":"mock-job","jobVersion":"0","taskItemVersion":0}'
    } catch { $basketRejected = [int]$_.Exception.Response.StatusCode -eq 409 }
    if (-not $basketRejected) { throw '多筐接口不应接受缺失的真实采样' }
    $finishBody = @{
        operationId='finish-smoke'; taskId='9007199254740993'; taskItemId='9007199254740994'
        operatorSessionId='MOCK-OPERATOR'; jobId='mock-job'; jobVersion='0'; taskItemVersion=0
        manifestHash=$summary.manifestHash; result='NORMAL'; reason=$null
    } | ConvertTo-Json
    $finishRejected = $false
    try {
        $null = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/item-completions') -Method Post -Headers $headers `
            -ContentType 'application/json' -Body $finishBody
    } catch { $finishRejected = [int]$_.Exception.Response.StatusCode -eq 409 }
    if (-not $finishRejected) { throw '空多筐商品不应完成' }
    $pageRejected = $false
    try { $null = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/baskets' + $basketQuery + '&limit=101') -Headers $headers }
    catch { $pageRejected = [int]$_.Exception.Response.StatusCode -eq 409 }
    if (-not $pageRejected) { throw '多筐越界分页没有拒绝' }
    Stop-TestHost $mockProcess 'mock'; $mockProcess = $null
    $cached = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/tasks') -Headers $headers
    if (-not $cached.cached) { throw '断网未返回当前员工的持久任务缓存' }
    Stop-TestHost $agentProcess 'agent-before-restart'; $agentProcess = $null
    $agentProcess = Start-TestHost $agentDll $agentUrl $variables
    $null = Wait-TestHost $agentUrl
    $restored = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/status') -Headers $headers
    $cachedAgain = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/tasks') -Headers $headers
    if (-not $restored.deviceAuthorized -or -not $cachedAgain.cached) { throw '重启后凭证或任务缓存没有恢复' }
    $restoredSummary = Invoke-RestMethod -Uri ($agentUrl + '/api/agent/basket-summary' + $basketQuery) -Headers $headers
    if ($restoredSummary.manifestHash -cne $summary.manifestHash) { throw '多筐摘要重启后不一致' }
    Write-Output 'PASS HTTP 冒烟：鉴权、任务、多筐查询/队列、无采样及空完成拒绝、分页、断网缓存和重启恢复'
}
finally {
    Stop-TestHost $agentProcess 'agent'
    Stop-TestHost $mockProcess 'mock'
}
