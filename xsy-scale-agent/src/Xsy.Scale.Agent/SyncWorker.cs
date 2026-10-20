using System.Text.Json;

namespace Xsy.Scale.Agent;

/// <summary>单机后台上传和打印回执编排，不在恢复网络时替员工接受称重事件。</summary>
internal sealed class SyncWorker(AgentOptions options, StateStore store, PlatformClient platform,
    ScaleState scale, IRawPrinter printer, PrintService prints, ILogger<SyncWorker> logger) : BackgroundService
{
    private DateTimeOffset lastHeartbeat = DateTimeOffset.MinValue;

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                if (platform.Credential() != null)
                {
                    for (int count = 0; count < 10 && platform.Credential() != null; count++)
                        if (!await UploadOne(stoppingToken)) break;
                    // 三类同步互不阻塞：打印接口故障不能让设备心跳消失。
                    if (platform.Credential() != null && DateTimeOffset.UtcNow - lastHeartbeat >= TimeSpan.FromSeconds(options.HeartbeatSeconds))
                    {
                        try { await platform.Heartbeat(scale, printer, stoppingToken); lastHeartbeat = DateTimeOffset.UtcNow; }
                        catch (Exception error) when (!stoppingToken.IsCancellationRequested)
                        { logger.LogWarning("设备心跳未完成，类型 {Type}", error.GetType().Name); }
                    }
                    if (platform.Credential() != null)
                    {
                        try { await prints.Acknowledge(stoppingToken); }
                        catch (Exception error) when (!stoppingToken.IsCancellationRequested)
                        { logger.LogWarning("打印回执待重传，类型 {Type}", error.GetType().Name); }
                    }
                    // 打印机没有现场配置时，不领取平台任务，避免误占其他工作台打印队列。
                    if (platform.Credential() != null && options.PrinterName.Length > 0 && options.AllowedPrintFormats.Length > 0)
                    {
                        foreach (PrintJob candidate in (await platform.PrintJobs(stoppingToken)).Items)
                        {
                            if (store.HasPrint(candidate.PrintAttemptId)) continue;
                            PrintJob claimed = await platform.Claim(candidate, stoppingToken);
                            if (claimed.PrintJobId != candidate.PrintJobId || claimed.PrintAttemptId != candidate.PrintAttemptId)
                                throw new AgentFault("PRINT_ATTEMPT_MISMATCH", "领取响应的打印尝试与待执行任务不匹配");
                            await prints.Execute(claimed, stoppingToken);
                        }
                    }
                }
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested) { break; }
            catch (Exception error)
            {
                logger.LogWarning("平台同步暂未完成，状态 {Code}", error is PlatformFault fault ? fault.Code : error.GetType().Name);
            }
            try { await Task.Delay(TimeSpan.FromSeconds(options.PlatformPollSeconds), stoppingToken); }
            catch (OperationCanceledException) { break; }
        }
    }

    internal async Task<bool> UploadOne(CancellationToken cancellation)
    {
        LocalEvent? record = store.NextEvent();
        if (record == null) return false;
        try
        {
            JsonElement result = await platform.Upload(record.Measurement, cancellation);
            if (!result.TryGetProperty("measurementId", out JsonElement id) || id.ValueKind != JsonValueKind.String
                || string.IsNullOrWhiteSpace(id.GetString()) || !result.TryGetProperty("status", out var status)
                || status.GetString() is not ("PENDING" or "ACCEPTED" or "REJECTED"))
                throw new PlatformFault("INVALID_RESPONSE", true);
            store.FinishEvent(record, "SERVER_PENDING", id.GetString(), null);
            return true;
        }
        catch (PlatformFault error)
        {
            store.FinishEvent(record, error.Retryable || error.Code == "DEVICE_UNAUTHORIZED" ? "RETRY_WAIT" : "NEEDS_REVIEW",
                null, error.Code);
            return !error.Retryable && error.Code != "DEVICE_UNAUTHORIZED";
        }
        catch (Exception error) when (error is HttpRequestException or OperationCanceledException or JsonException or InvalidOperationException)
        { store.FinishEvent(record, "RETRY_WAIT", null, "NETWORK_OR_RESPONSE_ERROR"); return false; }
    }
}
