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
                    // 多筐是新主流程：先按 sequence 派发，失败即停，保证筐先于完成命令到达平台。
                    if (platform.Credential() != null) await SyncBasketCommands(stoppingToken);
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

    /// <summary>
    /// 按 sequence 派发多筐命令。任一命令未成功就结束本轮，避免完成命令越过尚未受理的筐。
    /// </summary>
    private async Task SyncBasketCommands(CancellationToken cancellation)
    {
        foreach (PendingBasketCommand pending in store.PendingBasketCommands(20))
        {
            if (cancellation.IsCancellationRequested) return;
            if (!await DispatchBasketCommand(pending, cancellation)) return;
        }
    }

    /// <returns>true 表示可以继续下一条；false 表示本轮应停止。</returns>
    private async Task<bool> DispatchBasketCommand(PendingBasketCommand pending, CancellationToken cancellation)
    {
        try
        {
            if (pending.Kind == "COMPLETE_ITEM")
            {
                // 完成命令必须等全部筐落地，否则平台按不完整集合求和。
                if (store.HasUnsentCaptures(pending.ScopeKey)) return false;
                ItemCompletion completion = WireJson.Decode<ItemCompletion>(pending.Payload);
                JsonElement completed = await platform.CompleteItem(
                    PlatformItemCompletion.From(completion, pending.RequestHash), completion.OperatorSessionId,
                    cancellation);
                RequireAccepted(completed, "completionId");
                store.SetBasketCommandState(pending.Sequence, BasketCommandState.Sent);
                return true;
            }
            BasketRecord record = WireJson.Decode<BasketRecord>(pending.Payload);
            JsonElement uploaded = await platform.UploadBasket(
                PlatformBasketUpload.From(record.Measurement, pending.OperationId, record.BasketNumber),
                record.Measurement.OperatorSessionId, cancellation);
            RequireAccepted(uploaded, "measurementId");
            store.SetBasketCommandState(pending.Sequence, BasketCommandState.Sent);
            return true;
        }
        catch (PlatformFault error)
        {
            // 凭证失效与可重试错误保留队列等下一轮；明确拒绝转人工，并停止同范围的自动重试。
            if (error.Retryable || error.Code == "DEVICE_UNAUTHORIZED")
            {
                store.SetBasketCommandState(pending.Sequence, BasketCommandState.Retry);
                return false;
            }
            store.ParkBasketCommands(pending.ScopeKey, error.Message);
            return true;
        }
        catch (Exception error) when (error is HttpRequestException or OperationCanceledException or JsonException or InvalidOperationException)
        {
            store.SetBasketCommandState(pending.Sequence, BasketCommandState.Retry);
            return false;
        }
    }

    /// <summary>成功响应必须带业务标识；只有 code=OK 却没有标识说明契约不一致，按可重试处理。</summary>
    private static void RequireAccepted(JsonElement data, string idField)
    {
        if (!data.TryGetProperty(idField, out JsonElement id)) throw new PlatformFault("INVALID_RESPONSE", true);
        bool valid = id.ValueKind switch
        {
            JsonValueKind.String => !string.IsNullOrWhiteSpace(id.GetString()),
            JsonValueKind.Number => true,
            _ => false
        };
        if (!valid) throw new PlatformFault("INVALID_RESPONSE", true);
    }
}
