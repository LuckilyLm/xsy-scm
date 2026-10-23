using System.Text.Json;

namespace Xsy.Scale.Agent;

internal sealed record TraceLabelRequest(string OperationId, string EventKey, string LotId, string PackageType);

/// <summary>平台发码成功后才追加可打印快照；请求重试返回同一次打印尝试。</summary>
internal sealed class TraceLabelService(PlatformClient platform, StateStore store, BasketService baskets,
    AgentOptions options, WorkstationOptions workstation)
{
    internal Task<JsonElement> Lots(BasketScope scope, CancellationToken cancellation)
    {
        RequireLive(scope);
        return platform.TraceLots(scope.TaskItemId, scope.OperatorSessionId, cancellation);
    }

    internal async Task<LocalTicket> Print(BasketScope scope, TraceLabelRequest request, CancellationToken cancellation)
    {
        RequireLive(scope);
        if (string.IsNullOrWhiteSpace(request.OperationId) || request.OperationId.Length > 160
            || string.IsNullOrWhiteSpace(request.EventKey) || !long.TryParse(request.LotId, out long lotId) || lotId <= 0
            || request.PackageType is not ("BAG" or "BOX" or "CRATE"))
            throw new AgentFault("INVALID_TRACE_REQUEST", "请选择批次和包装类型");
        LocalTicket? previous = store.TraceTicket(scope, request);
        if (previous != null) return previous;
        if (string.IsNullOrWhiteSpace(options.PrinterName))
            throw new AgentFault("PRINTER_NOT_CONFIGURED", "请先配置标签打印机");
        BasketRecord record = store.TraceBasket(scope, request.EventKey);
        // 重放已冻结的筐上报取得正式记录编号，不重新采样，也不重新累计。
        JsonElement uploaded = await platform.UploadBasket(PlatformBasketUpload.From(record.Measurement,
            record.OperationId, record.BasketNumber), scope.OperatorSessionId, cancellation);
        if (!uploaded.TryGetProperty("measurementId", out var id) || string.IsNullOrWhiteSpace(id.ToString()))
            throw new PlatformFault("INVALID_RESPONSE", true);
        JsonElement unit = await platform.IssueTraceUnit(scope.TaskItemId, id.ToString(), request.LotId,
            request.PackageType, scope.OperatorSessionId, cancellation);
        string code = unit.GetProperty("unitCode").GetString() ?? throw new PlatformFault("INVALID_RESPONSE", true);
        string? url = unit.TryGetProperty("traceUrl", out var link) ? link.GetString() : null;
        if (!string.IsNullOrWhiteSpace(url))
        {
            if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != "https" || uri.UserInfo.Length != 0)
                throw new AgentFault("INVALID_TRACE_URL", "平台公开查询地址必须为HTTPS");
            using var matrix = TraceLink.Matrix(url);
        }
        else url = null;
        TaskSnapshot job = store.CachedTasks(scope.OperatorSessionId)!.Tasks.Single(t => t.JobId == scope.JobId && t.TaskId == scope.TaskId);
        TaskItem item = job.Items.Single(i => i.TaskItemId == scope.TaskItemId);
        var snapshot = new TicketSnapshot(record.EventKey, job.TaskNo, item.ProductName, item.SpecName,
            item.CustomerName, item.OrderNo, record.BasketNumber, unit.GetProperty("netWeight").ToString(),
            unit.GetProperty("weightUnit").GetString()!, record.Measurement.TareWeight, options.DeviceCode,
            record.Measurement.CapturedAt, false, "PLATFORM_TRACE_V1", code, url,
            unit.TryGetProperty("lotNo", out var lot) ? lot.GetString() : null);
        var ticket = new LocalTicket(Guid.NewGuid().ToString("N"), request.OperationId, WireJson.Digest(scope),
            snapshot, "QUEUED", options.PrinterName, workstation.PaperWidthMm, workstation.PaperHeightMm,
            false, null, DateTimeOffset.UtcNow);
        // 版式不合格时不排入打印队列；已签发码仍能以同一称重记录重取。
        using var preview = TicketLayout.Preview(ticket);
        return store.SaveTraceTicket(scope, request, ticket);
    }

    private void RequireLive(BasketScope scope)
    {
        if (workstation.LocalTest || workstation.HardwareTest)
            throw new AgentFault("TEST_TRACE_BLOCKED", "演练模式不签发正式包装码");
        if (baskets.Summary(scope).AuthorizationExpired)
            throw new AgentFault("JOB_CONTEXT_STALE", "请刷新任务并确认当前作业授权");
    }
}
